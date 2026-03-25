package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class BrightnessTool(private val context: Context) : Tool {

    companion object {
        private const val TAG = "BrightnessTool"
    }

    override val name = "brightness"
    override val description = "Get or set screen brightness. Use action 'get' to read current brightness, 'set' to change brightness level, or 'auto' to toggle auto-brightness."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("get"))
                    add(JsonPrimitive("set"))
                    add(JsonPrimitive("auto"))
                })
                put("description", "Action: 'get' to read brightness, 'set' to change brightness, 'auto' to toggle auto-brightness")
            }
            putJsonObject("level") {
                put("type", "integer")
                put("description", "Brightness level to set (0-255, for 'set' action)")
            }
            putJsonObject("percent") {
                put("type", "integer")
                put("description", "Brightness percentage to set (0-100, for 'set' action, alternative to level)")
            }
            putJsonObject("enabled") {
                put("type", "boolean")
                put("description", "Enable or disable auto-brightness (for 'auto' action)")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("action"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "get" -> handleGet()
            "set" -> handleSet(args)
            "auto" -> handleAuto(args)
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use 'get', 'set', or 'auto'."
            )
        }
    }

    private fun handleGet(): ToolResult {
        return try {
            val brightness = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS
            )
            val percent = brightness * 100 / 255
            val autoMode = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE
            )
            val isAuto = autoMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

            val info = buildString {
                appendLine("## Screen Brightness")
                appendLine("- 🔆 Brightness: $brightness/255 ($percent%)")
                appendLine("- 💡 Auto-brightness: ${if (isAuto) "ON" else "OFF"}")
            }
            ToolResult(success = true, content = info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get brightness", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get brightness: ${e.message}")
        }
    }

    private fun handleSet(args: JsonObject): ToolResult {
        if (!Settings.System.canWrite(context)) {
            return requestWriteSettingsPermission()
        }

        val levelArg = args["level"]?.jsonPrimitive?.content?.toIntOrNull()
        val percentArg = args["percent"]?.jsonPrimitive?.content?.toIntOrNull()

        val targetLevel = when {
            levelArg != null -> levelArg
            percentArg != null -> percentArg * 255 / 100
            else -> return ToolResult(
                success = false, content = "", errorMessage = "Missing 'level' or 'percent' parameter for set action"
            )
        }

        val safeLevel = targetLevel.coerceIn(0, 255)

        return try {
            // Disable auto-brightness when manually setting brightness
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                safeLevel
            )
            val percent = safeLevel * 100 / 255
            ToolResult(success = true, content = "Set screen brightness to $safeLevel/255 ($percent%)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to set brightness: ${e.message}")
        }
    }

    private fun handleAuto(args: JsonObject): ToolResult {
        if (!Settings.System.canWrite(context)) {
            return requestWriteSettingsPermission()
        }

        val enabled = args["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'enabled' parameter for auto action"
            )

        return try {
            val mode = if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode
            )
            ToolResult(success = true, content = "Auto-brightness ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set auto-brightness", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to set auto-brightness: ${e.message}")
        }
    }

    private fun requestWriteSettingsPermission(): ToolResult {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open write settings page", e)
        }
        return ToolResult(
            success = false,
            content = "",
            errorMessage = "WRITE_SETTINGS permission not granted. Opening settings page for the user to grant permission. Please try again after granting."
        )
    }
}
