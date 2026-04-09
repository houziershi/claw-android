package com.openclaw.agent.core.deviceagent.model

import kotlinx.serialization.Serializable

@Serializable
enum class ArtifactType {
    SCREENSHOT,
    UI_TREE,
    LOGCAT,
    COVERAGE,
    VIDEO,
    FILE
}

@Serializable
data class ExecutionArtifact(
    val type: ArtifactType,
    val path: String,
    val label: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
