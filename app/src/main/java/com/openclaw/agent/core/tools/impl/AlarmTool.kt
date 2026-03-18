package com.openclaw.agent.core.tools.impl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
private const val PREFS_NAME = "claw_alarms"

class AlarmTool(private val context: Context) : Tool {
    override val name = "alarm"
    override val description = "Manage alarms. Actions: 'set' (create alarm), 'list' (show all Claw alarms), 'delete' (cancel alarm by hour+minute)."
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

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

            // Strategy 2: AlarmManager as backup
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

            // Save to local record
            saveAlarmRecord(hour, minute, message, calendar.timeInMillis)

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
        val alarms = getAllAlarmRecords()
        if (alarms.isEmpty()) {
            return ToolResult(success = true, content = "No alarms set by Claw.")
        }

        val now = System.currentTimeMillis()
        val sb = StringBuilder("Claw Alarms (${alarms.size}):\n")
        alarms.sortedBy { it.triggerTime }.forEach { alarm ->
            val status = if (alarm.triggerTime > now) "⏳ pending" else "✅ fired"
            sb.appendLine("  • %02d:%02d — %s [%s]".format(alarm.hour, alarm.minute, alarm.message, status))
        }
        return ToolResult(success = true, content = sb.toString().trim())
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

            // Cancel AlarmManager
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

            // Try dismiss system alarm
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

            // Remove local record
            removeAlarmRecord(hour, minute)

            // Cancel notification if any
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(requestCode)

            ToolResult(
                success = true,
                content = "Claw notification for %02d:%02d cancelled. Note: system Clock app alarms cannot be deleted from background — user may need to manually delete them in the Clock app.".format(hour, minute)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete alarm", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to delete alarm: ${e.message}")
        }
    }

    // ── Local alarm records (SharedPreferences) ─────────────────────────

    private data class AlarmRecord(val hour: Int, val minute: Int, val message: String, val triggerTime: Long)

    private fun alarmKey(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

    private fun saveAlarmRecord(hour: Int, minute: Int, message: String, triggerTime: Long) {
        val key = alarmKey(hour, minute)
        val value = "$message|$triggerTime"
        prefs.edit().putString(key, value).apply()
        Log.d(TAG, "Saved alarm record: $key -> $value")
    }

    private fun removeAlarmRecord(hour: Int, minute: Int) {
        val key = alarmKey(hour, minute)
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Removed alarm record: $key")
    }

    private fun getAllAlarmRecords(): List<AlarmRecord> {
        return prefs.all.mapNotNull { (key, value) ->
            try {
                val parts = key.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                val valueParts = (value as String).split("|", limit = 2)
                val message = valueParts[0]
                val triggerTime = valueParts[1].toLong()
                AlarmRecord(hour, minute, message, triggerTime)
            } catch (e: Exception) {
                null
            }
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
