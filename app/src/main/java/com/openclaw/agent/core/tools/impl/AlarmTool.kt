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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Calendar

private const val TAG = "AlarmTool"
private const val CHANNEL_ID = "claw_alarm"
private const val CHANNEL_NAME = "Claw Alarms"

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
        return try {
            val hour = args["hour"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'hour' parameter"
            )
            val minute = args["minute"]?.jsonPrimitive?.int ?: return ToolResult(
                success = false, content = "", errorMessage = "Missing 'minute' parameter"
            )
            val message = args["message"]?.jsonPrimitive?.content ?: "Claw Alarm"

            // Strategy 1: Try system AlarmClock intent (may fail from background)
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

            // Strategy 2: Always set AlarmManager as backup (guaranteed to work)
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
                Log.d(TAG, "Exact alarm set via AlarmManager: %02d:%02d".format(hour, minute))
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.d(TAG, "Inexact alarm set via AlarmManager: %02d:%02d".format(hour, minute))
            }

            val timeStr = "%02d:%02d".format(hour, minute)
            val resultMsg = if (systemAlarmSet) {
                "Alarm set for $timeStr — $message"
            } else {
                "Alarm set for $timeStr — $message (Claw will notify you)"
            }

            ToolResult(success = true, content = resultMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to set alarm: ${e.message}")
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

        // Create notification channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
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
