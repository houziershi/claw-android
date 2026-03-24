package com.openclaw.agent.core.runtime

import android.util.Log
import com.openclaw.agent.core.llm.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContextCompactor"
private const val TOKEN_THRESHOLD = 50_000
private const val KEEP_RECENT_COUNT = 6

/**
 * Automatically compacts (summarizes) long conversation histories to avoid
 * context window overflow.
 * 
 * When estimated tokens exceed threshold, older messages are summarized via LLM,
 * and the summary replaces them. Recent messages (last N) are kept intact.
 */
@Singleton
class ContextCompactor @Inject constructor() {

    /**
     * Compact the conversation history if it's too long.
     * 
     * @param history Original message list
     * @param apiKey API key for summarization LLM call
     * @param baseUrl API base URL
     * @param model Model to use for summarization
     * @return Compacted history (or original if no compaction needed)
     */
    suspend fun compact(
        history: List<LlmMessage>,
        apiKey: String,
        baseUrl: String,
        model: String
    ): List<LlmMessage> {
        // Don't compact if history is too short
        if (history.size <= KEEP_RECENT_COUNT + 2) {
            Log.d(TAG, "History too short (${history.size} messages), skipping compaction")
            return history
        }

        // Estimate tokens (rough: char_count / 4)
        val estimatedTokens = history.sumOf { msg ->
            when (val content = msg.content) {
                is JsonPrimitive -> content.content.length / 4
                is JsonArray -> content.toString().length / 4
                is JsonObject -> content.toString().length / 4
                else -> 0
            }
        }

        Log.d(TAG, "Estimated tokens: $estimatedTokens (threshold: $TOKEN_THRESHOLD)")

        if (estimatedTokens < TOKEN_THRESHOLD) {
            Log.d(TAG, "Under threshold, no compaction needed")
            return history
        }

        // Split: old messages to summarize + recent messages to keep
        val splitIndex = history.size - KEEP_RECENT_COUNT
        val oldMessages = history.subList(0, splitIndex)
        val recentMessages = history.subList(splitIndex, history.size)

        Log.d(TAG, "Compacting ${oldMessages.size} old messages, keeping ${recentMessages.size} recent")

        // Format old messages as text for summarization
        val conversationText = oldMessages.joinToString("\n") { msg ->
            val contentStr = when (val content = msg.content) {
                is JsonPrimitive -> content.content
                is JsonArray -> content.toString()
                is JsonObject -> content.toString()
                else -> ""
            }
            "${msg.role.uppercase()}: $contentStr"
        }

        // Call LLM for summary
        val summary = try {
            summarize(conversationText, apiKey, baseUrl, model)
        } catch (e: Exception) {
            Log.w(TAG, "Summarization failed, returning original history", e)
            return history
        }

        // Build compacted history: summary + recent messages
        val summaryMessage = LlmMessage(
            role = "user",
            content = JsonPrimitive("[Context Summary from previous conversation]:\n$summary")
        )

        val compacted = listOf(summaryMessage) + recentMessages
        Log.d(TAG, "Compaction complete: ${history.size} → ${compacted.size} messages")
        return compacted
    }

    private suspend fun summarize(conversationText: String, apiKey: String, baseUrl: String, model: String): String {
        val client = createClient(apiKey, baseUrl)
        val systemPrompt = """
            You are a conversation summarizer. Create a concise summary of the conversation history below, 
            preserving key facts, decisions, and context needed to continue the conversation.
            Keep the summary under 500 words.
        """.trimIndent()

        val messages = listOf(
            LlmMessage(role = "user", content = JsonPrimitive(conversationText))
        )

        val summaryBuilder = StringBuilder()

        client.chat(
            messages = messages,
            systemPrompt = systemPrompt,
            tools = emptyList(),
            model = model,
            maxTokens = 1024
        ).collect { event ->
            when (event) {
                is LlmEvent.TextChunk -> summaryBuilder.append(event.text)
                is LlmEvent.Error -> throw Exception("Summarization LLM error: ${event.message}")
                else -> {}
            }
        }

        return summaryBuilder.toString().trim().ifBlank {
            throw Exception("Empty summary returned")
        }
    }

    private fun createClient(apiKey: String, baseUrl: String): LlmClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        return ClaudeClient(apiKey, okHttpClient, baseUrl)
    }
}
