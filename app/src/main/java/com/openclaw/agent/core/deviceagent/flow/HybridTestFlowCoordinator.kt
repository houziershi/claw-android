package com.openclaw.agent.core.deviceagent.flow

import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.AutomationPlan
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.report.ExecutionSummary
import com.openclaw.agent.core.tools.impl.DeviceExecutePlanPayload
import kotlinx.serialization.Serializable

interface AndroidTestRunner {
    suspend fun run(spec: AndroidTestSpec): AndroidTestRunResult
}

@Serializable
data class AndroidTestSpec(
    val gradleTask: String = "connectedDebugAndroidTest",
    val testClasses: List<String> = emptyList()
)

@Serializable
data class AndroidTestRunResult(
    val status: ExecutionStatus,
    val executedTask: String,
    val tests: List<String> = emptyList(),
    val artifacts: List<ExecutionArtifact> = emptyList(),
    val output: String? = null,
    val failureMessage: String? = null
)

@Serializable
data class HybridTestFlowRequest(
    val runId: String,
    val androidTest: AndroidTestSpec,
    val devicePlan: AutomationPlan,
    val coverage: ChangedLineCoverageSpec? = null,
    val continueOnAndroidTestFailure: Boolean = false
)

@Serializable
data class HybridTestFlowResult(
    val runId: String,
    val status: ExecutionStatus,
    val androidTest: AndroidTestRunResult,
    val devicePlan: DeviceExecutePlanPayload? = null,
    val coverage: ChangedLineCoverageResult? = null,
    val artifacts: List<ExecutionArtifact> = emptyList(),
    val markdown: String
)

interface DevicePlanPayloadExecutor {
    suspend fun execute(plan: AutomationPlan): DeviceExecutePlanPayload
}

class DevicePlanPayloadExecutorAdapter(
    private val executor: com.openclaw.agent.core.tools.impl.DevicePlanExecutor
) : DevicePlanPayloadExecutor {
    override suspend fun execute(plan: AutomationPlan): DeviceExecutePlanPayload = executor.execute(plan)
}

class HybridTestFlowCoordinator(
    private val androidTestRunner: AndroidTestRunner,
    private val devicePlanExecutor: DevicePlanPayloadExecutor,
    private val coverageRunner: ChangedLineCoverageRunner? = null
) {
    suspend fun run(request: HybridTestFlowRequest): HybridTestFlowResult {
        val androidTestResult = androidTestRunner.run(request.androidTest)
        val androidArtifacts = androidTestResult.artifacts

        if (androidTestResult.status == ExecutionStatus.FAILED && !request.continueOnAndroidTestFailure) {
            return HybridTestFlowResult(
                runId = request.runId,
                status = ExecutionStatus.FAILED,
                androidTest = androidTestResult,
                artifacts = androidArtifacts,
                markdown = renderMarkdown(request.runId, androidTestResult, null, null)
            )
        }

        val devicePlanResult = devicePlanExecutor.execute(request.devicePlan)
        val coverageResult = if (request.coverage != null && coverageRunner != null) {
            coverageRunner.run(request.coverage)
        } else {
            null
        }
        val allArtifacts = buildList {
            addAll(androidArtifacts)
            addAll(devicePlanResult.report.artifacts)
            addAll(coverageResult?.artifacts.orEmpty())
        }

        val finalStatus = when {
            androidTestResult.status == ExecutionStatus.FAILED ||
                devicePlanResult.report.status == ExecutionStatus.FAILED ||
                coverageResult?.status == ExecutionStatus.FAILED -> ExecutionStatus.FAILED

            androidTestResult.status == ExecutionStatus.RUNNING ||
                devicePlanResult.report.status == ExecutionStatus.RUNNING ||
                coverageResult?.status == ExecutionStatus.RUNNING -> ExecutionStatus.RUNNING

            else -> ExecutionStatus.PASSED
        }
        return HybridTestFlowResult(
            runId = request.runId,
            status = finalStatus,
            androidTest = androidTestResult,
            devicePlan = devicePlanResult,
            coverage = coverageResult,
            artifacts = allArtifacts,
            markdown = renderMarkdown(request.runId, androidTestResult, devicePlanResult.summary, coverageResult)
        )
    }

    private fun renderMarkdown(
        runId: String,
        androidTestResult: AndroidTestRunResult,
        deviceSummary: ExecutionSummary?,
        coverage: ChangedLineCoverageResult?
    ): String {
        val lines = mutableListOf<String>()
        lines += "# Hybrid Device Test Flow"
        lines += ""
        lines += "- Run: `$runId`"
        lines += "- androidTest: `${androidTestResult.status}` via `${androidTestResult.executedTask}`"
        if (!androidTestResult.failureMessage.isNullOrBlank()) {
            lines += "- androidTest failure: ${androidTestResult.failureMessage}"
        }
        if (androidTestResult.tests.isNotEmpty()) {
            lines += "- androidTest suites: ${androidTestResult.tests.joinToString(", ")}"
        }
        lines += ""
        lines += "## Device Agent"
        lines += ""
        if (deviceSummary == null) {
            lines += "Device plan not executed."
        } else {
            lines += "- Status: `${deviceSummary.status}`"
            lines += "- Steps: ${deviceSummary.passedSteps}/${deviceSummary.totalSteps} passed, ${deviceSummary.failedSteps} failed"
            deviceSummary.primaryFailure?.let {
                lines += "- Primary failure: `${it.code}` — ${it.message}"
            }
        }
        lines += ""
        lines += "## Changed-line Coverage"
        lines += ""
        if (coverage == null) {
            lines += "Not requested."
        } else {
            lines += "- Status: `${coverage.status}`"
            lines += "- Base ref: `${coverage.baseRef}`"
            if (!coverage.failureMessage.isNullOrBlank()) {
                lines += "- Coverage failure: ${coverage.failureMessage}"
            } else {
                lines += "- Changed files: ${coverage.changedFiles}"
                lines += "- Matched source files: ${coverage.matchedSourceFiles}"
                lines += "- Changed executable lines: ${coverage.changedExecutableLines}"
                lines += "- Covered changed lines: ${coverage.coveredChangedLines}"
                lines += "- Changed-line coverage: ${coverage.coveredChangedLines}/${coverage.changedExecutableLines} = ${coverage.coveragePercent}%"
                if (coverage.uncoveredLines.isNotEmpty()) {
                    lines += "- Top uncovered changed lines:"
                    coverage.uncoveredLines.take(10).forEach {
                        lines += "  - `${it.file}:${it.line}`"
                    }
                }
            }
            val coverageArtifacts = coverage.artifacts.filter { it.type == ArtifactType.COVERAGE }
            if (coverageArtifacts.isNotEmpty()) {
                lines += "- Coverage artifacts:"
                coverageArtifacts.forEach { artifact ->
                    lines += "  - `${artifact.label ?: artifact.path}` → `${artifact.path}`"
                }
            }
        }
        return lines.joinToString("\n")
    }
}

fun AndroidTestRunResult.asArtifact(label: String, path: String): ExecutionArtifact {
    return ExecutionArtifact(
        type = ArtifactType.FILE,
        path = path,
        label = label,
        metadata = buildMap {
            put("task", executedTask)
            if (tests.isNotEmpty()) put("tests", tests.joinToString(","))
        }
    )
}
