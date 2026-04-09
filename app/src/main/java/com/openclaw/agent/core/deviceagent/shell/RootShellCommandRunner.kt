package com.openclaw.agent.core.deviceagent.shell

open class RootShellCommandRunner(
    executor: ShellProcessExecutor = DefaultShellProcessExecutor(),
    private val rootBinary: String = "su"
) : ShellCommandRunner(executor = executor, shellBinary = rootBinary) {

    override suspend fun isAvailable(): Boolean {
        val result = run("id")
        return result.isSuccess && result.stdout.contains("uid=0")
    }
}
