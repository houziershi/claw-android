package com.openclaw.agent.core.deviceagent.capability

interface AccessibilityCapability {
    suspend fun isAvailable(): Boolean
}
