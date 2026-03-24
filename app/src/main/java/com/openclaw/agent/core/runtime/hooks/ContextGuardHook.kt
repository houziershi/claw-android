package com.openclaw.agent.core.runtime.hooks

import android.util.Log

private const val TAG = "ContextGuardHook"

class ContextGuardHook(
    private val contextWindowTokens: Int = 200_000,
    private val singleResultMaxPercent: Float = 0.5f
) : HookHandler<HookEvent.PostToolUse> {

    override suspend fun handle(event: HookEvent.PostToolUse): HookDecision {
        val resultTokens = event.toolResult.length / 4  // rough estimate
        val singleLimit = (contextWindowTokens * singleResultMaxPercent).toInt()

        if (resultTokens > singleLimit) {
            val charLimit = singleLimit * 4
            Log.w(TAG, "${event.toolName} result exceeds ${(singleResultMaxPercent * 100).toInt()}% of context window: ${resultTokens} tokens > $singleLimit limit")
            val truncated = event.toolResult.take(charLimit)
            return HookDecision.ModifyResult(
                "$truncated\n\n[context guard: truncated to fit ${(singleResultMaxPercent * 100).toInt()}% of context window]"
            )
        }

        return HookDecision.Allow
    }
}
