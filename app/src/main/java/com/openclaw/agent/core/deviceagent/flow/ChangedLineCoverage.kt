package com.openclaw.agent.core.deviceagent.flow

import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ChangedLineCoverageSpec(
    val baseRef: String = "origin/main",
    val xmlReportPath: String,
    val htmlReportPath: String? = null,
    val ecReportPath: String? = null,
    val sourceRoots: List<String> = emptyList(),
    val diffFilePath: String? = null,
    val scriptPath: String = "~/.hermes/skills/software-development/android-real-device-line-coverage/scripts/jacoco_changed_line_coverage.py"
)

@Serializable
data class ChangedLineRef(
    val file: String,
    val line: Int
)

@Serializable
data class ChangedLineCoverageResult(
    val status: ExecutionStatus,
    val baseRef: String,
    val changedFiles: Int = 0,
    val matchedSourceFiles: Int = 0,
    val changedExecutableLines: Int = 0,
    val coveredChangedLines: Int = 0,
    val coveragePercent: Double = 0.0,
    val uncoveredLines: List<ChangedLineRef> = emptyList(),
    val artifacts: List<ExecutionArtifact> = emptyList(),
    val output: String? = null,
    val failureMessage: String? = null
)

interface ChangedLineCoverageRunner {
    suspend fun run(spec: ChangedLineCoverageSpec): ChangedLineCoverageResult
}

class ScriptChangedLineCoverageRunner(
    private val commandRunner: CommandRunner = ProcessCommandRunner(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ChangedLineCoverageRunner {
    override suspend fun run(spec: ChangedLineCoverageSpec): ChangedLineCoverageResult {
        val command = buildCommand(spec)
        val completed = commandRunner.run(command)
        if (completed.exitCode != 0) {
            return ChangedLineCoverageResult(
                status = ExecutionStatus.FAILED,
                baseRef = spec.baseRef,
                artifacts = coverageArtifacts(spec),
                output = completed.stdout.ifBlank { null },
                failureMessage = completed.stderr.ifBlank { "changed-line coverage command failed with exit code ${completed.exitCode}" }
            )
        }

        return runCatching {
            val payload = json.decodeFromString(ChangedLineCoverageScriptPayload.serializer(), completed.stdout)
            ChangedLineCoverageResult(
                status = ExecutionStatus.PASSED,
                baseRef = payload.base,
                changedFiles = payload.changedFiles,
                matchedSourceFiles = payload.matchedSourceFiles,
                changedExecutableLines = payload.changedExecutableLines,
                coveredChangedLines = payload.coveredChangedLines,
                coveragePercent = payload.coveragePercent,
                uncoveredLines = payload.uncoveredLines.map { ChangedLineRef(it.file, it.line) },
                artifacts = coverageArtifacts(spec),
                output = completed.stdout
            )
        }.getOrElse { error ->
            ChangedLineCoverageResult(
                status = ExecutionStatus.FAILED,
                baseRef = spec.baseRef,
                artifacts = coverageArtifacts(spec),
                output = completed.stdout.ifBlank { null },
                failureMessage = error.message ?: "failed to parse changed-line coverage output"
            )
        }
    }

    private fun buildCommand(spec: ChangedLineCoverageSpec): List<String> {
        return buildList {
            add("python3")
            add(expandHome(spec.scriptPath))
            add("--base")
            add(spec.baseRef)
            add("--xml")
            add(spec.xmlReportPath)
            spec.sourceRoots.forEach {
                add("--source-root")
                add(it)
            }
            spec.diffFilePath?.let {
                add("--diff-file")
                add(it)
            }
        }
    }

    private fun coverageArtifacts(spec: ChangedLineCoverageSpec): List<ExecutionArtifact> {
        return buildList {
            add(
                ExecutionArtifact(
                    type = ArtifactType.COVERAGE,
                    path = spec.xmlReportPath,
                    label = "jacoco-xml",
                    metadata = mapOf("baseRef" to spec.baseRef, "format" to "xml")
                )
            )
            spec.htmlReportPath?.let {
                add(
                    ExecutionArtifact(
                        type = ArtifactType.COVERAGE,
                        path = it,
                        label = "jacoco-html",
                        metadata = mapOf("baseRef" to spec.baseRef, "format" to "html")
                    )
                )
            }
            spec.ecReportPath?.let {
                add(
                    ExecutionArtifact(
                        type = ArtifactType.COVERAGE,
                        path = it,
                        label = "jacoco-ec",
                        metadata = mapOf("baseRef" to spec.baseRef, "format" to "ec")
                    )
                )
            }
        }
    }

    private fun expandHome(path: String): String {
        if (!path.startsWith("~/")) return path
        return File(System.getProperty("user.home"), path.removePrefix("~/")).path
    }
}

interface CommandRunner {
    suspend fun run(command: List<String>): CommandExecutionResult
}

class ProcessCommandRunner : CommandRunner {
    override suspend fun run(command: List<String>): CommandExecutionResult {
        val process = ProcessBuilder(command).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return CommandExecutionResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr
        )
    }
}

@Serializable
private data class ChangedLineCoverageScriptPayload(
    val base: String,
    @SerialName("changed_files") val changedFiles: Int,
    @SerialName("matched_source_files") val matchedSourceFiles: Int = 0,
    @SerialName("changed_executable_lines") val changedExecutableLines: Int,
    @SerialName("covered_changed_lines") val coveredChangedLines: Int,
    @SerialName("coverage_percent") val coveragePercent: Double,
    @SerialName("uncovered_lines") val uncoveredLines: List<ChangedLineRefPayload> = emptyList()
)

@Serializable
private data class ChangedLineRefPayload(
    val file: String,
    val line: Int
)

data class CommandExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
