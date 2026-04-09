package com.openclaw.agent.core.deviceagent.report

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import com.openclaw.agent.core.deviceagent.snapshot.UiNodeSnapshot
import org.junit.Test
import java.nio.file.Files

class ExecutionRecorderTest {

    @Test
    fun attachStepArtifacts_persistsSnapshotLogsAndFileCopies() {
        val rootDir = Files.createTempDirectory("artifact-store")
        val recorder = ExecutionRecorder(ArtifactStore(rootDir))
        val screenshot = Files.createTempFile("screen", ".png").apply { Files.write(this, "png".toByteArray()) }
        val coverage = Files.createTempFile("coverage", ".ec").apply { Files.write(this, "ec".toByteArray()) }
        val baseResult = ExecutionStepResult(
            stepId = "step-1",
            status = ExecutionStatus.PASSED,
            artifacts = listOf(
                ExecutionArtifact(
                    type = ArtifactType.FILE,
                    path = "/tmp/existing.txt",
                    label = "existing"
                )
            )
        )

        val enriched = recorder.attachStepArtifacts(
            runId = "run-42",
            stepResult = baseResult,
            snapshot = sampleSnapshot(),
            logcat = "E/Test: boom",
            screenshotPath = screenshot,
            coveragePath = coverage,
            stacktrace = "IllegalStateException"
        )

        assertThat(enriched.artifacts).hasSize(6)
        assertThat(enriched.artifacts.mapNotNull { it.label }).containsAtLeast(
            "existing",
            "ui-tree",
            "logcat",
            "screenshot",
            "coverage",
            "stacktrace"
        )

        val uiTree = enriched.artifacts.first { it.label == "ui-tree" }
        val screenshotArtifact = enriched.artifacts.first { it.label == "screenshot" }
        assertThat(Files.exists(java.nio.file.Path.of(uiTree.path))).isTrue()
        assertThat(String(Files.readAllBytes(java.nio.file.Path.of(uiTree.path)))).contains("settings_save")
        assertThat(Files.exists(java.nio.file.Path.of(screenshotArtifact.path))).isTrue()
        assertThat(screenshotArtifact.metadata["sourcePath"]).isEqualTo(screenshot.toString())
    }

    @Test
    fun finalizeReport_derivesOverallStatusFromSteps() {
        val recorder = ExecutionRecorder(ArtifactStore(Files.createTempDirectory("artifact-store")))
        val report = recorder.finalizeReport(
            planId = "plan-1",
            steps = listOf(
                ExecutionStepResult(stepId = "a", status = ExecutionStatus.PASSED),
                ExecutionStepResult(stepId = "b", status = ExecutionStatus.FAILED)
            )
        )

        assertThat(report.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(report.failedSteps).isEqualTo(1)
    }

    private fun sampleSnapshot() = DeviceSnapshot(
        packageName = "com.openclaw.agent",
        activityName = "MainActivity",
        nodeCount = 1,
        root = UiNodeSnapshot(
            path = "0",
            viewIdResourceName = "com.openclaw.agent:id/settings_save",
            className = "android.widget.Button"
        ),
        searchIndex = "0 settings_save"
    )
}
