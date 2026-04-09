package com.openclaw.agent.core.deviceagent.shell

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.UiBounds
import com.openclaw.agent.core.deviceagent.model.UiLocator
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShellCommandRunnerTest {

    @Test
    fun shellCommandRunner_wrapsCommandsWithShDashC() = runTest {
        val executor = RecordingExecutor()
        val runner = ShellCommandRunner(executor = executor, shellBinary = "/bin/sh")

        val result = runner.run("input keyevent 4")

        assertThat(executor.commands.single()).containsExactly("/bin/sh", "-c", "input keyevent 4").inOrder()
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun rootShellCommandRunner_checksUidZeroAvailability() = runTest {
        val executor = RecordingExecutor(stdout = "uid=0(root) gid=0(root)")
        val runner = RootShellCommandRunner(executor = executor, rootBinary = "su")

        val available = runner.isAvailable()

        assertThat(available).isTrue()
        assertThat(executor.commands.single()).containsExactly("su", "-c", "id").inOrder()
    }

    @Test
    fun shellCommandBuilder_buildsTapFromLocatorBounds() {
        val spec = ShellCommandBuilder.build(
            DeviceAction.Tap(
                UiLocator(bounds = UiBounds(left = 10, top = 20, right = 30, bottom = 60))
            )
        )

        assertThat(spec?.command).isEqualTo("input tap 20 40")
        assertThat(spec?.matchedBounds).isEqualTo(UiBounds(10, 20, 30, 60))
    }

    @Test
    fun shellCommandBuilder_escapesTextInputAndFocusesBounds() {
        val spec = ShellCommandBuilder.build(
            DeviceAction.InputText(
                locator = UiLocator(bounds = UiBounds(0, 0, 100, 40)),
                text = "hello world 100%"
            )
        )

        assertThat(spec?.command).isEqualTo("input tap 50 20 && input text 'hello%sworld%s100%25'")
    }

    @Test
    fun shellCommandBuilder_mapsSystemCommands() {
        val launch = ShellCommandBuilder.build(DeviceAction.LaunchApp(packageName = "com.openclaw.agent"))
        val key = ShellCommandBuilder.build(DeviceAction.PressKey(keyCode = 4))
        val clear = ShellCommandBuilder.build(DeviceAction.ClearAppData(packageName = "com.openclaw.agent"))
        val pull = ShellCommandBuilder.build(
            DeviceAction.PullFile(
                sourcePath = "/data/local/tmp/a.txt",
                destinationPath = "/sdcard/Download/a.txt",
                requiresRoot = true
            )
        )

        assertThat(launch?.command).isEqualTo("monkey -p 'com.openclaw.agent' -c android.intent.category.LAUNCHER 1")
        assertThat(key?.command).isEqualTo("input keyevent 4")
        assertThat(clear?.command).isEqualTo("pm clear com.openclaw.agent")
        assertThat(pull?.command).isEqualTo("cp '/data/local/tmp/a.txt' '/sdcard/Download/a.txt'")
        assertThat(pull?.requiresRoot).isTrue()
    }

    private class RecordingExecutor(
        private val exitCode: Int = 0,
        private val stdout: String = "",
        private val stderr: String = ""
    ) : ShellProcessExecutor {
        val commands = mutableListOf<List<String>>()

        override fun execute(command: List<String>, timeoutMs: Long): CommandResult {
            commands += command
            return CommandResult(
                command = command,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                durationMs = 5
            )
        }
    }
}
