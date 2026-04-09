package com.openclaw.agent.core.deviceagent.capability

interface ShellCapability {
    suspend fun isAvailable(): Boolean
}
