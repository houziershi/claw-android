package com.openclaw.agent.core.runtime

import android.util.Log
import com.openclaw.agent.core.llm.*
import com.openclaw.agent.data.preferences.SettingsStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TwoPhaseRouter"
private const val CONFIDENCE_THRESHOLD = 0.7f

// Map intent names to relevant tool names
private val INTENT_TOOL_MAP = mapOf(
    "device_control" to setOf("alarm", "bluetooth", "volume", "clipboard", "get_device_info", "get_current_time"),
    "web_search" to setOf("web_search", "web_fetch", "get_current_time"),
    "memory" to setOf("memory_read", "memory_write", "memory_search", "get_current_time"),
    "file_ops" to setOf("memory_read", "memory_write", "memory_search", "get_current_time"),
    "general_chat" to emptySet()  // empty = use all tools
)

/**
 * Two-phase routing: lightweight classification first, then filter tools.
 * Phase 1: call LLM with ONLY a "route" tool to get intent + confidence.
 * Phase 2: based on intent, return a filtered tool set (or null = all tools).
 * 
 * This reduces token cost — instead of sending 12+ tool definitions every turn,
 * send 1 lightweight tool, get an intent, then send only relevant tools.
 */
@Singleton
class TwoPhaseRouter @Inject constructor(
    private val settingsStore: SettingsStore
) {
    /**
     * Route the user prompt to an intent category.
     * Returns null (use all tools) or a Set<String> of tool names to filter to.
     */
    suspend fun route(userPrompt: String, apiKey: String, baseUrl: String, model: String): Set<String>? {
        val routeTool = buildRouteTool()
        val client = createClient(apiKey, baseUrl)
        val messages = listOf(LlmMessage(role = "user", content = JsonPrimitive(userPrompt)))
        val systemPrompt = "You are a request classifier. Use the route tool to classify the user request into a category. Do not answer the question, just classify it."

        var intent: String? = null
        var confidence = 0f

        try {
            client.chat(
                messages = messages,
                systemPrompt = systemPrompt,
                tools = listOf(routeTool),
                model = model,
                maxTokens = 256
            ).collect { event ->
                when (event) {
                    is LlmEvent.ToolCallComplete -> {
                        intent = event.input["intent"]?.jsonPrimitive?.content
                        confidence = event.input["confidence"]?.jsonPrimitive?.float ?: 0f
                    }
                    is LlmEvent.Error -> {
                        Log.w(TAG, "Route LLM error: ${event.message}")
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Route call failed, using all tools", e)
            return null
        }

        Log.d(TAG, "Route result: intent=$intent confidence=$confidence")

        // If low confidence or general_chat, use all tools
        if (intent == null || confidence < CONFIDENCE_THRESHOLD || intent == "general_chat") {
            Log.d(TAG, "Using all tools (confidence too low or general_chat)")
            return null
        }

        // Return tool set for this intent (or null if not mapped)
        val tools = INTENT_TOOL_MAP[intent]
        return if (tools.isNullOrEmpty()) null else tools
    }

    private fun buildRouteTool(): ToolDefinition {
        return ToolDefinition(
            name = "route",
            description = "Classify the user request into a category to select the appropriate tools.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("intent") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("device_control")
                            add("web_search")
                            add("memory")
                            add("file_ops")
                            add("general_chat")
                        })
                        put("description", "The category of the user request")
                    }
                    putJsonObject("confidence") {
                        put("type", "number")
                        put("description", "Confidence level 0.0-1.0")
                    }
                }
                put("required", buildJsonArray {
                    add("intent")
                    add("confidence")
                })
            }
        )
    }

    private fun createClient(apiKey: String, baseUrl: String): LlmClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        return ClaudeClient(apiKey, okHttpClient, baseUrl)
    }
}
