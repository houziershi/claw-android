package com.openclaw.agent.core.tools.impl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*
import java.util.Calendar

private const val TAG = "AlarmTool"
private const val CHANNEL_ID = "claw_alarm"
private const val CHANNEL_NAME = "Claw Alarms"

class AlarmTool(private val context: Context) : Tool {
    override val name = "alarm"
    override val description = "Manage alarms. Actions: 'set' (create alarm), 'list' (query system next alarm), 'delete' (try to cancel alarm). Note: list can only return the next upcoming system alarm due to Android API limitation."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("set"); add("list"); add("delete") })
                put("description", "Action: set, list, or delete")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Hour in 24-hour format (0-23). Required for set/delete.")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Minute (0-59). Required for set/delete.")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Label for the alarm (only for set)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications from Claw"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )
        return when (action) {
            "set" -> setAlarm(args)
            "list" -> listAlarms()
            "delete" -> deleteAlarm(args)
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use set, list, or delete.")
        }
    }

    // ── SET ──────────────────────────────────────────────────────────────

    private fun setAlarm(args: JsonObject): ToolResult {
        return try {
            val hour = args["hour"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'hour' parameter"
            )
            val minute = args["minute"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'minute' parameter"
            )
            val message = args["message"]?.jsonPrimitive?.content ?: "Claw Alarm"

            // Strategy 1: Try system AlarmClock intent
            var systemAlarmSet = false
            try {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                systemAlarmSet = true
                Log.d(TAG, "System alarm set: %02d:%02d".format(hour, minute))
            } catch (e: Exception) {
                Log.w(TAG, "System AlarmClock failed: ${e.message}")
            }

            // Strategy 2: AlarmManager as backup notification
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val requestCode = hour * 100 + minute
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("message", message)
                putExtra("hour", hour)
                putExtra("minute", minute)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }

            val timeStr = "%02d:%02d".format(hour, minute)
            val resultMsg = if (systemAlarmSet) {
                "Alarm set for $timeStr — $message"
            } else {
                "Alarm set for $timeStr — $message (Claw will notify you)"
            }
            Log.d(TAG, resultMsg)
            ToolResult(success = true, content = resultMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to set alarm: ${e.message}")
        }
    }

    // ── LIST ─────────────────────────────────────────────────────────────

    private fun listAlarms(): ToolResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextAlarm: AlarmManager.AlarmClockInfo? = alarmManager.nextAlarmClock
            if (nextAlarm != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTime }
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val m = cal.get(Calendar.MINUTE)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val weekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    Calendar.SUNDAY -> "Sun"
                    else -> ""
                }
                ToolResult(
                    success = true,
                    content = "Next alarm: %d/%d (%s) %02d:%02d. Note: Android only allows querying the next upcoming alarm, not all alarms.".format(month, day, weekday, h, m)
                )
            } else {
                ToolResult(success = true, content = "No upcoming alarms found.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read system alarm: ${e.message}")
            ToolResult(success = true, content = "Unable to read system alarms.")
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────────

    private fun deleteAlarm(args: JsonObject): ToolResult {
        return try {
            val hour = args["hour"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'hour' for delete"
            )
            val minute = args["minute"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'minute' for delete"
            )

            // Cancel AlarmManager backup notification
            val requestCode = hour * 100 + minute
            val alarmIntent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, alarmIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "AlarmManager alarm cancelled: %02d:%02d".format(hour, minute))
            }

            // Try dismiss system alarm (may fail from background)
            try {
                val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dismissIntent)
                Log.d(TAG, "System alarm dismissed: %02d:%02d".format(hour, minute))
            } catch (e: Exception) {
                Log.w(TAG, "System dismiss failed: ${e.message}")
            }

            // Cancel notification if any
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(requestCode)

            ToolResult(
                success = true,
                content = "Claw notification for %02d:%02d cancelled. Note: system Clock app alarms may need to be manually deleted by the user.".format(hour, minute)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete alarm", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to delete alarm: ${e.message}")
        }
    }
}

/**
 * BroadcastReceiver that fires when the AlarmManager alarm triggers.
 * Shows a high-priority notification with sound and vibration.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Claw Alarm"
        val hour = intent.getIntExtra("hour", 0)
        val minute = intent.getIntExtra("minute", 0)
        val timeStr = "%02d:%02d".format(hour, minute)

        Log.d(TAG, "Alarm triggered: $timeStr — $message")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $message")
            .setContentText("Alarm: $timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(hour * 100 + minute, notification)
    }
}
