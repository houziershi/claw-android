package com.openclaw.agent.core.tools.impl

import android.content.Context
import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import com.openclaw.agent.core.deviceagent.report.ArtifactStore
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Path

class DeviceCollectArtifactsTool(
    private val collector: DeviceArtifactCollector,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) : Tool {
    override val name: String = "device_collect_artifacts"
    override val description: String = "Collect device artifacts such as UI snapshots, logcat, screenshots, coverage files, and arbitrary files into the artifact store."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        putJsonObject("properties") {
            putJsonObject("run_id") {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Run identifier used for artifact storage"))
            }
            putJsonObject("step_id") {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Optional step identifier for grouping artifacts"))
            }
            putJsonObject("collect") {
                put("type", JsonPrimitive("array"))
                putJsonObject("items") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("snapshot"))
                        add(JsonPrimitive("logcat"))
                        add(JsonPrimitive("screenshot"))
                        add(JsonPrimitive("coverage"))
                        add(JsonPrimitive("file"))
                    })
                }
            }
            putJsonObject("file_requests") {
                put("type", JsonPrimitive("array"))
                putJsonObject("items") {
                    put("type", JsonPrimitive("object"))
                }
                put("description", JsonPrimitive("Optional file copy requests when collect includes 'file' or 'coverage'"))
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("run_id"))
            add(JsonPrimitive("collect"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val runId = args["run_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult(false, "", "Missing 'run_id'")
        val stepId = args["step_id"]?.jsonPrimitive?.contentOrNull
        val collect = args["collect"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: return ToolResult(false, "", "Missing 'collect'")
        val fileRequests = args["file_requests"]?.jsonArray?.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(FileArtifactRequest.serializer(), element) }.getOrNull()
        }.orEmpty()

        val payload = collector.collect(
            runId = runId,
            stepId = stepId,
            requests = collect,
            fileRequests = fileRequests
        )
        return ToolResult(success = true, content = json.encodeToString(DeviceCollectArtifactsPayload.serializer(), payload))
    }
}

interface DeviceArtifactCollector {
    suspend fun collect(
        runId: String,
        stepId: String?,
        requests: List<String>,
        fileRequests: List<FileArtifactRequest> = emptyList()
    ): DeviceCollectArtifactsPayload
}

class DefaultDeviceArtifactCollector(
    private val artifactStore: ArtifactStore,
    private val snapshotSource: DeviceSnapshotSource? = null,
    private val logcatSource: LogcatSource? = null,
    private val screenshotSource: ScreenshotArtifactSource? = null,
    private val fileCollector: FileArtifactCollector = FileArtifactCollector(artifactStore)
) : DeviceArtifactCollector {
    override suspend fun collect(
        runId: String,
        stepId: String?,
        requests: List<String>,
        fileRequests: List<FileArtifactRequest>
    ): DeviceCollectArtifactsPayload {
        val artifacts = mutableListOf<ExecutionArtifact>()
        val normalized = requests.map { it.lowercase() }

        if ("snapshot" in normalized) {
            snapshotSource?.currentSnapshot()?.let {
                artifacts += fileCollector.saveSnapshot(runId, stepId, it)
            }
        }
        if ("logcat" in normalized) {
            logcatSource?.currentLogcat()?.takeIf { it.isNotBlank() }?.let {
                artifacts += artifactStore.saveText(runId, stepId, ArtifactType.LOGCAT, "logcat", it, "log")
            }
        }
        if ("screenshot" in normalized) {
            screenshotSource?.capture()?.let { capture ->
                artifacts += artifactStore.copyFile(
                    runId = runId,
                    stepId = stepId,
                    type = ArtifactType.SCREENSHOT,
                    label = capture.label,
                    sourcePath = Path.of(capture.path),
                    metadata = capture.metadata
                )
            }
        }

        val shouldCollectCoverage = "coverage" in normalized
        val shouldCollectFile = "file" in normalized
        if (shouldCollectCoverage || shouldCollectFile) {
            fileRequests.forEach { request ->
                val type = when {
                    request.type != null -> request.type
                    shouldCollectCoverage -> ArtifactType.COVERAGE
                    else -> ArtifactType.FILE
                }
                artifacts += artifactStore.copyFile(
                    runId = runId,
                    stepId = stepId,
                    type = type,
                    label = request.label,
                    sourcePath = Path.of(request.path),
                    metadata = request.metadata
                )
            }
        }

        return DeviceCollectArtifactsPayload(
            runId = runId,
            stepId = stepId,
            artifactCount = artifacts.size,
            artifacts = artifacts
        )
    }
}

class FileArtifactCollector(
    private val artifactStore: ArtifactStore,
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true }
) {
    fun saveSnapshot(runId: String, stepId: String?, snapshot: com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot): ExecutionArtifact {
        return artifactStore.saveText(
            runId = runId,
            stepId = stepId,
            type = ArtifactType.UI_TREE,
            label = "ui-tree",
            content = json.encodeToString(com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot.serializer(), snapshot),
            extension = "json",
            metadata = mapOf("packageName" to snapshot.packageName, "nodeCount" to snapshot.nodeCount.toString())
        )
    }
}

interface LogcatSource {
    suspend fun currentLogcat(): String
}

interface ScreenshotArtifactSource {
    suspend fun capture(): CapturedArtifact?
}

@Serializable
data class CapturedArtifact(
    val path: String,
    val label: String = "screenshot",
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class FileArtifactRequest(
    val path: String,
    val label: String,
    val type: ArtifactType? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class DeviceCollectArtifactsPayload(
    val runId: String,
    val stepId: String? = null,
    val artifactCount: Int,
    val artifacts: List<ExecutionArtifact>
)

class AndroidLogcatSource(
    private val context: Context
) : LogcatSource {
    override suspend fun currentLogcat(): String {
        val process = ProcessBuilder("logcat", "-d", "-t", "200").start()
        return process.inputStream.bufferedReader().use { it.readText() }
    }
}
