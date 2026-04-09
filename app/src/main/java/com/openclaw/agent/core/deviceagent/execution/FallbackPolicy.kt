package com.openclaw.agent.core.deviceagent.execution

import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.capability.CapabilitySet
import com.openclaw.agent.core.deviceagent.model.DeviceAction

class FallbackPolicy {
    suspend fun routesFor(action: DeviceAction, capabilities: CapabilitySet): List<CapabilityRoute> {
        val primary = capabilities.preferredRouteFor(action)
        if (primary == CapabilityRoute.NONE) return emptyList()

        val routes = mutableListOf(primary)
        val available = capabilities.availableCapabilities()

        when (primary) {
            CapabilityRoute.ACCESSIBILITY -> {
                if (com.openclaw.agent.core.deviceagent.capability.CapabilityType.SHELL in available) {
                    routes += CapabilityRoute.SHELL
                }
            }
            CapabilityRoute.APP_CONTROL -> {
                if (com.openclaw.agent.core.deviceagent.capability.CapabilityType.SHELL in available) {
                    routes += CapabilityRoute.SHELL
                }
            }
            CapabilityRoute.SHELL,
            CapabilityRoute.NONE -> Unit
        }

        return routes.distinct()
    }
}
