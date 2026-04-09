package com.openclaw.agent.core.deviceagent.report

import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionReport
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path

class ExecutionRecorder(
    private val artifactStore: ArtifactStore,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
) {
    fun attachStepArtifacts(
        runId: String,
        stepResult: ExecutionStepResult,
        snapshot: DeviceSnapshot? = null,
        logcat: String? = null,
        screenshotPath: Path? = null,
        coveragePath: Path? = null,
        stacktrace: String? = null
    ): ExecutionStepResult {
        val recordedArtifacts = buildList {
            if (snapshot != null) {
                add(
                    artifactStore.saveText(
                        runId = runId,
                        stepId = stepResult.stepId,
                        type = ArtifactType.UI_TREE,
                        label = "ui-tree",
                        content = json.encodeToString(DeviceSnapshot.serializer(), snapshot),
                        extension = "json",
                        metadata = mapOf(
                            "nodeCount" to snapshot.nodeCount.toString(),
                            "packageName" to snapshot.packageName
                        ) + listOfNotNull(
                            snapshot.activityName?.let { "activityName" to it }
                        ).toMap()
                    )
                )
            }
            if (!logcat.isNullOrBlank()) {
                add(
                    artifactStore.saveText(
                        runId = runId,
                        stepId = stepResult.stepId,
                        type = ArtifactType.LOGCAT,
                        label = "logcat",
                        content = logcat,
                        extension = "log"
                    )
                )
            }
            if (screenshotPath != null) {
                add(
                    artifactStore.copyFile(
                        runId = runId,
                        stepId = stepResult.stepId,
                        type = ArtifactType.SCREENSHOT,
                        label = "screenshot",
                        sourcePath = screenshotPath
                    )
                )
            }
            if (coveragePath != null) {
                add(
                    artifactStore.copyFile(
                        runId = runId,
                        stepId = stepResult.stepId,
                        type = ArtifactType.COVERAGE,
                        label = "coverage",
                        sourcePath = coveragePath
                    )
                )
            }
            if (!stacktrace.isNullOrBlank()) {
                add(
                    artifactStore.saveText(
                        runId = runId,
                        stepId = stepResult.stepId,
                        type = ArtifactType.FILE,
                        label = "stacktrace",
                        content = stacktrace,
                        extension = "txt"
                    )
                )
            }
        }

        return stepResult.copy(artifacts = stepResult.artifacts + recordedArtifacts)
    }

    fun finalizeReport(
        planId: String,
        steps: List<ExecutionStepResult>,
        runArtifacts: List<ExecutionArtifact> = emptyList()
    ): ExecutionReport {
        val status = when {
            steps.any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
            steps.any { it.status == ExecutionStatus.RUNNING } -> ExecutionStatus.RUNNING
            steps.all { it.status == ExecutionStatus.SKIPPED } && steps.isNotEmpty() -> ExecutionStatus.SKIPPED
            else -> ExecutionStatus.PASSED
        }
        return ExecutionReport(
            planId = planId,
            status = status,
            steps = steps,
            artifacts = runArtifacts
        )
    }
}
