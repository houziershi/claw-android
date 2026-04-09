package com.openclaw.agent.core.deviceagent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DeviceAction {
    @Serializable
    @SerialName("tap")
    data class Tap(
        val locator: UiLocator
    ) : DeviceAction()

    @Serializable
    @SerialName("input_text")
    data class InputText(
        val locator: UiLocator,
        val text: String,
        val clearBeforeInput: Boolean = true
    ) : DeviceAction()

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Long = 300
    ) : DeviceAction()

    @Serializable
    @SerialName("wait")
    data class Wait(
        val durationMs: Long
    ) : DeviceAction()

    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(
        val packageName: String,
        val activityName: String? = null
    ) : DeviceAction()

    @Serializable
    @SerialName("press_key")
    data class PressKey(
        val keyCode: Int
    ) : DeviceAction()

    @Serializable
    @SerialName("force_stop_app")
    data class ForceStopApp(
        val packageName: String
    ) : DeviceAction()

    @Serializable
    @SerialName("clear_app_data")
    data class ClearAppData(
        val packageName: String
    ) : DeviceAction()

    @Serializable
    @SerialName("pull_file")
    data class PullFile(
        val sourcePath: String,
        val destinationPath: String,
        val requiresRoot: Boolean = false
    ) : DeviceAction()

    @Serializable
    @SerialName("assert_text_contains")
    data class AssertTextContains(
        val locator: UiLocator,
        val expectedText: String
    ) : DeviceAction()

    @Serializable
    @SerialName("assert_visible")
    data class AssertVisible(
        val locator: UiLocator
    ) : DeviceAction()
}

@Serializable
data class AutomationStep(
    val id: String,
    val action: DeviceAction,
    val timeoutMs: Long? = null,
    val optional: Boolean = false,
    val note: String? = null
)

@Serializable
data class AutomationPlan(
    val id: String,
    val name: String,
    val target: AutomationTarget,
    val steps: List<AutomationStep>
)
