package com.openclaw.agent.core.tools.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

private const val TAG = "NotificationTool"
private const val CHANNEL_ID = "claw_general"
private const val CHANNEL_NAME = "General Notifications"

class NotificationTool(private val context: Context) : Tool {
    override val name = "notification"
    override val description = "Manage notifications. Actions: 'send' (send a local notification with title, message, optional priority), 'channels' (list all notification channels), 'settings' (open app notification settings)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("send")); add(JsonPrimitive("channels")); add(JsonPrimitive("settings")) })
                put("description", "Action: send, channels, or settings")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Notification title (required for send)")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Notification message body (required for send)")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("low")); add(JsonPrimitive("default")); add(JsonPrimitive("high")) })
                put("description", "Notification priority: low, default, or high (optional, defaults to default)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "send" -> sendNotification(args)
            "channels" -> listChannels()
            "settings" -> openSettings()
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use send, channels, or settings.")
        }
    }

    private fun sendNotification(args: JsonObject): ToolResult {
        val title = args["title"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'title' parameter for send action"
        )
        val message = args["message"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'message' parameter for send action"
        )
        val priority = args["priority"]?.jsonPrimitive?.contentOrNull ?: "default"

        return try {
            // Check POST_NOTIFICATIONS permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionResult = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                    return ToolResult(
                        success = false,
                        content = "",
                        errorMessage = "POST_NOTIFICATIONS permission not granted. Cannot send notification on Android 13+."
                    )
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Ensure notification channel exists (required for Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = when (priority) {
                    "low" -> NotificationManager.IMPORTANCE_LOW
                    "high" -> NotificationManager.IMPORTANCE_HIGH
                    else -> NotificationManager.IMPORTANCE_DEFAULT
                }
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                    description = "General notifications from Claw Agent"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationPriority = when (priority) {
                "low" -> NotificationCompat.PRIORITY_LOW
                "high" -> NotificationCompat.PRIORITY_HIGH
                else -> NotificationCompat.PRIORITY_DEFAULT
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(notificationPriority)
                .setAutoCancel(true)
                .build()

            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)

            Log.d(TAG, "Notification sent: id=$notificationId, title=$title, priority=$priority")
            ToolResult(success = true, content = "Notification sent successfully (id=$notificationId, title=\"$title\", priority=$priority).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to send notification: ${e.message}")
        }
    }

    private fun listChannels(): ToolResult {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return ToolResult(success = true, content = "Notification channels are not supported below Android 8.0 (API 26).")
            }

            val channels = notificationManager.notificationChannels
            if (channels.isNullOrEmpty()) {
                return ToolResult(success = true, content = "No notification channels found.")
            }

            val sb = StringBuilder()
            sb.appendLine("Notification channels (${channels.size}):")
            channels.forEach { channel ->
                val importanceLabel = when (channel.importance) {
                    NotificationManager.IMPORTANCE_NONE -> "None"
                    NotificationManager.IMPORTANCE_MIN -> "Min"
                    NotificationManager.IMPORTANCE_LOW -> "Low"
                    NotificationManager.IMPORTANCE_DEFAULT -> "Default"
                    NotificationManager.IMPORTANCE_HIGH -> "High"
                    NotificationManager.IMPORTANCE_MAX -> "Max"
                    else -> "Unknown(${channel.importance})"
                }
                sb.appendLine("  • ${channel.name} (id=${channel.id}, importance=$importanceLabel)")
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list notification channels", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to list notification channels: ${e.message}")
        }
    }

    private fun openSettings(): ToolResult {
        return try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened app notification settings")
            ToolResult(success = true, content = "Opened app notification settings.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open notification settings: ${e.message}")
        }
    }
}
