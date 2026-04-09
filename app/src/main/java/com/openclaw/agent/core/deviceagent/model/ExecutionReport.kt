package com.openclaw.agent.core.deviceagent.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionStatus {
    PASSED,
    FAILED,
    RUNNING,
    SKIPPED
}

@Serializable
data class ExecutionFailure(
    val code: String,
    val message: String,
    val recoverable: Boolean = false
)

@Serializable
data class ExecutionStepResult(
    val stepId: String,
    val status: ExecutionStatus,
    val matchedLocator: UiLocator? = null,
    val failure: ExecutionFailure? = null,
    val artifacts: List<ExecutionArtifact> = emptyList(),
    val durationMs: Long? = null
)

@Serializable
data class ExecutionReport(
    val planId: String,
    val status: ExecutionStatus,
    val steps: List<ExecutionStepResult>,
    val artifacts: List<ExecutionArtifact> = emptyList()
) {
    val totalSteps: Int get() = steps.size
    val passedSteps: Int get() = steps.count { it.status == ExecutionStatus.PASSED }
    val failedSteps: Int get() = steps.count { it.status == ExecutionStatus.FAILED }
    val isSuccess: Boolean get() = status == ExecutionStatus.PASSED && failedSteps == 0
    val primaryFailure: ExecutionFailure? get() = steps.firstNotNullOfOrNull { it.failure }
}
