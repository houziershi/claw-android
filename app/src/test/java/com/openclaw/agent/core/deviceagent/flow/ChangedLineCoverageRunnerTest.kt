package com.openclaw.agent.core.deviceagent.flow

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import org.junit.Test

class ChangedLineCoverageRunnerTest {

    @Test
    fun run_parsesCoverageJsonAndBuildsArtifacts() = kotlinx.coroutines.test.runTest {
        val runner = ScriptChangedLineCoverageRunner(
            commandRunner = FakeCommandRunner(
                CommandExecutionResult(
                    exitCode = 0,
                    stdout = """
                        {
                          "base": "origin/main",
                          "changed_files": 4,
                          "matched_source_files": 3,
                          "changed_executable_lines": 83,
                          "covered_changed_lines": 61,
                          "coverage_percent": 73.49,
                          "uncovered_lines": [
                            {"file": "app/src/main/java/com/openclaw/agent/Foo.kt", "line": 42},
                            {"file": "app/src/main/java/com/openclaw/agent/Bar.kt", "line": 57}
                          ]
                        }
                    """.trimIndent(),
                    stderr = ""
                )
            )
        )

        val result = runner.run(
            ChangedLineCoverageSpec(
                xmlReportPath = "app/build/reports/coverage/debug/report.xml",
                htmlReportPath = "app/build/reports/coverage/debug/index.html",
                ecReportPath = "app/build/outputs/code_coverage/debug.ec",
                sourceRoots = listOf("app/src/main/java", "app/src/main/kotlin")
            )
        )

        assertThat(result.status).isEqualTo(ExecutionStatus.PASSED)
        assertThat(result.baseRef).isEqualTo("origin/main")
        assertThat(result.changedFiles).isEqualTo(4)
        assertThat(result.coveragePercent).isEqualTo(73.49)
        assertThat(result.uncoveredLines).containsExactly(
            ChangedLineRef("app/src/main/java/com/openclaw/agent/Foo.kt", 42),
            ChangedLineRef("app/src/main/java/com/openclaw/agent/Bar.kt", 57)
        )
        assertThat(result.artifacts.mapNotNull { it.label }).containsExactly("jacoco-xml", "jacoco-html", "jacoco-ec")
    }

    @Test
    fun run_returnsFailedResultWhenCommandFails() = kotlinx.coroutines.test.runTest {
        val runner = ScriptChangedLineCoverageRunner(
            commandRunner = FakeCommandRunner(
                CommandExecutionResult(
                    exitCode = 2,
                    stdout = "",
                    stderr = "git diff failed"
                )
            )
        )

        val result = runner.run(
            ChangedLineCoverageSpec(
                baseRef = "origin/main",
                xmlReportPath = "app/build/reports/coverage/debug/report.xml"
            )
        )

        assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
        assertThat(result.failureMessage).contains("git diff failed")
        assertThat(result.artifacts).hasSize(1)
        assertThat(result.artifacts.single().label).isEqualTo("jacoco-xml")
    }
}

private class FakeCommandRunner(
    private val result: CommandExecutionResult
) : CommandRunner {
    override suspend fun run(command: List<String>): CommandExecutionResult = result
}
