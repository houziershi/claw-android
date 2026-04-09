package com.openclaw.agent.core.deviceagent.execution

import android.content.Context
import android.content.Intent
import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult

class AppControlActionRouteHandler(
    private val context: Context
) : ActionRouteHandler {
    override val route: CapabilityRoute = CapabilityRoute.APP_CONTROL

    override suspend fun execute(step: AutomationStep): ExecutionStepResult {
        val action = step.action
        if (action !is DeviceAction.LaunchApp) {
            return ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                failure = ExecutionFailure(
                    code = "app_control_action_unsupported",
                    message = "App control route only supports LaunchApp",
                    recoverable = true
                )
            )
        }

        return try {
            val intent = if (action.activityName != null) {
                Intent().setClassName(action.packageName, action.activityName)
            } else {
                context.packageManager.getLaunchIntentForPackage(action.packageName)
            } ?: return ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                failure = ExecutionFailure(
                    code = "launch_intent_not_found",
                    message = "No launch intent found for ${action.packageName}",
                    recoverable = true
                )
            )

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.PASSED
            )
        } catch (t: Throwable) {
            ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                failure = ExecutionFailure(
                    code = "launch_app_failed",
                    message = t.message ?: "Unknown launch error",
                    recoverable = true
                )
            )
        }
    }
}
