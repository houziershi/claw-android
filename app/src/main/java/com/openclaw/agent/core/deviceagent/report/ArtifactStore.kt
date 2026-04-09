package com.openclaw.agent.core.deviceagent.report

import com.openclaw.agent.core.deviceagent.model.ArtifactType
import com.openclaw.agent.core.deviceagent.model.ExecutionArtifact
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

open class ArtifactStore(
    private val rootDir: Path
) {
    init {
        Files.createDirectories(rootDir)
    }

    open fun saveText(
        runId: String,
        stepId: String?,
        type: ArtifactType,
        label: String,
        content: String,
        extension: String,
        metadata: Map<String, String> = emptyMap()
    ): ExecutionArtifact {
        val target = artifactPath(runId, stepId, label, extension)
        Files.createDirectories(target.parent)
        Files.write(target, content.toByteArray())
        return target.toArtifact(type, label, metadata)
    }

    open fun copyFile(
        runId: String,
        stepId: String?,
        type: ArtifactType,
        label: String,
        sourcePath: Path,
        metadata: Map<String, String> = emptyMap()
    ): ExecutionArtifact {
        val extension = sourcePath.fileName.toString().substringAfterLast('.', missingDelimiterValue = "bin")
        val target = artifactPath(runId, stepId, label, extension)
        Files.createDirectories(target.parent)
        Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
        return target.toArtifact(type, label, metadata + mapOf("sourcePath" to sourcePath.toString()))
    }

    protected fun artifactPath(runId: String, stepId: String?, label: String, extension: String): Path {
        val safeRunId = sanitize(runId)
        val safeStepId = stepId?.let(::sanitize) ?: "run"
        val safeLabel = sanitize(label)
        return rootDir.resolve(safeRunId).resolve(safeStepId).resolve("$safeLabel.$extension")
    }

    private fun Path.toArtifact(
        type: ArtifactType,
        label: String,
        metadata: Map<String, String>
    ) = ExecutionArtifact(
        type = type,
        path = toString(),
        label = label,
        metadata = metadata
    )

    private fun sanitize(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifEmpty { "artifact" }
    }
}
