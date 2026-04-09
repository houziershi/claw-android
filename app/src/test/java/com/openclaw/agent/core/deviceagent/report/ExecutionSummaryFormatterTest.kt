package com.openclaw.agent.core.deviceagent.report

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionReport
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.model.UiBounds
import com.openclaw.agent.core.deviceagent.model.UiLocator
import org.junit.Test

class ExecutionSummaryFormatterTest {

    private val formatter = ExecutionSummaryFormatter()

    @Test
    fun format_buildsStructuredSummaryAndMarkdown() {
        val report = ExecutionReport(
            planId = "settings-smoke",
            status = ExecutionStatus.FAILED,
            steps = listOf(
                ExecutionStepResult(
                    stepId = "open-settings",
                    status = ExecutionStatus.PASSED,
                    matchedLocator = UiLocator(contentDescription = "Settings"),
                    artifacts = listOf(
                        ExecutionArtifact(
                            type = ArtifactType.SCREENSHOT,
                            path = "/tmp/step1.png",
                            label = "screenshot"
                        )
                    ),
                    durationMs = 1200
                ),
                ExecutionStepResult(
                    stepId = "save-settings",
                    status = ExecutionStatus.FAILED,
                    matchedLocator = UiLocator(bounds = UiBounds(0, 0, 100, 40)),
                    failure = ExecutionFailure(
                        code = "shell_command_failed",
                        message = "input tap failed",
                        recoverable = true
                    ),
                    artifacts = listOf(
                        ExecutionArtifact(
                            type = ArtifactType.LOGCAT,
                            path = "/tmp/logcat.log",
                            label = "logcat"
                        ),
                        ExecutionArtifact(
                            type = ArtifactType.UI_TREE,
                            path = "/tmp/ui-tree.json",
                            label = "ui-tree"
                        )
                    )
                )
            ),
            artifacts = listOf(
                ExecutionArtifact(
                    type = ArtifactType.COVERAGE,
                    path = "/tmp/coverage.ec",
                    label = "coverage"
                )
            )
        )

        val summary = formatter.format(report)

        assertThat(summary.planId).isEqualTo("settings-smoke")
        assertThat(summary.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(summary.totalSteps).isEqualTo(2)
        assertThat(summary.passedSteps).isEqualTo(1)
        assertThat(summary.failedSteps).isEqualTo(1)
        assertThat(summary.runArtifactCount).isEqualTo(1)
        assertThat(summary.stepArtifactCount).isEqualTo(3)
        assertThat(summary.primaryFailure?.code).isEqualTo("shell_command_failed")
        assertThat(summary.failureSteps.single().stepId).isEqualTo("save-settings")
        assertThat(summary.failureSteps.single().artifactLabels).containsExactly("logcat", "ui-tree")
        assertThat(summary.recentArtifacts.map { it.label }).containsAtLeast("coverage", "screenshot", "logcat", "ui-tree")
        assertThat(summary.markdown).contains("# Device Execution Report")
        assertThat(summary.markdown).contains("`save-settings` — `FAILED`")
        assertThat(summary.markdown).contains("Primary failure: `shell_command_failed` — input tap failed")
        assertThat(summary.markdown).contains("bounds=0,0,100,40")
    }

    @Test
    fun format_handlesCleanPassedReport() {
        val report = ExecutionReport(
            planId = "launch-only",
            status = ExecutionStatus.PASSED,
            steps = listOf(
                ExecutionStepResult(stepId = "launch", status = ExecutionStatus.PASSED)
            )
        )

        val summary = formatter.format(report)

        assertThat(summary.failureSteps).isEmpty()
        assertThat(summary.primaryFailure).isNull()
        assertThat(summary.markdown).contains("Status: `PASSED`")
        assertThat(summary.markdown).doesNotContain("Primary failure")
    }
}
