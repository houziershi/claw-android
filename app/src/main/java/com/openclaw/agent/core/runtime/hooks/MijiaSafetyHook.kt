package com.openclaw.agent.core.runtime.hooks

import android.util.Log

private const val TAG = "MijiaSafetyHook"

/**
 * Safety hook for Mijia device control — requires user confirmation
 * before operating sensitive devices (locks, cameras, security systems).
 *
 * Registered as a PreToolUse hook with matcher = "mijia_control".
 */
class MijiaSafetyHook : HookHandler<HookEvent.PreToolUse> {

    private val sensitiveKeywords = listOf(
        "锁", "门禁", "摄像", "安防", "监控", "门锁", "智能锁",
        "lock", "camera", "security", "surveillance", "doorbell"
    )

    override suspend fun handle(event: HookEvent.PreToolUse): HookDecision {
        // Only intercept mijia_control set actions
        val action = event.toolInput["action"]?.toString()?.trim('"')
        if (action != "set") return HookDecision.Allow

        val deviceName = event.toolInput["name"]?.toString()?.trim('"') ?: ""

        val isSensitive = sensitiveKeywords.any { keyword ->
            deviceName.contains(keyword, ignoreCase = true)
        }

        if (isSensitive) {
            Log.w(TAG, "Blocked sensitive device operation: $deviceName")
            return HookDecision.Deny(
                "⚠️ 检测到对敏感设备「$deviceName」的操作请求。出于安全考虑，请先向用户确认是否允许此操作，然后再重新调用。"
            )
        }

        return HookDecision.Allow
    }
}
