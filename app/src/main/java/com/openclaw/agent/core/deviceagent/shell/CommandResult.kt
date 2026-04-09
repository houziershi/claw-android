package com.openclaw.agent.core.deviceagent.shell

data class CommandResult(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0
}
