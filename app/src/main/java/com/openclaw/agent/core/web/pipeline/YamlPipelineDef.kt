package com.openclaw.agent.core.web.pipeline

data class YamlPipelineDef(
    val site: String,
    val name: String,
    val description: String,
    val strategy: String = "public",
    val browser: Boolean = false,
    val args: Map<String, ArgDef> = emptyMap(),
    val pipeline: List<PipelineStep> = emptyList(),
    val columns: List<String> = emptyList()
)

data class ArgDef(
    val type: String = "string",
    val default: Any? = null,
    val required: Boolean = false,
    val description: String = ""
)

sealed class PipelineStep {
    data class Fetch(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val params: Map<String, String> = emptyMap()
    ) : PipelineStep()

    data class MapStep(val fields: Map<String, String>) : PipelineStep()
    data class Limit(val expr: String) : PipelineStep()
    data class Filter(val expr: String) : PipelineStep()
    data class Select(val field: String) : PipelineStep()
}
