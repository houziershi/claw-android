package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonPrimitive

class MemoryListTool(private val memoryStore: MemoryStore) : Tool {
    override val name = "memory_list"
    override val description = "List all memory files. Optionally specify a directory like 'daily' to list only daily notes."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("directory") {
                put("type", "string")
                put("description", "Directory to list, e.g. 'daily'. Leave empty for all files.")
            }
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val directory = args["directory"]?.jsonPrimitive?.content ?: ""
        val files = memoryStore.list(directory)
        return if (files.isEmpty()) {
            ToolResult(success = true, content = "No memory files found" + if (directory.isNotEmpty()) " in '$directory'" else "")
        } else {
            val formatted = files.joinToString("\n") { "- $it" }
            ToolResult(success = true, content = "Memory files (${files.size}):\n$formatted")
        }
    }
}
