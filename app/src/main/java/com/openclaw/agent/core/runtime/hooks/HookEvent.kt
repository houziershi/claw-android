package com.openclaw.agent.core.runtime.hooks

import kotlinx.serialization.json.JsonObject

data class ToolCallRecord(
    val name: String,
    val argsHash: Int,
    val resultHash: Int,
    val timestamp: Long
)

sealed class HookEvent {
    data class UserPromptSubmit(val prompt: String, val sessionId: String) : HookEvent()
    data class PreToolUse(
        val toolName: String,
        val toolInput: JsonObject,
        val turnIndex: Int,
        val callHistory: List<ToolCallRecord>
    ) : HookEvent()
    data class PostToolUse(
        val toolName: String,
        val toolInput: JsonObject,
        val toolResult: String,
        val success: Boolean,
        val durationMs: Long
    ) : HookEvent()
    data class PostToolUseFailure(
        val toolName: String,
        val toolInput: JsonObject,
        val error: String
    ) : HookEvent()
    data class Stop(
        val fullText: String,
        val usage: com.openclaw.agent.core.runtime.Usage?,
        val turnCount: Int
    ) : HookEvent()
}
