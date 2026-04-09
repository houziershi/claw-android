package com.openclaw.agent.core.deviceagent.shell

import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.UiBounds

internal data class ShellCommandSpec(
    val command: String,
    val requiresRoot: Boolean = false,
    val matchedBounds: UiBounds? = null
)

internal object ShellCommandBuilder {
    fun build(action: DeviceAction): ShellCommandSpec? {
        return when (action) {
            is DeviceAction.Tap -> {
                val bounds = action.locator.bounds ?: return null
                ShellCommandSpec(
                    command = "input tap ${bounds.centerX()} ${bounds.centerY()}",
                    matchedBounds = bounds
                )
            }
            is DeviceAction.InputText -> {
                val bounds = action.locator.bounds ?: return null
                val escapedText = action.text.toShellInputText()
                val command = buildString {
                    append("input tap ${bounds.centerX()} ${bounds.centerY()}")
                    append(" && ")
                    append("input text '$escapedText'")
                }
                ShellCommandSpec(command = command, matchedBounds = bounds)
            }
            is DeviceAction.Swipe -> ShellCommandSpec(
                command = "input swipe ${action.startX} ${action.startY} ${action.endX} ${action.endY} ${action.durationMs}"
            )
            is DeviceAction.LaunchApp -> ShellCommandSpec(
                command = action.toLaunchCommand()
            )
            is DeviceAction.PressKey -> ShellCommandSpec(
                command = "input keyevent ${action.keyCode}"
            )
            is DeviceAction.ForceStopApp -> ShellCommandSpec(
                command = "am force-stop ${action.packageName}"
            )
            is DeviceAction.ClearAppData -> ShellCommandSpec(
                command = "pm clear ${action.packageName}"
            )
            is DeviceAction.PullFile -> ShellCommandSpec(
                command = "cp ${action.sourcePath.shellQuoted()} ${action.destinationPath.shellQuoted()}",
                requiresRoot = action.requiresRoot
            )
            is DeviceAction.Wait -> ShellCommandSpec(
                command = "sleep ${action.durationMs.toSecondsString()}"
            )
            is DeviceAction.AssertTextContains,
            is DeviceAction.AssertVisible -> null
        }
    }

    private fun DeviceAction.LaunchApp.toLaunchCommand(): String {
        return activityName?.let { activity ->
            "am start -n ${packageName.shellQuoted()}/${activity.shellQuoted()}"
        } ?: "monkey -p ${packageName.shellQuoted()} -c android.intent.category.LAUNCHER 1"
    }

    private fun UiBounds.centerX(): Int = left + ((right - left) / 2)
    private fun UiBounds.centerY(): Int = top + ((bottom - top) / 2)

    private fun Long.toSecondsString(): String {
        return (this.toDouble() / 1_000.0).toString()
    }
}

internal fun String.shellQuoted(): String = buildString {
    append('\'')
    append(this@shellQuoted.replace("'", "'\"'\"'"))
    append('\'')
}

internal fun String.toShellInputText(): String {
    return replace("%", "%25")
        .replace(" ", "%s")
        .replace("'", "\\'")
}
