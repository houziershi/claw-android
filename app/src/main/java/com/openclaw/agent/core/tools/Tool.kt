package com.openclaw.agent.core.tools

import kotlinx.serialization.json.JsonObject

/**
 * Interface for all tools that the Agent can invoke via function calling.
 */
interface Tool {
    /** Unique tool name (used in API tool_use calls) */
    val name: String

    /** Human-readable description for the LLM */
    val description: String

    /** JSON Schema for the tool's input parameters */
    val parameterSchema: JsonObject

    /** Execute the tool with the given arguments */
    suspend fun execute(args: JsonObject): ToolResult
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val success: Boolean,
    val content: String,
    val errorMessage: String? = null
)
