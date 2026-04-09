package com.openclaw.agent.core.deviceagent.accessibility

object AccessibilityServiceStatus {
    fun isServiceEnabled(enabledServices: String?, expectedComponent: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        val expected = expectedComponent.trim().lowercase()
        return enabledServices
            .split(':')
            .map { it.trim().lowercase() }
            .any { it == expected }
    }
}
