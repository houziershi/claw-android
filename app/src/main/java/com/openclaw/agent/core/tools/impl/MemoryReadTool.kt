package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class MemoryReadTool(private val memoryStore: MemoryStore) : Tool {
    override val name = "memory_read"
    override val description = "Read a memory file. Available files: SOUL.md (your persona), USER.md (user profile), MEMORY.md (long-term facts). You can also read daily notes like 'daily/2026-03-18.md'."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File path relative to memory root, e.g. 'MEMORY.md', 'USER.md', 'daily/2026-03-18.md'")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("path"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val path = args["path"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'path' parameter"
        )
        val content = memoryStore.read(path)
        return if (content != null) {
            ToolResult(success = true, content = content)
        } else {
            ToolResult(success = true, content = "(File '$path' does not exist yet)")
        }
    }
}
