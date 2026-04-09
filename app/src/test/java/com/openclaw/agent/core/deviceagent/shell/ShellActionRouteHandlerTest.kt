package com.openclaw.agent.core.deviceagent.shell

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.model.UiBounds
import com.openclaw.agent.core.deviceagent.model.UiLocator
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShellActionRouteHandlerTest {

    @Test
    fun execute_runsShellCommandForBoundsTap() = runTest {
        val runner = FakeShellRunner()
        val handler = ShellActionRouteHandler(shellRunner = runner)
        val step = AutomationStep(
            id = "tap-bounds",
            action = DeviceAction.Tap(UiLocator(bounds = UiBounds(0, 0, 100, 40)))
        )

        val result = handler.execute(step)

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(runner.commands).containsExactly("input tap 50 20")
        assertThat(result.matchedLocator?.bounds).isEqualTo(UiBounds(0, 0, 100, 40))
        assertThat(result.artifacts.single().metadata["exitCode"]).isEqualTo("0")
    }

    @Test
    fun execute_returnsRecoverableFailureWhenShellCannotMapAction() = runTest {
        val handler = ShellActionRouteHandler(shellRunner = FakeShellRunner())
        val step = AutomationStep(
            id = "tap-semantic",
            action = DeviceAction.Tap(UiLocator(text = "Settings"))
        )

        val result = handler.execute(step)

        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.failure?.code).isEqualTo("shell_action_unsupported")
        assertThat(result.failure?.recoverable).isTrue()
    }

    @Test
    fun execute_usesRootRunnerWhenActionRequiresRoot() = runTest {
        val shellRunner = FakeShellRunner()
        val rootRunner = FakeRootRunner()
        val handler = ShellActionRouteHandler(shellRunner = shellRunner, rootRunner = rootRunner)
        val step = AutomationStep(
            id = "pull-private",
            action = DeviceAction.PullFile(
                sourcePath = "/data/data/com.openclaw.agent/files/coverage.ec",
                destinationPath = "/sdcard/Download/coverage.ec",
                requiresRoot = true
            )
        )

        val result = handler.execute(step)

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(shellRunner.commands).isEmpty()
        assertThat(rootRunner.commands).containsExactly("cp '/data/data/com.openclaw.agent/files/coverage.ec' '/sdcard/Download/coverage.ec'")
        assertThat(result.artifacts.single().label).isEqualTo("shell:root-command")
    }

    @Test
    fun execute_returnsNonRecoverableFailureWhenRootRunnerMissing() = runTest {
        val handler = ShellActionRouteHandler(shellRunner = FakeShellRunner())
        val step = AutomationStep(
            id = "pull-private",
            action = DeviceAction.PullFile(
                sourcePath = "/data/data/com.openclaw.agent/files/coverage.ec",
                destinationPath = "/sdcard/Download/coverage.ec",
                requiresRoot = true
            )
        )

        val result = handler.execute(step)

        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.failure?.code).isEqualTo("root_runner_unavailable")
        assertThat(result.failure?.recoverable).isFalse()
    }

    private open class FakeShellRunner(
        private val nextResult: CommandResult = CommandResult(
            command = listOf("sh", "-c", "true"),
            exitCode = 0,
            stdout = "ok",
            stderr = "",
            durationMs = 9
        )
    ) : ShellCommandRunner(executor = ShellProcessExecutor { _, _ -> error("unused") }) {
        val commands = mutableListOf<String>()

        override suspend fun isAvailable(): Boolean = true

        override suspend fun run(command: String, timeoutMs: Long): CommandResult {
            commands += command
            return nextResult.copy(command = listOf("sh", "-c", command))
        }
    }

    private class FakeRootRunner : RootShellCommandRunner(executor = ShellProcessExecutor { _, _ -> error("unused") }) {
        val commands = mutableListOf<String>()

        override suspend fun isAvailable(): Boolean = true

        override suspend fun run(command: String, timeoutMs: Long): CommandResult {
            commands += command
            return CommandResult(
                command = listOf("su", "-c", command),
                exitCode = 0,
                stdout = "uid=0(root)",
                stderr = "",
                durationMs = 7
            )
        }
    }
}
