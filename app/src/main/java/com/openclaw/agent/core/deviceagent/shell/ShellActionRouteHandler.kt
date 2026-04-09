package com.openclaw.agent.core.deviceagent.shell

import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.execution.ActionRouteHandler
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.model.UiLocator

class ShellActionRouteHandler(
    private val shellRunner: ShellCommandRunner,
    private val rootRunner: RootShellCommandRunner? = null
) : ActionRouteHandler {
    override val route: CapabilityRoute = CapabilityRoute.SHELL

    override suspend fun execute(step: AutomationStep): ExecutionStepResult {
        val spec = ShellCommandBuilder.build(step.action) ?: return ExecutionStepResult(
            stepId = step.id,
            status = ExecutionStatus.FAILED,
            failure = ExecutionFailure(
                code = "shell_action_unsupported",
                message = "Shell route cannot execute ${step.action::class.simpleName} without bounds or shell mapping",
                recoverable = true
            )
        )

        val runner = if (spec.requiresRoot) {
            rootRunner ?: return ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                failure = ExecutionFailure(
                    code = "root_runner_unavailable",
                    message = "Action requires root but no root runner is configured",
                    recoverable = false
                )
            )
        } else {
            shellRunner
        }

        val result = runner.run(spec.command, step.timeoutMs ?: ShellCommandRunner.DEFAULT_TIMEOUT_MS)
        val matchedLocator = spec.matchedBounds?.let { UiLocator(bounds = it) }
        val artifacts = listOf(
            ExecutionArtifact(
                type = ArtifactType.FILE,
                path = result.command.joinToString(" "),
                label = if (spec.requiresRoot) "shell:root-command" else "shell:command",
                metadata = buildMap {
                    put("exitCode", result.exitCode.toString())
                    if (result.stdout.isNotBlank()) put("stdout", result.stdout)
                    if (result.stderr.isNotBlank()) put("stderr", result.stderr)
                }
            )
        )

        return if (result.isSuccess) {
            ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.PASSED,
                matchedLocator = matchedLocator,
                artifacts = artifacts,
                durationMs = result.durationMs
            )
        } else {
            ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                matchedLocator = matchedLocator,
                failure = ExecutionFailure(
                    code = if (spec.requiresRoot) "root_command_failed" else "shell_command_failed",
                    message = result.stderr.ifBlank { result.stdout.ifBlank { "Command exited with ${result.exitCode}" } },
                    recoverable = !spec.requiresRoot,
                ),
                artifacts = artifacts,
                durationMs = result.durationMs
            )
        }
    }
}
