package com.openclaw.agent.core.runtime.hooks

import android.util.Log

private const val TAG = "LoopDetectionHook"

/** Tools that are polling-based (same result = no progress) */
private val POLL_TOOLS = setOf("process")

/** Thresholds for loop escalation */
private const val THRESHOLD_WARN = 10
private const val THRESHOLD_DENY = 20
private const val THRESHOLD_BLOCK = 30

/**
 * Detects three types of infinite loops in the function-calling loop:
 *
 * 1. **generic_repeat**: Same tool + same argsHash called repeatedly.
 * 2. **ping_pong**: Two tools alternating A→B→A→B.
 * 3. **known_poll_no_progress**: A polling tool returning the same resultHash repeatedly.
 *
 * Attached as a PreToolUse hook (priority=0) in AgentRuntime.
 */
class LoopDetectionHook : HookHandler<HookEvent.PreToolUse> {

    override suspend fun handle(event: HookEvent.PreToolUse): HookDecision {
        val history = event.callHistory
        if (history.size < 2) return HookDecision.Allow

        // 1. Generic repeat: same tool + same argsHash
        val repeatCount = countGenericRepeat(event.toolName, event.toolInput.hashCode(), history)
        if (repeatCount >= THRESHOLD_BLOCK) {
            Log.w(TAG, "BLOCK generic_repeat: ${event.toolName} repeated $repeatCount times")
            return HookDecision.Block("Loop detected: tool '${event.toolName}' called $repeatCount times with identical arguments. Stopping to prevent infinite loop.")
        }
        if (repeatCount >= THRESHOLD_DENY) {
            Log.w(TAG, "DENY generic_repeat: ${event.toolName} repeated $repeatCount times")
            return HookDecision.Deny("Loop guard: '${event.toolName}' has been called $repeatCount times with the same arguments. Try a different approach.")
        }
        if (repeatCount >= THRESHOLD_WARN) {
            Log.d(TAG, "WARN generic_repeat: ${event.toolName} repeated $repeatCount times")
            // Warning is injected as a note in the tool result (handled via ModifyResult later)
            // For PreToolUse we return Allow but log; AgentRuntime can check the count via PostToolUse.
            // Actual warning injection happens via the note appended in callHistory.
        }

        // 2. Ping-pong: alternating A→B→A→B
        val pingPongCount = countPingPong(history)
        if (pingPongCount >= THRESHOLD_BLOCK) {
            Log.w(TAG, "BLOCK ping_pong: ${pingPongCount} alternations in history")
            return HookDecision.Block("Loop detected: two tools are alternating repeatedly ($pingPongCount alternations). Stopping to prevent infinite loop.")
        }
        if (pingPongCount >= THRESHOLD_DENY) {
            Log.w(TAG, "DENY ping_pong: $pingPongCount alternations")
            return HookDecision.Deny("Loop guard: detected $pingPongCount alternating tool calls. Try a different strategy.")
        }

        // 3. Known poll no progress: same poll tool + same resultHash
        if (event.toolName in POLL_TOOLS) {
            val pollCount = countPollNoProgress(event.toolName, history)
            if (pollCount >= THRESHOLD_BLOCK) {
                Log.w(TAG, "BLOCK poll_no_progress: ${event.toolName} returned same result $pollCount times")
                return HookDecision.Block("Loop detected: '${event.toolName}' returned the same result $pollCount times with no progress.")
            }
            if (pollCount >= THRESHOLD_DENY) {
                Log.w(TAG, "DENY poll_no_progress: ${event.toolName} same result $pollCount times")
                return HookDecision.Deny("Poll guard: '${event.toolName}' returned the same result $pollCount times. The operation may be stuck.")
            }
        }

        return HookDecision.Allow
    }

    /** Count consecutive trailing calls with the same toolName + argsHash */
    private fun countGenericRepeat(toolName: String, argsHash: Int, history: List<ToolCallRecord>): Int {
        var count = 0
        for (record in history.asReversed()) {
            if (record.name == toolName && record.argsHash == argsHash) count++
            else break
        }
        return count
    }

    /**
     * Count ping-pong alternations at the tail of history.
     * Returns the number of A→B→A cycles detected.
     * e.g. [A,B,A,B,A,B] → 3 cycles (=6 alternations, but we count full A→B pairs)
     */
    private fun countPingPong(history: List<ToolCallRecord>): Int {
        if (history.size < 4) return 0
        val tail = history.takeLast(THRESHOLD_BLOCK * 2)
        if (tail.size < 4) return 0

        // Check if the last 4+ entries alternate between exactly 2 tool names
        val lastName = tail.last().name
        val secondLastName = tail.dropLast(1).last().name
        if (lastName == secondLastName) return 0  // not alternating

        var alternations = 0
        var expected = lastName
        for (record in tail.asReversed()) {
            if (record.name == expected) {
                alternations++
                expected = if (expected == lastName) secondLastName else lastName
            } else {
                break
            }
        }
        return alternations / 2  // count full A→B pairs
    }

    /** Count consecutive trailing calls where resultHash is the same (no progress) */
    private fun countPollNoProgress(toolName: String, history: List<ToolCallRecord>): Int {
        if (history.isEmpty()) return 0
        val lastResult = history.last().resultHash
        var count = 0
        for (record in history.asReversed()) {
            if (record.name == toolName && record.resultHash == lastResult) count++
            else break
        }
        return count
    }
}
