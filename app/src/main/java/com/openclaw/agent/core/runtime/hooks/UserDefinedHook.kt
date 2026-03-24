package com.openclaw.agent.core.runtime.hooks

import android.util.Log
import javax.inject.Inject

private const val TAG = "UserDefinedHook"

/**
 * Hook handler that reads user-defined hook configurations from UserHookRegistry
 * and applies them to PreToolUse events.
 * 
 * Supported actions:
 * - "deny": block the tool call with a message
 * - "log": log the call and allow it
 * - "confirm": require user confirmation (currently simplified to deny with message)
 */
class UserDefinedHook @Inject constructor(
    private val registry: UserHookRegistry
) : HookHandler<HookEvent.PreToolUse> {

    override suspend fun handle(event: HookEvent.PreToolUse): HookDecision {
        val hooks = registry.getHooks().filter { it.enabled && it.event == "PreToolUse" }

        for (config in hooks) {
            // Check if matcher regex matches the tool name
            val matches = try {
                Regex(config.matcher).matches(event.toolName)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid regex in hook ${config.id}: ${config.matcher}", e)
                false
            }

            if (!matches) continue

            // Apply action
            return when (config.action) {
                "deny" -> {
                    Log.i(TAG, "UserHook [deny]: tool=${event.toolName} — ${config.message}")
                    HookDecision.Deny(config.message)
                }
                "log" -> {
                    Log.i(TAG, "UserHook [log]: tool=${event.toolName} — ${config.message}")
                    HookDecision.Allow
                }
                "confirm" -> {
                    // Simplified: treat as deny with confirmation message
                    // Full implementation would emit AgentEvent.ConfirmRequired and wait for user response
                    Log.i(TAG, "UserHook [confirm]: tool=${event.toolName} — ${config.message}")
                    HookDecision.Deny("Confirmation required: ${config.message}")
                }
                else -> {
                    Log.w(TAG, "Unknown action in hook ${config.id}: ${config.action}")
                    HookDecision.Allow
                }
            }
        }

        return HookDecision.Allow
    }
}
