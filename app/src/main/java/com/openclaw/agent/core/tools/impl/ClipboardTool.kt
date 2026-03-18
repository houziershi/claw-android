package com.openclaw.agent.core.tools.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ClipboardTool(private val context: Context) : Tool {
    override val name = "clipboard"
    override val description = "Read from or write to the device clipboard. Use action 'read' to get current clipboard content, or 'write' with text to copy text to clipboard."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("read"))
                    add(kotlinx.serialization.json.JsonPrimitive("write"))
                })
                put("description", "Action to perform: 'read' or 'write'")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to copy to clipboard (required for 'write' action)")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("action"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return when (action) {
            "read" -> {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    ToolResult(success = true, content = "Clipboard content: $text")
                } else {
                    ToolResult(success = true, content = "Clipboard is empty")
                }
            }
            "write" -> {
                val text = args["text"]?.jsonPrimitive?.content ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing 'text' parameter for write action"
                )
                clipboard.setPrimaryClip(ClipData.newPlainText("claw", text))
                ToolResult(success = true, content = "Text copied to clipboard: ${text.take(100)}")
            }
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use 'read' or 'write'.")
        }
    }
}
