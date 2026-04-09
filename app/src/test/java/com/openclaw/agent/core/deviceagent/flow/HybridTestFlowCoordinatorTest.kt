package com.openclaw.agent.core.deviceagent.flow

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.AutomationPlan
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.AutomationTarget
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionReport
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.model.UiLocator
import com.openclaw.agent.core.deviceagent.report.ExecutionSummary
import com.openclaw.agent.core.tools.impl.DeviceExecutePlanPayload
import org.junit.Test

class HybridTestFlowCoordinatorTest {

    @Test
    fun run_stopsAfterAndroidTestFailureByDefault() = kotlinx.coroutines.test.runTest {
        val coordinator = HybridTestFlowCoordinator(
            androidTestRunner = FakeAndroidTestRunner(
                AndroidTestRunResult(
                    status = ExecutionStatus.FAILED,
                    executedTask = "connectedDebugAndroidTest",
                    failureMessage = "instrumentation failed"
                )
            ),
            devicePlanExecutor = RecordingDevicePlanExecutor()
        )

        val result = coordinator.run(sampleRequest())

        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.devicePlan).isNull()
        assertThat(result.coverage).isNull()
        assertThat(result.markdown).contains("instrumentation failed")
    }

    @Test
    fun run_continuesWhenAndroidTestFailureIsAllowed() = kotlinx.coroutines.test.runTest {
        val coordinator = HybridTestFlowCoordinator(
            androidTestRunner = FakeAndroidTestRunner(
                AndroidTestRunResult(
                    status = ExecutionStatus.FAILED,
                    executedTask = "connectedDebugAndroidTest",
                    tests = listOf("AppLaunchSmokeTest"),
                    artifacts = listOf(
                        ExecutionArtifact(ArtifactType.FILE, "/tmp/android-test.xml", "android-test-report")
                    ),
                    failureMessage = "flaky smoke"
                )
            ),
            devicePlanExecutor = RecordingDevicePlanExecutor(
                payload = successPayload()
            )
        )

        val result = coordinator.run(sampleRequest(continueOnFailure = true))

        assertThat(result.devicePlan).isNotNull()
        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.artifacts.mapNotNull { it.label }).contains("android-test-report")
        assertThat(result.markdown).contains("Device Agent")
    }

    @Test
    fun run_returnsPassedWhenBothStagesPass() = kotlinx.coroutines.test.runTest {
        val coordinator = HybridTestFlowCoordinator(
            androidTestRunner = FakeAndroidTestRunner(
                AndroidTestRunResult(
                    status = ExecutionStatus.PASSED,
                    executedTask = "connectedDebugAndroidTest",
                    tests = listOf("AppLaunchSmokeTest", "SettingsFlowInstrumentedTest")
                )
            ),
            devicePlanExecutor = RecordingDevicePlanExecutor(
                payload = successPayload()
            )
        )

        val result = coordinator.run(sampleRequest())

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.devicePlan?.summary?.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.markdown).contains("AppLaunchSmokeTest")
        assertThat(result.markdown).contains("2/2 passed")
        assertThat(result.markdown).contains("Not requested")
    }

    @Test
    fun run_includesChangedLineCoverageWhenConfigured() = kotlinx.coroutines.test.runTest {
        val coordinator = HybridTestFlowCoordinator(
            androidTestRunner = FakeAndroidTestRunner(
                AndroidTestRunResult(
                    status = ExecutionStatus.PASSED,
                    executedTask = "connectedDebugAndroidTest",
                    tests = listOf("AppLaunchSmokeTest")
                )
            ),
            devicePlanExecutor = RecordingDevicePlanExecutor(
                payload = successPayload()
            ),
            coverageRunner = FakeChangedLineCoverageRunner(
                ChangedLineCoverageResult(
                    status = ExecutionStatus.PASSED,
                    baseRef = "origin/main",
                    changedFiles = 4,
                    matchedSourceFiles = 3,
                    changedExecutableLines = 83,
                    coveredChangedLines = 61,
                    coveragePercent = 73.49,
                    uncoveredLines = listOf(
                        ChangedLineRef("app/src/main/java/com/openclaw/agent/Foo.kt", 42)
                    ),
                    artifacts = listOf(
                        ExecutionArtifact(ArtifactType.COVERAGE, "/tmp/report.xml", "jacoco-xml")
                    )
                )
            )
        )

        val result = coordinator.run(
            sampleRequest(
                coverage = ChangedLineCoverageSpec(
                    baseRef = "origin/main",
                    xmlReportPath = "/tmp/report.xml",
                    sourceRoots = listOf("app/src/main/java", "app/src/main/kotlin")
                )
            )
        )

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.coverage?.coveragePercent).isEqualTo(73.49)
        assertThat(result.artifacts.mapNotNull { it.label }).contains("jacoco-xml")
        assertThat(result.markdown).contains("Changed-line coverage: 61/83 = 73.49%")
        assertThat(result.markdown).contains("Foo.kt:42")
    }

    private fun sampleRequest(
        continueOnFailure: Boolean = false,
        coverage: ChangedLineCoverageSpec? = null
    ) = HybridTestFlowRequest(
        runId = "hybrid-1",
        androidTest = AndroidTestSpec(
            gradleTask = "connectedDebugAndroidTest",
            testClasses = listOf("AppLaunchSmokeTest", "SettingsFlowInstrumentedTest")
        ),
        devicePlan = AutomationPlan(
            id = "device-plan-1",
            name = "settings-path",
            target = AutomationTarget(packageName = "com.openclaw.agent"),
            steps = listOf(
                AutomationStep(
                    id = "tap-settings",
                    action = DeviceAction.Tap(UiLocator(contentDescription = "Settings"))
                )
            )
        ),
        coverage = coverage,
        continueOnAndroidTestFailure = continueOnFailure
    )

    private fun successPayload() = DeviceExecutePlanPayload(
        report = ExecutionReport(
            planId = "device-plan-1",
            status = ExecutionStatus.PASSED,
            steps = listOf(
                ExecutionStepResult(stepId = "tap-settings", status = ExecutionStatus.PASSED),
                ExecutionStepResult(stepId = "save-settings", status = ExecutionStatus.PASSED)
            )
        ),
        summary = ExecutionSummary(
            planId = "device-plan-1",
            status = ExecutionStatus.PASSED,
            totalSteps = 2,
            passedSteps = 2,
            failedSteps = 0,
            runArtifactCount = 0,
            stepArtifactCount = 0,
            markdown = "# Device Execution Report"
        )
    )
}

private class FakeAndroidTestRunner(
    private val result: AndroidTestRunResult
) : AndroidTestRunner {
    override suspend fun run(spec: AndroidTestSpec): AndroidTestRunResult = result
}

private class RecordingDevicePlanExecutor(
    private val payload: DeviceExecutePlanPayload? = null
) : DevicePlanPayloadExecutor {
    var callCount = 0

    override suspend fun execute(plan: AutomationPlan): DeviceExecutePlanPayload {
        callCount += 1
        return payload ?: error("Device plan should not execute")
    }
}

private class FakeChangedLineCoverageRunner(
    private val result: ChangedLineCoverageResult
) : ChangedLineCoverageRunner {
    override suspend fun run(spec: ChangedLineCoverageSpec): ChangedLineCoverageResult = result
}
