package com.openclaw.agent.core.deviceagent.execution

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.capability.AccessibilityCapability
import com.openclaw.agent.core.deviceagent.capability.AppControlCapability
import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.capability.CapabilitySet
import com.openclaw.agent.core.deviceagent.capability.ShellCapability
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.UiLocator
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ActionExecutorTest {

    @Test
    fun execute_usesPreferredAccessibilityRouteWhenAvailable() = runTest {
        val executor = ActionExecutor(
            handlers = listOf(
                FakeRouteHandler(CapabilityRoute.ACCESSIBILITY, ExecutionStatus.PASSED),
                FakeRouteHandler(CapabilityRoute.SHELL, ExecutionStatus.PASSED)
            )
        )
        val capabilities = CapabilitySet(
            accessibility = AlwaysAvailableAccessibility(),
            shell = AlwaysAvailableShell()
        )
        val step = testTapStep()

        val result = executor.execute(step, capabilities)

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.failure).isNull()
        assertThat(result.artifacts.map { it.label }).containsExactly("route:ACCESSIBILITY")
    }

    @Test
    fun execute_fallsBackToShellWhenAccessibilityFailsRecoverably() = runTest {
        val executor = ActionExecutor(
            handlers = listOf(
                FakeRouteHandler(
                    CapabilityRoute.ACCESSIBILITY,
                    ExecutionStatus.FAILED,
                    failure = ExecutionFailure(
                        code = "accessibility_click_failed",
                        message = "node click returned false",
                        recoverable = true
                    )
                ),
                FakeRouteHandler(CapabilityRoute.SHELL, ExecutionStatus.PASSED)
            )
        )
        val capabilities = CapabilitySet(
            accessibility = AlwaysAvailableAccessibility(),
            shell = AlwaysAvailableShell()
        )

        val result = executor.execute(testTapStep(), capabilities)

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.artifacts.map { it.label }).containsExactly("route:SHELL")
    }

    @Test
    fun execute_prefersAppControlForLaunchApp() = runTest {
        val step = TestSteps.launchApp()
        val executor = ActionExecutor(
            handlers = listOf(
                FakeRouteHandler(CapabilityRoute.APP_CONTROL, ExecutionStatus.PASSED),
                FakeRouteHandler(CapabilityRoute.SHELL, ExecutionStatus.FAILED)
            )
        )
        val capabilities = CapabilitySet(
            appControl = AlwaysAvailableAppControl(),
            shell = AlwaysAvailableShell()
        )

        val result = executor.execute(step, capabilities)

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.artifacts.map { it.label }).containsExactly("route:APP_CONTROL")
    }

    @Test
    fun execute_returnsStructuredFailureWhenNoRouteAvailable() = runTest {
        val executor = ActionExecutor(emptyList())
        val capabilities = CapabilitySet()

        val result = executor.execute(testTapStep(), capabilities)

        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.failure?.code).isEqualTo("no_available_route")
    }

    @Test
    fun fallbackPolicy_stopsAfterNonRecoverableFailure() = runTest {
        val executor = ActionExecutor(
            handlers = listOf(
                FakeRouteHandler(
                    CapabilityRoute.ACCESSIBILITY,
                    ExecutionStatus.FAILED,
                    failure = ExecutionFailure(
                        code = "permission_denied",
                        message = "accessibility disabled",
                        recoverable = false
                    )
                ),
                FakeRouteHandler(CapabilityRoute.SHELL, ExecutionStatus.PASSED)
            )
        )
        val capabilities = CapabilitySet(
            accessibility = AlwaysAvailableAccessibility(),
            shell = AlwaysAvailableShell()
        )

        val result = executor.execute(testTapStep(), capabilities)

        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.failure?.code).isEqualTo("permission_denied")
    }

    private fun testTapStep() = TestSteps.tapSettings()
}

private object TestSteps {
    fun tapSettings() = com.openclaw.agent.core.deviceagent.model.AutomationStep(
        id = "tap-settings",
        action = DeviceAction.Tap(UiLocator(contentDescription = "Settings"))
    )

    fun launchApp() = com.openclaw.agent.core.deviceagent.model.AutomationStep(
        id = "launch-app",
        action = DeviceAction.LaunchApp(packageName = "com.openclaw.agent")
    )
}

private class FakeRouteHandler(
    override val route: CapabilityRoute,
    private val status: ExecutionStatus,
    private val failure: ExecutionFailure? = null
) : ActionRouteHandler {
    override suspend fun execute(step: com.openclaw.agent.core.deviceagent.model.AutomationStep): com.openclaw.agent.core.deviceagent.model.ExecutionStepResult {
        return com.openclaw.agent.core.deviceagent.model.ExecutionStepResult(
            stepId = step.id,
            status = status,
            failure = failure,
            artifacts = listOf(
                com.openclaw.agent.core.deviceagent.model.ExecutionArtifact(
                    type = com.openclaw.agent.core.deviceagent.model.ArtifactType.FILE,
                    path = route.name.lowercase(),
                    label = "route:${route.name}"
                )
            )
        )
    }
}

private class AlwaysAvailableAccessibility : AccessibilityCapability {
    override suspend fun isAvailable(): Boolean = true
}

private class AlwaysAvailableShell : ShellCapability {
    override suspend fun isAvailable(): Boolean = true
}

private class AlwaysAvailableAppControl : AppControlCapability {
    override suspend fun isAvailable(): Boolean = true
}
