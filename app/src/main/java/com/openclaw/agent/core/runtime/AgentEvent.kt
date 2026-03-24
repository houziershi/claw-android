package com.openclaw.agent.core.runtime

import kotlinx.serialization.json.JsonObject

data class Usage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0
)

sealed class AgentEvent {
    /** Incremental text from the assistant */
    data class TextChunk(val text: String) : AgentEvent()

    data class ThinkingChunk(val text: String) : AgentEvent()

    /** A tool call has started */
    data class ToolCallStarted(val id: String, val name: String, val input: JsonObject? = null) : AgentEvent()

    /** A tool finished executing */
    data class ToolCallFinished(
        val id: String,
        val name: String,
        val input: JsonObject? = null,
        val result: String,
        val success: Boolean
    ) : AgentEvent()

    data class TextSegmentComplete(val text: String) : AgentEvent()

    /** The assistant has finished this turn */
    data class TurnComplete(
        val fullText: String,
        val usage: Usage? = null,
        val model: String? = null,
        val cost: Double? = null
    ) : AgentEvent()

    /** An unrecoverable error occurred */
    data class Error(val message: String) : AgentEvent()

    /** The agent is thinking (processing tool results, about to call LLM again) */
    object Thinking : AgentEvent()

    /** User confirmation required for a hook-guarded tool (Phase 7.4) */
    data class ConfirmRequired(val toolName: String, val message: String, val hookId: String) : AgentEvent()
}
