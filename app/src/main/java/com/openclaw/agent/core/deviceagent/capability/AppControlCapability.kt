package com.openclaw.agent.core.deviceagent.capability

interface AppControlCapability {
    suspend fun isAvailable(): Boolean
}
