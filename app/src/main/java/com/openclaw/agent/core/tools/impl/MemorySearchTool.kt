package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class MemorySearchTool(private val memoryStore: MemoryStore) : Tool {
    override val name = "memory_search"
    override val description = "Search across all memory files (MEMORY.md, USER.md, daily notes) for relevant information. Use this when you need to recall something about the user or past conversations."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search keywords")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("query"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args["query"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'query' parameter"
        )

        val results = memoryStore.search(query, maxResults = 10)
        return if (results.isEmpty()) {
            ToolResult(success = true, content = "No memory found matching: $query")
        } else {
            val formatted = results.joinToString("\n\n") { snippet ->
                "**[${snippet.path}]** (relevance: ${"%.0f".format(snippet.relevance * 100)}%)\n${snippet.content}"
            }
            ToolResult(success = true, content = "Found ${results.size} memory matches:\n\n$formatted")
        }
    }
}
