package com.openclaw.agent.core.tools

import android.util.Log
import com.openclaw.agent.core.llm.ToolDefinition
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ToolRegistry"

/**
 * Central registry for all available tools.
 * Manages tool registration and lookup.
 */
@Singleton
class ToolRegistry @Inject constructor() {

    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        Log.d(TAG, "Registered tool: ${tool.name}")
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getToolCount(): Int = tools.size

    /**
     * Convert all registered tools to LLM API ToolDefinition format.
     */
    fun toToolDefinitions(): List<ToolDefinition> = tools.values.map { tool ->
        ToolDefinition(
            name = tool.name,
            description = tool.description,
            inputSchema = tool.parameterSchema
        )
    }
}
