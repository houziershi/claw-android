package com.openclaw.agent.core.tools

import android.util.Log
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ToolRouter"

/**
 * Routes tool calls from the LLM to the appropriate Tool implementation.
 */
@Singleton
class ToolRouter @Inject constructor(
    private val registry: ToolRegistry
) {
    /**
     * Execute a tool by name with the given arguments.
     * Returns a ToolResult (always succeeds at the routing level;
     * check ToolResult.success for tool-level errors).
     */
    suspend fun execute(toolName: String, args: JsonObject): ToolResult {
        val tool = registry.getTool(toolName)
        if (tool == null) {
            Log.e(TAG, "Unknown tool: $toolName")
            return ToolResult(
                success = false,
                content = "",
                errorMessage = "Unknown tool: $toolName. Available tools: ${registry.getAllTools().joinToString { it.name }}"
            )
        }

        Log.d(TAG, "Executing tool: $toolName with args: $args")
        return try {
            val result = tool.execute(args)
            Log.d(TAG, "Tool $toolName completed: success=${result.success}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Tool $toolName threw exception", e)
            ToolResult(
                success = false,
                content = "",
                errorMessage = "Tool execution failed: ${e.message}"
            )
        }
    }
}
