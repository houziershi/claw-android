package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class AlarmTool(private val context: Context) : Tool {
    override val name = "set_alarm"
    override val description = "Set an alarm or timer on the device. Specify hour (0-23), minute (0-59), and an optional message."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Hour in 24-hour format (0-23)")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Minute (0-59)")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Label for the alarm")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("hour"))
            add(kotlinx.serialization.json.JsonPrimitive("minute"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val hour = args["hour"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'hour' parameter"
            )
            val minute = args["minute"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'minute' parameter"
            )
            val message = args["message"]?.jsonPrimitive?.content ?: "Claw Alarm"

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult(success = true, content = "Alarm set for %02d:%02d — $message".format(hour, minute))
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "Failed to set alarm: ${e.message}")
        }
    }
}
