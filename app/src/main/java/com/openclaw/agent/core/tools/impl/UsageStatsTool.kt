package com.openclaw.agent.core.tools.impl

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "UsageStatsTool"

class UsageStatsTool(private val context: Context) : Tool {
    override val name = "usage_stats"
    override val description = "Query app usage statistics. Actions: 'today' (today's per-app usage time, top 20), 'range' (usage stats for a date range, requires start_date and end_date in yyyy-MM-dd), 'screen_time' (total screen time today), 'grant' (open usage access permission settings)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("today")); add(JsonPrimitive("range")); add(JsonPrimitive("screen_time")); add(JsonPrimitive("grant")) })
                put("description", "Action: today, range, screen_time, or grant")
            }
            putJsonObject("start_date") {
                put("type", "string")
                put("description", "Start date in yyyy-MM-dd format (required for 'range' action)")
            }
            putJsonObject("end_date") {
                put("type", "string")
                put("description", "End date in yyyy-MM-dd format (required for 'range' action)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.jsonPrimitive?.content ?: return@withContext ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        when (action) {
            "grant" -> openGrantSettings()
            "today" -> {
                if (!hasPermission()) return@withContext noPermissionResult()
                getTodayStats()
            }
            "range" -> {
                if (!hasPermission()) return@withContext noPermissionResult()
                val startDate = args["start_date"]?.jsonPrimitive?.content
                    ?: return@withContext ToolResult(success = false, content = "", errorMessage = "Missing 'start_date' parameter for range action")
                val endDate = args["end_date"]?.jsonPrimitive?.content
                    ?: return@withContext ToolResult(success = false, content = "", errorMessage = "Missing 'end_date' parameter for range action")
                getRangeStats(startDate, endDate)
            }
            "screen_time" -> {
                if (!hasPermission()) return@withContext noPermissionResult()
                getScreenTime()
            }
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use today, range, screen_time, or grant.")
        }
    }

    private fun hasPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check usage stats permission", e)
            false
        }
    }

    private fun noPermissionResult(): ToolResult {
        return ToolResult(
            success = false,
            content = "Usage access permission is not granted. Please use the 'grant' action to open settings and enable usage access for this app.",
            errorMessage = "Usage access permission not granted"
        )
    }

    private fun openGrantSettings(): ToolResult {
        return try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened usage access settings")
            ToolResult(success = true, content = "Opened usage access settings. Please grant usage access permission for this app.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open usage access settings", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open usage access settings: ${e.message}")
        }
    }

    private fun getTodayStats(): ToolResult {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val now = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            if (stats.isNullOrEmpty()) {
                return ToolResult(success = true, content = "No usage data available for today.")
            }

            val filtered = stats
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(20)

            if (filtered.isEmpty()) {
                return ToolResult(success = true, content = "No app usage recorded today.")
            }

            val sb = StringBuilder()
            sb.appendLine("📊 Today's App Usage (top ${filtered.size}):")
            sb.appendLine()
            filtered.forEachIndexed { index, usageStats ->
                val appName = getAppName(usageStats.packageName)
                val duration = formatDuration(usageStats.totalTimeInForeground)
                sb.appendLine("${index + 1}. $appName — $duration")
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get today's usage stats", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get today's usage stats: ${e.message}")
        }
    }

    private fun getRangeStats(startDate: String, endDate: String): ToolResult {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startMillis = dateFormat.parse(startDate)?.time
                ?: return ToolResult(success = false, content = "", errorMessage = "Invalid start_date format: $startDate. Use yyyy-MM-dd.")
            val endCalendar = Calendar.getInstance().apply {
                time = dateFormat.parse(endDate)
                    ?: return ToolResult(success = false, content = "", errorMessage = "Invalid end_date format: $endDate. Use yyyy-MM-dd.")
                // Set to end of day
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endMillis = endCalendar.timeInMillis

            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis)
            if (stats.isNullOrEmpty()) {
                return ToolResult(success = true, content = "No usage data available for $startDate to $endDate.")
            }

            // Aggregate by package name (multiple daily entries may exist)
            val aggregated = stats
                .filter { it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }
                .mapValues { (_, entries) -> entries.sumOf { it.totalTimeInForeground } }
                .entries
                .sortedByDescending { it.value }
                .take(20)

            if (aggregated.isEmpty()) {
                return ToolResult(success = true, content = "No app usage recorded for $startDate to $endDate.")
            }

            val sb = StringBuilder()
            sb.appendLine("📊 App Usage ($startDate to $endDate, top ${aggregated.size}):")
            sb.appendLine()
            aggregated.forEachIndexed { index, (packageName, totalTime) ->
                val appName = getAppName(packageName)
                val duration = formatDuration(totalTime)
                sb.appendLine("${index + 1}. $appName — $duration")
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get range usage stats", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get range usage stats: ${e.message}")
        }
    }

    private fun getScreenTime(): ToolResult {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val now = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            val totalTime = stats
                ?.filter { it.totalTimeInForeground > 0 }
                ?.sumOf { it.totalTimeInForeground }
                ?: 0L

            val duration = formatDuration(totalTime)
            ToolResult(success = true, content = "📱 Today's total screen time: $duration")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screen time", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get screen time: ${e.message}")
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m ${seconds}s"
        }
    }
}
