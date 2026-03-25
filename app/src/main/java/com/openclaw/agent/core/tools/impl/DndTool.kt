package com.openclaw.agent.core.tools.impl

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

private const val TAG = "DndTool"

class DndTool(private val context: Context) : Tool {
    override val name = "dnd"
    override val description = "Manage Do Not Disturb mode. Actions: 'status' (check current DND state), 'on' (enable DND, optional duration_minutes), 'off' (disable DND and restore normal notifications)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("status")); add(JsonPrimitive("on")); add(JsonPrimitive("off")) })
                put("description", "Action: status, on, or off")
            }
            putJsonObject("duration_minutes") {
                put("type", "integer")
                put("description", "Optional: auto-disable DND after this many minutes (only for 'on' action)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingOffRunnable: Runnable? = null

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return ToolResult(
                    success = false, content = "", errorMessage = "NotificationManager not available on this device."
                )

        return when (action) {
            "status" -> getStatus(notificationManager)
            "on" -> enableDnd(notificationManager, args)
            "off" -> disableDnd(notificationManager)
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use status, on, or off."
            )
        }
    }

    private fun getStatus(nm: NotificationManager): ToolResult {
        return try {
            val filterName = interruptionFilterName(nm.currentInterruptionFilter)
            val isDndOn = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            val sb = StringBuilder()
            sb.appendLine("Do Not Disturb: ${if (isDndOn) "ON 🔕" else "OFF 🔔"}")
            sb.appendLine("Mode: $filterName")
            sb.appendLine("Policy access granted: ${nm.isNotificationPolicyAccessGranted}")
            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get DND status", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get DND status: ${e.message}")
        }
    }

    private fun enableDnd(nm: NotificationManager, args: JsonObject): ToolResult {
        return try {
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened notification policy access settings for DND permission")
                return ToolResult(
                    success = false,
                    content = "DND policy access not granted. Opened system settings — please grant notification policy access to this app, then try again."
                )
            }

            // Cancel any previously scheduled auto-off
            cancelPendingOff()

            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            Log.d(TAG, "DND enabled (INTERRUPTION_FILTER_NONE)")

            val durationMinutes = args["duration_minutes"]?.jsonPrimitive?.intOrNull
            val message = if (durationMinutes != null && durationMinutes > 0) {
                val delayMs = durationMinutes * 60_000L
                val runnable = Runnable {
                    try {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        Log.d(TAG, "DND auto-disabled after $durationMinutes minutes")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-disable DND", e)
                    }
                }
                pendingOffRunnable = runnable
                handler.postDelayed(runnable, delayMs)
                "Do Not Disturb enabled 🔕 (will auto-disable in $durationMinutes minutes)"
            } else {
                "Do Not Disturb enabled 🔕"
            }

            ToolResult(success = true, content = message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable DND", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to enable DND: ${e.message}")
        }
    }

    private fun disableDnd(nm: NotificationManager): ToolResult {
        return try {
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened notification policy access settings for DND permission")
                return ToolResult(
                    success = false,
                    content = "DND policy access not granted. Opened system settings — please grant notification policy access to this app, then try again."
                )
            }

            // Cancel any pending auto-off since we're manually turning off
            cancelPendingOff()

            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            Log.d(TAG, "DND disabled (INTERRUPTION_FILTER_ALL)")
            ToolResult(success = true, content = "Do Not Disturb disabled 🔔 — notifications restored to normal.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable DND", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to disable DND: ${e.message}")
        }
    }

    private fun cancelPendingOff() {
        pendingOffRunnable?.let {
            handler.removeCallbacks(it)
            Log.d(TAG, "Cancelled pending DND auto-off")
        }
        pendingOffRunnable = null
    }

    private fun interruptionFilterName(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "Normal (all notifications)"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority only"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "Total silence"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
        else -> "Unknown ($filter)"
    }
}
