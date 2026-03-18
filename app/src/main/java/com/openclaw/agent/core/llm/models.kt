package com.openclaw.agent.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ─── Request models ───────────────────────────────────────────────────────────

@Serializable
data class LlmMessage(
    val role: String,
    /** Can be a plain String or a List<ContentBlock> encoded as JsonElement */
    val content: JsonElement
)

@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(
        val type: String = "text",
        val text: String
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val type: String = "tool_use",
        val id: String,
        val name: String,
        val input: JsonObject
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val type: String = "tool_result",
        @SerialName("tool_use_id") val toolUseId: String,
        val content: JsonElement
    ) : ContentBlock()
}

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject
)

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<LlmMessage>,
    val tools: List<ToolDefinition>? = null,
    val stream: Boolean
)

// ─── SSE streaming event models ───────────────────────────────────────────────

/** Internal SSE payload from Claude API */
@Serializable
data class SsePayload(
    val type: String,
    val index: Int? = null,
    val delta: SseDelta? = null,
    @SerialName("content_block") val contentBlock: SseContentBlock? = null,
    val message: SseMessage? = null,
    val usage: SseUsage? = null,
    val error: SseError? = null
)

@Serializable
data class SseDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class SseContentBlock(
    val type: String,
    val id: String? = null,
    val name: String? = null
)

@Serializable
data class SseMessage(
    val id: String? = null,
    val model: String? = null,
    val usage: SseUsage? = null
)

@Serializable
data class SseUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null
)

@Serializable
data class SseError(
    val type: String? = null,
    val message: String? = null
)

// ─── Unified streaming events emitted to callers ──────────────────────────────

sealed class LlmEvent {
    data class TextChunk(val text: String) : LlmEvent()
    data class ToolCallStart(val id: String, val name: String) : LlmEvent()
    data class ToolCallInput(val partialJson: String) : LlmEvent()
    data class ToolCallComplete(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : LlmEvent()
    data class Done(
        val stopReason: String,
        val inputTokens: Int,
        val outputTokens: Int
    ) : LlmEvent()
    data class Error(val message: String, val code: Int? = null) : LlmEvent()
}
