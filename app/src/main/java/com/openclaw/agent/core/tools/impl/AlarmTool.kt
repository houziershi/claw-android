package com.openclaw.agent.core.tools.impl

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Calendar

private const val TAG = "AlarmTool"

class AlarmTool(private val context: Context) : Tool {
    override val name = "set_alarm"
    override val description = "Set an alarm on the device. Specify hour (0-23), minute (0-59), and an optional message/label."
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

            // Try AlarmClock intent first (shows in system alarm app)
            try {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                // Check if there's an app that can handle this intent
                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                if (resolveInfo != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "Alarm set via AlarmClock intent: $hour:$minute — $message")
                    return ToolResult(
                        success = true,
                        content = "Alarm set for %02d:%02d — %s".format(hour, minute, message)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "AlarmClock intent failed, falling back to AlarmManager: ${e.message}")
            }

            // Fallback: use AlarmManager directly
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If the time has already passed today, set for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                hour * 100 + minute, // Simple unique request code
                Intent("com.openclaw.agent.ALARM").apply {
                    putExtra("message", message)
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Check exact alarm permission on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    // Use inexact alarm as fallback
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    Log.d(TAG, "Alarm set (inexact) via AlarmManager: $hour:$minute")
                    return ToolResult(
                        success = true,
                        content = "Alarm set for %02d:%02d — %s (approximate, exact alarm permission not granted)".format(hour, minute, message)
                    )
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d(TAG, "Alarm set (exact) via AlarmManager: $hour:$minute")
            ToolResult(
                success = true,
                content = "Alarm set for %02d:%02d — %s".format(hour, minute, message)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            ToolResult(
                success = false,
                content = "",
                errorMessage = "Failed to set alarm: ${e.message}"
            )
        }
    }
}
