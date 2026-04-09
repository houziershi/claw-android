package com.openclaw.agent.core.deviceagent.capability

interface ScreenshotCapability {
    suspend fun isAvailable(): Boolean
}
