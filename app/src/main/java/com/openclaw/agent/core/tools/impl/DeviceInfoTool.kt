package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DeviceInfoTool(private val context: Context) : Tool {
    override val name = "get_device_info"
    override val description = "Get current device information including battery level, network status, storage, time, and device model."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val info = buildString {
                // Device
                appendLine("## Device")
                appendLine("- Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

                // Time
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                appendLine("\n## Time")
                appendLine("- Local: ${sdf.format(Date())}")
                appendLine("- Timezone: ${TimeZone.getDefault().id}")

                // Battery
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent != null) {
                    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (scale > 0) (level * 100 / scale) else -1
                    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    appendLine("\n## Battery")
                    appendLine("- Level: $pct%")
                    appendLine("- Charging: $charging")
                }

                // Network
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(net)
                appendLine("\n## Network")
                if (caps == null) {
                    appendLine("- Status: Disconnected")
                } else {
                    val type = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        else -> "Other"
                    }
                    appendLine("- Status: Connected ($type)")
                }

                // Storage
                try {
                    val stat = StatFs(Environment.getDataDirectory().path)
                    val totalGB = stat.totalBytes / (1024.0 * 1024 * 1024)
                    val freeGB = stat.availableBytes / (1024.0 * 1024 * 1024)
                    appendLine("\n## Storage")
                    appendLine("- Total: ${"%.1f".format(totalGB)} GB")
                    appendLine("- Free: ${"%.1f".format(freeGB)} GB")
                } catch (_: Exception) {}
            }
            ToolResult(success = true, content = info)
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "Failed to get device info: ${e.message}")
        }
    }
}
