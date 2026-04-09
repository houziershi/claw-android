package com.openclaw.agent.core.deviceagent.capability

import com.openclaw.agent.core.deviceagent.model.DeviceAction

enum class CapabilityType {
    ACCESSIBILITY,
    SHELL,
    SCREENSHOT,
    APP_CONTROL
}

enum class CapabilityRoute {
    ACCESSIBILITY,
    SHELL,
    APP_CONTROL,
    NONE
}

data class CapabilitySet(
    val accessibility: AccessibilityCapability? = null,
    val shell: ShellCapability? = null,
    val screenshot: ScreenshotCapability? = null,
    val appControl: AppControlCapability? = null
) {
    suspend fun availableCapabilities(): Set<CapabilityType> {
        val available = linkedSetOf<CapabilityType>()
        if (accessibility?.isAvailable() == true) available += CapabilityType.ACCESSIBILITY
        if (shell?.isAvailable() == true) available += CapabilityType.SHELL
        if (screenshot?.isAvailable() == true) available += CapabilityType.SCREENSHOT
        if (appControl?.isAvailable() == true) available += CapabilityType.APP_CONTROL
        return available
    }

    suspend fun preferredRouteFor(action: DeviceAction): CapabilityRoute {
        val accessibilityReady = accessibility?.isAvailable() == true
        val shellReady = shell?.isAvailable() == true
        val appControlReady = appControl?.isAvailable() == true

        return when (action) {
            is DeviceAction.Tap,
            is DeviceAction.InputText,
            is DeviceAction.AssertTextContains,
            is DeviceAction.AssertVisible -> when {
                accessibilityReady -> CapabilityRoute.ACCESSIBILITY
                shellReady -> CapabilityRoute.SHELL
                else -> CapabilityRoute.NONE
            }
            is DeviceAction.Swipe,
            is DeviceAction.PressKey,
            is DeviceAction.ForceStopApp,
            is DeviceAction.ClearAppData,
            is DeviceAction.PullFile -> when {
                shellReady -> CapabilityRoute.SHELL
                else -> CapabilityRoute.NONE
            }
            is DeviceAction.LaunchApp -> when {
                appControlReady -> CapabilityRoute.APP_CONTROL
                shellReady -> CapabilityRoute.SHELL
                else -> CapabilityRoute.NONE
            }
            is DeviceAction.Wait -> CapabilityRoute.NONE
        }
    }
}
