package com.openclaw.agent.core.deviceagent.report

import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionReport
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionSummary(
    val planId: String,
    val status: ExecutionStatus,
    val totalSteps: Int,
    val passedSteps: Int,
    val failedSteps: Int,
    val runArtifactCount: Int,
    val stepArtifactCount: Int,
    val primaryFailure: ExecutionFailure? = null,
    val failureSteps: List<FailureStepSummary> = emptyList(),
    val recentArtifacts: List<ArtifactSummary> = emptyList(),
    val markdown: String
)

@Serializable
data class FailureStepSummary(
    val stepId: String,
    val code: String,
    val message: String,
    val recoverable: Boolean,
    val artifactLabels: List<String> = emptyList()
)

@Serializable
data class ArtifactSummary(
    val label: String,
    val type: ArtifactType,
    val path: String,
    val owner: String,
    val metadata: Map<String, String> = emptyMap()
)

class ExecutionSummaryFormatter {
    fun format(report: ExecutionReport): ExecutionSummary {
        val stepArtifacts = report.steps.flatMap { step ->
            step.artifacts.map { artifact ->
                artifact.toSummary(owner = step.stepId)
            }
        }
        val runArtifacts = report.artifacts.map { it.toSummary(owner = "run") }
        val recentArtifacts = (runArtifacts + stepArtifacts)
            .sortedBy { it.owner }
            .takeLast(8)

        return ExecutionSummary(
            planId = report.planId,
            status = report.status,
            totalSteps = report.totalSteps,
            passedSteps = report.passedSteps,
            failedSteps = report.failedSteps,
            runArtifactCount = report.artifacts.size,
            stepArtifactCount = stepArtifacts.size,
            primaryFailure = report.primaryFailure,
            failureSteps = report.steps.filter { it.status == ExecutionStatus.FAILED }.map { it.toFailureSummary() },
            recentArtifacts = recentArtifacts,
            markdown = renderMarkdown(report, runArtifacts, stepArtifacts)
        )
    }

    private fun renderMarkdown(
        report: ExecutionReport,
        runArtifacts: List<ArtifactSummary>,
        stepArtifacts: List<ArtifactSummary>
    ): String {
        val lines = mutableListOf<String>()
        lines += "# Device Execution Report"
        lines += ""
        lines += "- Plan: `${report.planId}`"
        lines += "- Status: `${report.status}`"
        lines += "- Steps: ${report.passedSteps}/${report.totalSteps} passed, ${report.failedSteps} failed"
        lines += "- Artifacts: ${runArtifacts.size} run-level, ${stepArtifacts.size} step-level"
        report.primaryFailure?.let {
            lines += "- Primary failure: `${it.code}` — ${it.message}"
        }
        lines += ""
        lines += "## Steps"
        lines += ""
        report.steps.forEach { step ->
            lines += renderStep(step)
        }
        if (runArtifacts.isNotEmpty()) {
            lines += ""
            lines += "## Run Artifacts"
            lines += ""
            runArtifacts.forEach { artifact ->
                lines += "- `${artifact.type}` ${artifact.label}: `${artifact.path}`"
            }
        }
        return lines.joinToString("\n")
    }

    private fun renderStep(step: ExecutionStepResult): String {
        val lines = mutableListOf<String>()
        lines += "### `${step.stepId}` — `${step.status}`"
        step.failure?.let {
            lines += "- Failure: `${it.code}` — ${it.message}${if (it.recoverable) " (recoverable)" else ""}"
        }
        step.matchedLocator?.let {
            val locatorParts = listOfNotNull(
                it.resourceId?.let { value -> "resourceId=$value" },
                it.testTag?.let { value -> "testTag=$value" },
                it.text?.let { value -> "text=$value" },
                it.contentDescription?.let { value -> "contentDescription=$value" },
                it.bounds?.let { value -> "bounds=${value.left},${value.top},${value.right},${value.bottom}" }
            )
            if (locatorParts.isNotEmpty()) {
                lines += "- Matched locator: ${locatorParts.joinToString(", ")}"
            }
        }
        step.durationMs?.let {
            lines += "- Duration: ${it}ms"
        }
        if (step.artifacts.isNotEmpty()) {
            lines += "- Artifacts:"
            step.artifacts.forEach { artifact ->
                lines += "  - `${artifact.type}` ${artifact.label ?: artifact.path}: `${artifact.path}`"
            }
        }
        lines += ""
        return lines.joinToString("\n")
    }

    private fun ExecutionStepResult.toFailureSummary(): FailureStepSummary {
        val failure = requireNotNull(failure)
        return FailureStepSummary(
            stepId = stepId,
            code = failure.code,
            message = failure.message,
            recoverable = failure.recoverable,
            artifactLabels = artifacts.mapNotNull { it.label }
        )
    }

    private fun ExecutionArtifact.toSummary(owner: String): ArtifactSummary {
        return ArtifactSummary(
            label = label ?: path.substringAfterLast('/'),
            type = type,
            path = path,
            owner = owner,
            metadata = metadata
        )
    }
}
