package com.openclaw.agent.core.runtime

import kotlinx.serialization.json.JsonObject

/**
 * Events emitted by AgentRuntime during a chat turn.
 * The UI consumes these to update the message list in real-time.
 */
sealed class AgentEvent {
    /** Incremental text from the assistant */
    data class TextChunk(val text: String) : AgentEvent()

    /** A tool call has started */
    data class ToolCallStarted(val id: String, val name: String) : AgentEvent()

    /** A tool finished executing */
    data class ToolCallFinished(
        val id: String,
        val name: String,
        val input: JsonObject,
        val result: String,
        val success: Boolean
    ) : AgentEvent()

    /** The assistant has finished this turn */
    data class TurnComplete(
        val fullText: String,
        val inputTokens: Int,
        val outputTokens: Int
    ) : AgentEvent()

    /** An unrecoverable error occurred */
    data class Error(val message: String) : AgentEvent()

    /** The agent is thinking (processing tool results, about to call LLM again) */
    object Thinking : AgentEvent()
}
