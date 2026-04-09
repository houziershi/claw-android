package com.openclaw.agent.core.tools.impl

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.capability.CapabilitySet
import com.openclaw.agent.core.deviceagent.capability.ShellCapability
import com.openclaw.agent.core.deviceagent.execution.ActionExecutor
import com.openclaw.agent.core.deviceagent.execution.ActionRouteHandler
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.AutomationPlan
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.AutomationTarget
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.model.UiLocator
import com.openclaw.agent.core.deviceagent.report.ArtifactStore
import com.openclaw.agent.core.deviceagent.report.ExecutionRecorder
import com.openclaw.agent.core.deviceagent.report.ExecutionSummaryFormatter
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import com.openclaw.agent.core.deviceagent.snapshot.UiNodeSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Test
import java.nio.file.Files

class DeviceExecutePlanToolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `execute returns structured payload for successful plan`() = runTest {
        val actionExecutor = ActionExecutor(
            handlers = listOf(SuccessShellHandler())
        )
        val tool = DeviceExecutePlanTool(
            planExecutor = DefaultDevicePlanExecutor(
                actionExecutor = actionExecutor,
                capabilitySet = CapabilitySet(shell = AlwaysAvailableShell()),
                recorder = ExecutionRecorder(ArtifactStore(Files.createTempDirectory("artifacts"))),
                formatter = ExecutionSummaryFormatter(),
                snapshotSource = FakePlanSnapshotSource(
                    DeviceSnapshot(
                        packageName = "com.openclaw.agent",
                        activityName = "MainActivity",
                        nodeCount = 1,
                        root = UiNodeSnapshot(path = "0", text = "Settings"),
                        searchIndex = "0 | Settings"
                    )
                )
            )
        )
        val plan = samplePlan()

        val result = tool.execute(buildJsonObject {
            put("plan", json.encodeToJsonElement(AutomationPlan.serializer(), plan))
        })
        val payload = json.decodeFromString(DeviceExecutePlanPayload.serializer(), result.content)

        assertThat(result.success).isTrue()
        assertThat(payload.report.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(payload.report.steps).hasSize(2)
        assertThat(payload.summary.totalSteps).isEqualTo(2)
        assertThat(payload.summary.markdown).contains("Device Execution Report")
        assertThat(payload.report.steps.first().artifacts.map { it.type }).contains(ArtifactType.UI_TREE)
    }

    @Test
    fun `execute stops on non optional failure`() = runTest {
        val actionExecutor = ActionExecutor(
            handlers = listOf(FailSecondShellHandler())
        )
        val tool = DeviceExecutePlanTool(
            planExecutor = DefaultDevicePlanExecutor(
                actionExecutor = actionExecutor,
                capabilitySet = CapabilitySet(shell = AlwaysAvailableShell()),
                recorder = ExecutionRecorder(ArtifactStore(Files.createTempDirectory("artifacts"))),
                formatter = ExecutionSummaryFormatter()
            )
        )
        val plan = samplePlan()

        val result = tool.execute(buildJsonObject {
            put("plan", json.encodeToJsonElement(AutomationPlan.serializer(), plan))
        })
        val payload = json.decodeFromString(DeviceExecutePlanPayload.serializer(), result.content)

        assertThat(payload.report.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(payload.report.steps).hasSize(2)
        assertThat(payload.summary.primaryFailure?.code).isEqualTo("boom")
    }

    @Test
    fun `missing plan returns error`() = runTest {
        val tool = DeviceExecutePlanTool(
            planExecutor = object : DevicePlanExecutor {
                override suspend fun execute(plan: AutomationPlan): DeviceExecutePlanPayload = error("unused")
            }
        )

        val result = tool.execute(buildJsonObject {})

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Missing 'plan'")
    }

    private fun samplePlan() = AutomationPlan(
        id = "plan-1",
        name = "Settings smoke",
        target = AutomationTarget(packageName = "com.openclaw.agent"),
        steps = listOf(
            AutomationStep(
                id = "step-1",
                action = DeviceAction.Tap(UiLocator(bounds = com.openclaw.agent.core.deviceagent.model.UiBounds(0, 0, 100, 40)))
            ),
            AutomationStep(
                id = "step-2",
                action = DeviceAction.PressKey(4)
            )
        )
    )
}

private class AlwaysAvailableShell : ShellCapability {
    override suspend fun isAvailable(): Boolean = true
}

private class SuccessShellHandler : ActionRouteHandler {
    override val route: CapabilityRoute = CapabilityRoute.SHELL

    override suspend fun execute(step: AutomationStep): ExecutionStepResult = ExecutionStepResult(
        stepId = step.id,
        status = ExecutionStatus.PASSED,
        artifacts = listOf(
            ExecutionArtifact(
                type = ArtifactType.FILE,
                path = "/tmp/${step.id}.txt",
                label = "shell"
            )
        )
    )
}

private class FailSecondShellHandler : ActionRouteHandler {
    override val route: CapabilityRoute = CapabilityRoute.SHELL
    private var callCount = 0

    override suspend fun execute(step: AutomationStep): ExecutionStepResult {
        callCount += 1
        return if (callCount == 2) {
            ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                failure = ExecutionFailure(code = "boom", message = "step failed", recoverable = false)
            )
        } else {
            ExecutionStepResult(stepId = step.id, status = ExecutionStatus.PASSED)
        }
    }
}

private class FakePlanSnapshotSource(
    private val snapshot: DeviceSnapshot
) : DeviceSnapshotSource {
    override suspend fun currentSnapshot(): DeviceSnapshot = snapshot
}
