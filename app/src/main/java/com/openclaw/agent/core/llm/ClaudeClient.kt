package com.openclaw.agent.core.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.coroutineContext

private const val TAG = "ClaudeClient"
private const val DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"

/**
 * Anthropic-compatible API client (works with Claude, Kimi, etc.).
 * Implements streaming via SSE (Server-Sent Events).
 *
 * SSE event types handled:
 *   message_start, content_block_start, content_block_delta,
 *   content_block_stop, message_delta, message_stop, error
 */
class ClaudeClient(
    private val apiKey: String,
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) : LlmClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun chat(
        messages: List<LlmMessage>,
        systemPrompt: String?,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int
    ): Flow<LlmEvent> = flow {
        val requestBody = ClaudeRequest(
            model = model,
            maxTokens = maxTokens,
            system = systemPrompt,
            messages = messages,
            tools = null, // Phase 2
            stream = true
        )

        val requestJson = json.encodeToString(requestBody)
        Log.d(TAG, "Sending request to Claude: model=$model, messages=${messages.size}, url=$baseUrl")
        Log.d(TAG, "Request body: $requestJson")

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            emit(LlmEvent.Error("Network error: ${e.message}"))
            return@flow
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "HTTP ${response.code}: $errorBody")
            emit(LlmEvent.Error("HTTP ${response.code}: $errorBody", response.code))
            response.close()
            return@flow
        }

        val source = response.body?.source()
        if (source == null) {
            emit(LlmEvent.Error("Empty response body"))
            response.close()
            return@flow
        }

        // SSE parsing state
        var currentBlockType = ""
        var currentToolId = ""
        var currentToolName = ""
        val currentToolInput = StringBuilder()
        var inputTokens = 0
        var outputTokens = 0

        try {
            while (!source.exhausted() && coroutineContext.isActive) {
                val line = source.readUtf8Line() ?: break

                when {
                    line.startsWith("data:") -> {
                        // Handle both "data: {...}" (Claude) and "data:{...}" (Kimi) formats
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") return@flow

                        val payload = try {
                            json.decodeFromString<SsePayload>(data)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed SSE: $data")
                            continue
                        }

                        when (payload.type) {
                            "message_start" -> {
                                inputTokens = payload.message?.usage?.inputTokens ?: 0
                            }

                            "content_block_start" -> {
                                val block = payload.contentBlock ?: continue
                                currentBlockType = block.type
                                if (block.type == "tool_use") {
                                    currentToolId = block.id ?: ""
                                    currentToolName = block.name ?: ""
                                    currentToolInput.clear()
                                    emit(LlmEvent.ToolCallStart(currentToolId, currentToolName))
                                }
                            }

                            "content_block_delta" -> {
                                val delta = payload.delta ?: continue
                                when (delta.type) {
                                    "text_delta" -> {
                                        val text = delta.text ?: continue
                                        emit(LlmEvent.TextChunk(text))
                                    }
                                    "input_json_delta" -> {
                                        val partial = delta.partialJson ?: continue
                                        currentToolInput.append(partial)
                                        emit(LlmEvent.ToolCallInput(partial))
                                    }
                                }
                            }

                            "content_block_stop" -> {
                                if (currentBlockType == "tool_use" && currentToolId.isNotEmpty()) {
                                    val inputObj = try {
                                        json.parseToJsonElement(currentToolInput.toString()).jsonObject
                                    } catch (e: Exception) {
                                        buildJsonObject {}
                                    }
                                    emit(LlmEvent.ToolCallComplete(currentToolId, currentToolName, inputObj))
                                    currentBlockType = ""
                                    currentToolId = ""
                                    currentToolName = ""
                                    currentToolInput.clear()
                                }
                            }

                            "message_delta" -> {
                                outputTokens = payload.usage?.outputTokens ?: 0
                                val stopReason = payload.delta?.stopReason ?: "end_turn"
                                emit(LlmEvent.Done(stopReason, inputTokens, outputTokens))
                            }

                            "error" -> {
                                val msg = payload.error?.message ?: "Unknown API error"
                                emit(LlmEvent.Error(msg))
                            }

                            "message_stop" -> {
                                // Stream complete — no more events
                                return@flow
                            }
                        }
                    }
                    // Lines starting with "event:" are type hints we handle via the payload "type" field.
                    // Empty lines are SSE event boundaries — no action needed.
                }
            }
        } catch (e: Exception) {
            if (coroutineContext.isActive) {
                Log.e(TAG, "SSE parse error", e)
                emit(LlmEvent.Error("Stream error: ${e.message}"))
            }
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)
}
