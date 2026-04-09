package com.openclaw.agent.core.tools.impl

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.report.ArtifactStore
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import com.openclaw.agent.core.deviceagent.snapshot.UiNodeSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DeviceCollectArtifactsToolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `collect tool gathers snapshot logcat screenshot and file artifacts`() = runTest {
        val artifactRoot = Files.createTempDirectory("artifact-root")
        val screenshot = Files.createTempFile("screen", ".png").also { Files.write(it, "png".toByteArray()) }
        val coverage = Files.createTempFile("coverage", ".ec").also { Files.write(it, "cov".toByteArray()) }
        val tool = DeviceCollectArtifactsTool(
            collector = DefaultDeviceArtifactCollector(
                artifactStore = ArtifactStore(artifactRoot),
                snapshotSource = object : DeviceSnapshotSource {
                    override suspend fun currentSnapshot(): DeviceSnapshot = DeviceSnapshot(
                        packageName = "com.openclaw.agent",
                        activityName = "MainActivity",
                        nodeCount = 1,
                        root = UiNodeSnapshot(path = "0", text = "Settings"),
                        searchIndex = "0 | Settings"
                    )
                },
                logcatSource = object : LogcatSource {
                    override suspend fun currentLogcat(): String = "E/Test: boom"
                },
                screenshotSource = object : ScreenshotArtifactSource {
                    override suspend fun capture(): CapturedArtifact = CapturedArtifact(
                        path = screenshot.toString(),
                        label = "screenshot-live",
                        metadata = mapOf("source" to "fake")
                    )
                }
            )
        )

        val result = tool.execute(buildJsonObject {
            put("run_id", "run-1")
            put("step_id", "step-1")
            put("collect", buildJsonArray {
                add(JsonPrimitive("snapshot"))
                add(JsonPrimitive("logcat"))
                add(JsonPrimitive("screenshot"))
                add(JsonPrimitive("coverage"))
            })
            put("file_requests", buildJsonArray {
                add(json.encodeToJsonElement(FileArtifactRequest.serializer(), FileArtifactRequest(
                    path = coverage.toString(),
                    label = "coverage",
                    type = ArtifactType.COVERAGE
                )))
            })
        })

        val payload = json.decodeFromString(DeviceCollectArtifactsPayload.serializer(), result.content)

        assertThat(result.success).isTrue()
        assertThat(payload.artifactCount).isEqualTo(4)
        assertThat(payload.artifacts.map { it.type }).containsAtLeast(
            ArtifactType.UI_TREE,
            ArtifactType.LOGCAT,
            ArtifactType.SCREENSHOT,
            ArtifactType.COVERAGE
        )
        assertThat(payload.artifacts.first { it.type == ArtifactType.SCREENSHOT }.metadata["source"]).isEqualTo("fake")
    }

    @Test
    fun `collect tool handles explicit file collection`() = runTest {
        val source = Files.createTempFile("plain", ".txt").also { Files.write(it, "hello".toByteArray()) }
        val collector = DefaultDeviceArtifactCollector(
            artifactStore = ArtifactStore(Files.createTempDirectory("artifact-root"))
        )
        val tool = DeviceCollectArtifactsTool(collector)

        val result = tool.execute(buildJsonObject {
            put("run_id", "run-2")
            put("collect", buildJsonArray { add(JsonPrimitive("file")) })
            put("file_requests", buildJsonArray {
                add(json.encodeToJsonElement(FileArtifactRequest.serializer(), FileArtifactRequest(
                    path = source.toString(),
                    label = "plain-file"
                )))
            })
        })
        val payload = json.decodeFromString(DeviceCollectArtifactsPayload.serializer(), result.content)

        assertThat(payload.artifactCount).isEqualTo(1)
        assertThat(payload.artifacts.single().type).isEqualTo(ArtifactType.FILE)
        assertThat(Files.exists(Path.of(payload.artifacts.single().path))).isTrue()
    }

    @Test
    fun `missing run id returns error`() = runTest {
        val tool = DeviceCollectArtifactsTool(
            collector = object : DeviceArtifactCollector {
                override suspend fun collect(
                    runId: String,
                    stepId: String?,
                    requests: List<String>,
                    fileRequests: List<FileArtifactRequest>
                ): DeviceCollectArtifactsPayload = error("unused")
            }
        )

        val result = tool.execute(buildJsonObject {
            put("collect", buildJsonArray { add(JsonPrimitive("snapshot")) })
        })

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("run_id")
    }
}
