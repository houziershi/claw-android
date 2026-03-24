package com.openclaw.agent.core.runtime.hooks

import android.util.Log

private const val TAG = "ResultTruncateHook"

class ResultTruncateHook(
    private val defaultMaxLength: Int = 4000,
    private val toolMaxLengths: Map<String, Int> = mapOf(
        "web_search" to 3000,
        "web_fetch" to 4000,
        "memory_search" to 2000,
        "memory_read" to 2000,
        "mijia_list_devices" to 3000
    )
) : HookHandler<HookEvent.PostToolUse> {

    override suspend fun handle(event: HookEvent.PostToolUse): HookDecision {
        val maxLength = toolMaxLengths[event.toolName] ?: defaultMaxLength

        if (event.toolResult.length <= maxLength) return HookDecision.Allow

        Log.d(TAG, "Truncating ${event.toolName} result: ${event.toolResult.length} → $maxLength chars")
        val truncated = smartTruncate(event.toolResult, maxLength)
        return HookDecision.ModifyResult(truncated)
    }

    private fun smartTruncate(text: String, limit: Int): String {
        val errorPatterns = listOf("error", "exception", "traceback", "failed", "Error:", "FAILED", "panic")
        val tailPortion = text.takeLast(text.length / 3)
        val hasTailError = errorPatterns.any { tailPortion.contains(it, ignoreCase = true) }

        return if (hasTailError) {
            val headBudget = (limit * 0.7).toInt()
            val tailBudget = limit - headBudget - 60
            "${text.take(headBudget)}\n\n...[truncated, ${text.length} chars total]...\n\n${text.takeLast(tailBudget)}"
        } else {
            "${text.take(limit)}\n\n...[truncated, ${text.length} chars total]"
        }
    }
}
