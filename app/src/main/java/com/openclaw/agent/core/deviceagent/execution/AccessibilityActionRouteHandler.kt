package com.openclaw.agent.core.deviceagent.execution

import com.openclaw.agent.core.deviceagent.accessibility.TestAgentAccessibilityService
import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult

class AccessibilityActionRouteHandler(
    private val serviceProvider: () -> TestAgentAccessibilityService? = { TestAgentAccessibilityService.current() }
) : ActionRouteHandler {
    override val route: CapabilityRoute = CapabilityRoute.ACCESSIBILITY

    override suspend fun execute(step: AutomationStep): ExecutionStepResult {
        val service = serviceProvider() ?: return failure(
            step = step,
            code = "accessibility_unavailable",
            message = "Accessibility service is not connected",
            recoverable = true
        )

        return when (val action = step.action) {
            is DeviceAction.Tap -> {
                if (service.click(action.locator)) {
                    ExecutionStepResult(
                        stepId = step.id,
                        status = ExecutionStatus.PASSED,
                        matchedLocator = action.locator
                    )
                } else {
                    failure(step, "accessibility_click_failed", "Could not click target via accessibility", true, action.locator)
                }
            }
            is DeviceAction.InputText -> {
                if (action.clearBeforeInput) {
                    // placeholder for future clear action; current service setText overwrites content when supported.
                }
                if (service.setText(action.locator, action.text)) {
                    ExecutionStepResult(
                        stepId = step.id,
                        status = ExecutionStatus.PASSED,
                        matchedLocator = action.locator
                    )
                } else {
                    failure(step, "accessibility_set_text_failed", "Could not set text via accessibility", true, action.locator)
                }
            }
            is DeviceAction.AssertVisible -> {
                if (service.findNode(action.locator) != null) {
                    ExecutionStepResult(stepId = step.id, status = ExecutionStatus.PASSED, matchedLocator = action.locator)
                } else {
                    failure(step, "accessibility_assert_visible_failed", "Target is not visible", false, action.locator)
                }
            }
            is DeviceAction.AssertTextContains -> {
                val node = service.findNode(action.locator)
                val content = listOfNotNull(node?.text?.toString(), node?.contentDescription?.toString()).joinToString(" ")
                if (node != null && content.contains(action.expectedText)) {
                    ExecutionStepResult(stepId = step.id, status = ExecutionStatus.PASSED, matchedLocator = action.locator)
                } else {
                    failure(step, "accessibility_assert_text_failed", "Expected text '${action.expectedText}' was not found", false, action.locator)
                }
            }
            is DeviceAction.Swipe -> failure(
                step,
                "accessibility_swipe_unsupported",
                "Accessibility route does not support raw swipe without semantic locator",
                true
            )
            else -> failure(
                step = step,
                code = "accessibility_action_unsupported",
                message = "Accessibility route cannot execute ${step.action::class.simpleName}",
                recoverable = true
            )
        }
    }

    private fun failure(
        step: AutomationStep,
        code: String,
        message: String,
        recoverable: Boolean,
        locator: com.openclaw.agent.core.deviceagent.model.UiLocator? = null
    ) = ExecutionStepResult(
        stepId = step.id,
        status = ExecutionStatus.FAILED,
        matchedLocator = locator,
        failure = ExecutionFailure(code = code, message = message, recoverable = recoverable)
    )
}
