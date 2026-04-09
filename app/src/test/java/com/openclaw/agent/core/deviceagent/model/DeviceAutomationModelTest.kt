package com.openclaw.agent.core.deviceagent.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class DeviceAutomationModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun automationPlan_roundTripsWithStructuredLocatorsActionsAndArtifacts() {
        val plan = AutomationPlan(
            id = "plan-1",
            name = "Settings smoke",
            target = AutomationTarget(packageName = "com.openclaw.agent"),
            steps = listOf(
                AutomationStep(
                    id = "step-open-settings",
                    action = DeviceAction.Tap(
                        locator = UiLocator(resourceId = "com.openclaw.agent:id/settings")
                    ),
                    timeoutMs = 3_000
                ),
                AutomationStep(
                    id = "step-fill-api-key",
                    action = DeviceAction.InputText(
                        locator = UiLocator(testTag = "settings_api_key"),
                        text = "sk-test"
                    )
                ),
                AutomationStep(
                    id = "step-send-back",
                    action = DeviceAction.PressKey(keyCode = 4)
                ),
                AutomationStep(
                    id = "step-assert-title",
                    action = DeviceAction.AssertTextContains(
                        locator = UiLocator(text = "Settings"),
                        expectedText = "Settings"
                    )
                )
            )
        )

        val encoded = json.encodeToString(AutomationPlan.serializer(), plan)
        val decoded = json.decodeFromString(AutomationPlan.serializer(), encoded)

        assertThat(decoded).isEqualTo(plan)
        assertThat(decoded.steps).hasSize(4)
        assertThat((decoded.steps[1].action as DeviceAction.InputText).locator.testTag)
            .isEqualTo("settings_api_key")
        assertThat(decoded.steps[2].action).isEqualTo(DeviceAction.PressKey(keyCode = 4))
    }

    @Test
    fun executionReport_exposesSummaryMetricsAndFailureStatus() {
        val report = ExecutionReport(
            planId = "plan-1",
            status = ExecutionStatus.FAILED,
            steps = listOf(
                ExecutionStepResult(
                    stepId = "step-open-settings",
                    status = ExecutionStatus.PASSED,
                    matchedLocator = UiLocator(contentDescription = "Settings")
                ),
                ExecutionStepResult(
                    stepId = "step-save",
                    status = ExecutionStatus.FAILED,
                    failure = ExecutionFailure(
                        code = "locator_not_found",
                        message = "Could not find save button"
                    ),
                    artifacts = listOf(
                        ExecutionArtifact(
                            type = ArtifactType.SCREENSHOT,
                            path = "/tmp/failure.png"
                        )
                    )
                )
            )
        )

        assertThat(report.totalSteps).isEqualTo(2)
        assertThat(report.passedSteps).isEqualTo(1)
        assertThat(report.failedSteps).isEqualTo(1)
        assertThat(report.isSuccess).isFalse()
        assertThat(report.primaryFailure?.code).isEqualTo("locator_not_found")
    }

    @Test
    fun uiLocator_requiresAtLeastOneSelector() {
        val error = runCatching { UiLocator() }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("at least one selector")
    }
}
