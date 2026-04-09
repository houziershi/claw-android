package com.openclaw.agent.core.deviceagent.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.TimeUnit

fun interface ShellProcessExecutor {
    fun execute(command: List<String>, timeoutMs: Long): CommandResult
}

open class ShellCommandRunner(
    private val executor: ShellProcessExecutor = DefaultShellProcessExecutor(),
    private val shellBinary: String = "/system/bin/sh"
) {
    open suspend fun isAvailable(): Boolean = runCatching {
        run("true").isSuccess
    }.getOrDefault(false)

    open suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult {
        val invocation = listOf(shellBinary, "-c", command)
        return withContext(Dispatchers.IO) {
            executor.execute(invocation, timeoutMs)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 10_000
    }
}

class DefaultShellProcessExecutor : ShellProcessExecutor {
    override fun execute(command: List<String>, timeoutMs: Long): CommandResult {
        val startedAt = System.nanoTime()
        val process = ProcessBuilder(command).start()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return CommandResult(
                command = command,
                exitCode = -1,
                stdout = process.inputStream.readFullySafely(),
                stderr = process.errorStream.readFullySafely(),
                durationMs = elapsedSince(startedAt)
            )
        }

        return CommandResult(
            command = command,
            exitCode = process.exitValue(),
            stdout = process.inputStream.readFullySafely(),
            stderr = process.errorStream.readFullySafely(),
            durationMs = elapsedSince(startedAt)
        )
    }

    private fun elapsedSince(startedAt: Long): Long {
        return (System.nanoTime() - startedAt) / 1_000_000
    }
}

private fun InputStream.readFullySafely(): String {
    return runCatching { bufferedReader().use { it.readText() } }.getOrDefault("")
}
