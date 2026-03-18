package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class MemoryWriteTool(private val memoryStore: MemoryStore) : Tool {
    override val name = "memory_write"
    override val description = """Write or update a memory file. Use this to:
- Save important user preferences to USER.md
- Record key facts to MEMORY.md  
- Update your persona in SOUL.md
Use action 'write' to overwrite, 'append' to add content at the end.""".trimIndent()
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File path, e.g. 'MEMORY.md', 'USER.md'")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Content to write or append")
            }
            putJsonObject("action") {
                put("type", "string")
                put("enum", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("write"))
                    add(kotlinx.serialization.json.JsonPrimitive("append"))
                })
                put("description", "Action: 'write' to overwrite file, 'append' to add to end. Default: 'append'")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("path"))
            add(kotlinx.serialization.json.JsonPrimitive("content"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val path = args["path"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'path' parameter"
        )
        val content = args["content"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'content' parameter"
        )
        val action = args["action"]?.jsonPrimitive?.content ?: "append"

        // Security: only allow writing to known safe paths
        val allowedPrefixes = listOf("MEMORY.md", "USER.md", "SOUL.md", "daily/", "notes/")
        if (allowedPrefixes.none { path.startsWith(it) }) {
            return ToolResult(
                success = false, content = "",
                errorMessage = "Cannot write to '$path'. Allowed paths: MEMORY.md, USER.md, SOUL.md, daily/*, notes/*"
            )
        }

        return try {
            when (action) {
                "write" -> {
                    memoryStore.write(path, content)
                    ToolResult(success = true, content = "File '$path' written successfully (${content.length} chars)")
                }
                "append" -> {
                    memoryStore.append(path, content)
                    ToolResult(success = true, content = "Content appended to '$path' (${content.length} chars)")
                }
                else -> ToolResult(
                    success = false, content = "",
                    errorMessage = "Unknown action: $action. Use 'write' or 'append'."
                )
            }
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "Failed to write: ${e.message}")
        }
    }
}
