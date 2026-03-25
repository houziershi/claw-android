package com.openclaw.agent.core.tools.impl

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private const val TAG = "SystemActionTool"

class SystemActionTool(private val context: Context) : Tool {
    override val name = "system"
    override val description = "Get system information. Actions: 'battery' (level, charging status, health, temperature, voltage), 'storage' (internal/external storage usage), 'network' (connection type, carrier, signal), 'memory' (RAM usage), 'uptime' (time since last boot)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("battery")); add(JsonPrimitive("storage")); add(JsonPrimitive("network")); add(JsonPrimitive("memory")); add(JsonPrimitive("uptime"))
                })
                put("description", "Action: battery, storage, network, memory, or uptime")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "battery" -> getBatteryInfo()
            "storage" -> getStorageInfo()
            "network" -> getNetworkInfo()
            "memory" -> getMemoryInfo()
            "uptime" -> getUptime()
            else -> ToolResult(
                success = false, content = "",
                errorMessage = "Unknown action: $action. Use battery, storage, network, memory, or uptime."
            )
        }
    }

    private suspend fun getBatteryInfo(): ToolResult = withContext(Dispatchers.IO) {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent == null) {
                return@withContext ToolResult(success = false, content = "", errorMessage = "Unable to read battery info.")
            }

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100 / scale) else -1

            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }

            val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempCelsius = if (temperature > 0) temperature / 10.0 else null

            val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val pluggedStr = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                0 -> "Not plugged"
                else -> "Unknown"
            }

            val sb = StringBuilder()
            sb.appendLine("## Battery Info")
            sb.appendLine("- Level: $pct%")
            sb.appendLine("- Status: $statusStr")
            sb.appendLine("- Health: $healthStr")
            if (tempCelsius != null) {
                sb.appendLine("- Temperature: ${"%.1f".format(tempCelsius)}°C")
            }
            if (voltage > 0) {
                sb.appendLine("- Voltage: ${voltage}mV")
            }
            sb.appendLine("- Plugged: $pluggedStr")

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery info", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get battery info: ${e.message}")
        }
    }

    private suspend fun getStorageInfo(): ToolResult = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            sb.appendLine("## Storage Info")

            // Internal storage
            val internalStat = StatFs(Environment.getDataDirectory().path)
            val internalTotal = internalStat.totalBytes
            val internalFree = internalStat.freeBytes
            val internalAvailable = internalStat.availableBytes
            val internalUsed = internalTotal - internalAvailable

            sb.appendLine("### Internal Storage")
            sb.appendLine("- Total: ${formatSize(internalTotal)}")
            sb.appendLine("- Used: ${formatSize(internalUsed)}")
            sb.appendLine("- Available: ${formatSize(internalAvailable)}")
            sb.appendLine("- Free: ${formatSize(internalFree)}")

            // External storage
            val externalDirs = context.getExternalFilesDirs(null)
            if (externalDirs.size > 1) {
                externalDirs.drop(1).forEachIndexed { index, file ->
                    if (file != null) {
                        try {
                            val extStat = StatFs(file.path)
                            val extTotal = extStat.totalBytes
                            val extAvailable = extStat.availableBytes
                            val extUsed = extTotal - extAvailable

                            sb.appendLine("### External Storage ${index + 1}")
                            sb.appendLine("- Total: ${formatSize(extTotal)}")
                            sb.appendLine("- Used: ${formatSize(extUsed)}")
                            sb.appendLine("- Available: ${formatSize(extAvailable)}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read external storage $index", e)
                        }
                    }
                }
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get storage info", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get storage info: ${e.message}")
        }
    }

    private fun getNetworkInfo(): ToolResult {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)

            val sb = StringBuilder()
            sb.appendLine("## Network Info")

            if (caps == null) {
                sb.appendLine("- Status: Disconnected")
            } else {
                val type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Other"
                }
                sb.appendLine("- Status: Connected")
                sb.appendLine("- Type: $type")

                // Bandwidth
                val downMbps = caps.linkDownstreamBandwidthKbps / 1000
                val upMbps = caps.linkUpstreamBandwidthKbps / 1000
                sb.appendLine("- Downstream: ${downMbps} Mbps")
                sb.appendLine("- Upstream: ${upMbps} Mbps")

                // Carrier info
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val carrier = tm?.networkOperatorName
                if (!carrier.isNullOrBlank()) {
                    sb.appendLine("- Carrier: $carrier")
                }

                // Signal strength (available via TelephonyManager on API 28+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    try {
                        val signalStrength = tm?.signalStrength
                        if (signalStrength != null) {
                            val level = signalStrength.level // 0-4
                            sb.appendLine("- Signal level: $level/4")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get signal strength", e)
                    }
                }
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network info", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get network info: ${e.message}")
        }
    }

    private fun getMemoryInfo(): ToolResult {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)

            val totalMem = memInfo.totalMem
            val availMem = memInfo.availMem
            val usedMem = totalMem - availMem
            val usagePercent = if (totalMem > 0) (usedMem * 100.0 / totalMem) else 0.0
            val threshold = memInfo.threshold
            val lowMemory = memInfo.lowMemory

            val sb = StringBuilder()
            sb.appendLine("## Memory Info")
            sb.appendLine("- Total RAM: ${formatSize(totalMem)}")
            sb.appendLine("- Available RAM: ${formatSize(availMem)}")
            sb.appendLine("- Used RAM: ${formatSize(usedMem)}")
            sb.appendLine("- Usage: ${"%.1f".format(usagePercent)}%")
            sb.appendLine("- Low memory threshold: ${formatSize(threshold)}")
            sb.appendLine("- Low memory: $lowMemory")

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get memory info", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get memory info: ${e.message}")
        }
    }

    private fun getUptime(): ToolResult {
        return try {
            val uptimeMs = SystemClock.elapsedRealtime()
            val totalSeconds = uptimeMs / 1000
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60

            val formatted = buildString {
                if (days > 0) append("${days}天 ")
                if (days > 0 || hours > 0) append("${hours}小时 ")
                append("${minutes}分钟")
            }

            val sb = StringBuilder()
            sb.appendLine("## Uptime")
            sb.appendLine("- Since last boot: $formatted")
            sb.appendLine("- Total: ${totalSeconds}s")

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get uptime", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get uptime: ${e.message}")
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return "${"%.2f".format(value)} ${units[unitIndex]}"
    }
}
