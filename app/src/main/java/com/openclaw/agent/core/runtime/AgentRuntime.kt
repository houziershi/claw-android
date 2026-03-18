package com.openclaw.agent.core.runtime

import android.util.Log
import com.openclaw.agent.core.llm.*
import com.openclaw.agent.core.memory.MemoryContextBuilder
import com.openclaw.agent.data.db.MessageDao
import com.openclaw.agent.data.db.SessionDao
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.preferences.SettingsStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgentRuntime"
private const val MAX_TOOL_LOOPS = 10

@Singleton
class AgentRuntime @Inject constructor(
    private val settingsStore: SettingsStore,
    private val memoryContextBuilder: MemoryContextBuilder,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Process a user message and stream back AgentEvents.
     * Implements the function-calling loop: LLM → tool_use → tool_result → LLM → ...
     */
    fun chat(
        sessionId: String,
        userMessage: String,
        model: String,
        apiKey: String,
        baseUrl: String = "https://api.anthropic.com/v1/messages"
    ): Flow<AgentEvent> = flow {
        if (apiKey.isBlank()) {
            emit(AgentEvent.Error("API key not set. Go to Settings to add your Claude API key."))
            return@flow
        }

        // Save user message to DB
        val userMsgEntity = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "user",
            content = userMessage,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(userMsgEntity)
        sessionDao.incrementMessageCount(sessionId, System.currentTimeMillis())

        // Build system prompt with memory context
        val systemPrompt = memoryContextBuilder.buildSystemPrompt(lastUserMessage = userMessage)

        // Build conversation history from DB
        val dbMessages = messageDao.getMessagesForSessionOnce(sessionId)
        val llmMessages = dbMessages.map { msg ->
            LlmMessage(
                role = msg.role,
                content = JsonPrimitive(msg.content)
            )
        }

        // Create LLM client
        val client = createClient(apiKey, baseUrl)

        // Call LLM (no tools in Phase 1)
        val fullText = StringBuilder()
        var inputTokens = 0
        var outputTokens = 0

        client.chat(
            messages = llmMessages,
            systemPrompt = systemPrompt,
            tools = emptyList(), // Tools added in Phase 2
            model = model
        ).collect { event ->
            when (event) {
                is LlmEvent.TextChunk -> {
                    fullText.append(event.text)
                    emit(AgentEvent.TextChunk(event.text))
                }
                is LlmEvent.Done -> {
                    inputTokens = event.inputTokens
                    outputTokens = event.outputTokens
                }
                is LlmEvent.Error -> {
                    emit(AgentEvent.Error(event.message))
                    return@collect
                }
                is LlmEvent.ToolCallStart,
                is LlmEvent.ToolCallInput,
                is LlmEvent.ToolCallComplete -> {
                    // Tool handling will be added in Phase 2
                    Log.d(TAG, "Tool event received (not handled in Phase 1): $event")
                }
            }
        }

        // Save assistant message to DB
        if (fullText.isNotEmpty()) {
            val assistantMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = fullText.toString(),
                timestamp = System.currentTimeMillis(),
                inputTokens = inputTokens,
                outputTokens = outputTokens
            )
            messageDao.insertMessage(assistantMsg)
            sessionDao.incrementMessageCount(sessionId, System.currentTimeMillis())

            emit(AgentEvent.TurnComplete(fullText.toString(), inputTokens, outputTokens))
        }
    }

    private fun createClient(apiKey: String, baseUrl: String): LlmClient {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return ClaudeClient(apiKey, okHttpClient, baseUrl)
    }
}
