package com.openclaw.agent.core.tools.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

private const val TAG = "WiFiTool"

class WiFiTool(private val context: Context) : Tool {
    override val name = "wifi"
    override val description = "Manage WiFi. Actions: 'status' (current WiFi state including SSID, signal strength, link speed, IP, frequency), 'open' (open system WiFi settings — Android 10+ does not allow apps to toggle WiFi directly), 'scan' (scan nearby WiFi networks, returns SSID, signal strength, security type)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("status")); add(JsonPrimitive("open")); add(JsonPrimitive("scan")) })
                put("description", "Action: status, open, or scan")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            return ToolResult(success = false, content = "", errorMessage = "This device does not support WiFi.")
        }

        return when (action) {
            "status" -> getStatus(wifiManager)
            "open" -> openSettings()
            "scan" -> scanNetworks(wifiManager)
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use status, open, or scan.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getStatus(wifiManager: WifiManager): ToolResult {
        return try {
            val isEnabled = wifiManager.isWifiEnabled
            val sb = StringBuilder()
            sb.appendLine("WiFi: ${if (isEnabled) "ON ✅" else "OFF ❌"}")

            if (isEnabled) {
                @Suppress("DEPRECATION")
                val connectionInfo: WifiInfo? = wifiManager.connectionInfo

                if (connectionInfo != null && connectionInfo.networkId != -1) {
                    // SSID
                    val ssid = connectionInfo.ssid?.removeSurrounding("\"") ?: "Unknown"
                    sb.appendLine("SSID: $ssid")

                    // BSSID
                    val bssid = connectionInfo.bssid ?: "Unknown"
                    sb.appendLine("BSSID: $bssid")

                    // RSSI & signal level
                    val rssi = connectionInfo.rssi
                    val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        wifiManager.calculateSignalLevel(rssi)
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.calculateSignalLevel(rssi, 5)
                    }
                    val maxLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        wifiManager.maxSignalLevel
                    } else {
                        4
                    }
                    sb.appendLine("Signal: $rssi dBm (level $signalLevel/$maxLevel)")

                    // Link speed
                    val linkSpeed = connectionInfo.linkSpeed
                    sb.appendLine("Link speed: $linkSpeed Mbps")

                    // Frequency
                    val frequency = connectionInfo.frequency
                    val band = when {
                        frequency in 2400..2500 -> "2.4 GHz"
                        frequency in 4900..5900 -> "5 GHz"
                        frequency in 5925..7125 -> "6 GHz"
                        else -> "Unknown"
                    }
                    sb.appendLine("Frequency: $frequency MHz ($band)")

                    // IP address
                    val ipInt = connectionInfo.ipAddress
                    val ipAddress = formatIpAddress(ipInt)
                    sb.appendLine("IP address: $ipAddress")
                } else {
                    sb.appendLine("Not connected to any WiFi network.")
                }
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WiFi status", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get WiFi status: ${e.message}")
        }
    }

    private fun openSettings(): ToolResult {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened WiFi settings")
            ToolResult(success = true, content = "Opened WiFi settings. Please toggle WiFi or select a network.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WiFi settings", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open WiFi settings: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanNetworks(wifiManager: WifiManager): ToolResult {
        return try {
            // Check location permission (required for scan results on Android 9+)
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return ToolResult(
                    success = false, content = "",
                    errorMessage = "ACCESS_FINE_LOCATION permission is required to scan WiFi networks."
                )
            }

            if (!wifiManager.isWifiEnabled) {
                return ToolResult(
                    success = false, content = "",
                    errorMessage = "WiFi is disabled. Please enable WiFi first."
                )
            }

            // Trigger scan (may be throttled on Android 9+: max 4 scans per 2 minutes)
            @Suppress("DEPRECATION")
            val scanStarted = wifiManager.startScan()
            if (!scanStarted) {
                Log.w(TAG, "WiFi scan request was throttled or rejected, using cached results.")
            }

            // Get scan results (may be cached if scan was throttled)
            val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()

            if (results.isEmpty()) {
                return ToolResult(success = true, content = "No WiFi networks found nearby.")
            }

            // Sort by signal strength (strongest first), limit to 20
            val sorted = results
                .sortedByDescending { it.level }
                .take(20)

            val sb = StringBuilder()
            sb.appendLine("Nearby WiFi networks (${sorted.size} of ${results.size} total):")
            sb.appendLine()

            sorted.forEachIndexed { index, result ->
                val ssid = if (result.SSID.isNullOrBlank()) "(Hidden)" else result.SSID
                val security = getSecurityType(result)
                val freq = result.frequency
                val band = when {
                    freq in 2400..2500 -> "2.4G"
                    freq in 4900..5900 -> "5G"
                    freq in 5925..7125 -> "6G"
                    else -> "${freq}MHz"
                }
                sb.appendLine("${index + 1}. $ssid")
                sb.appendLine("   Signal: ${result.level} dBm | Freq: $freq MHz ($band) | Security: $security")
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan WiFi networks", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to scan WiFi networks: ${e.message}")
        }
    }

    private fun getSecurityType(result: ScanResult): String {
        val capabilities = result.capabilities ?: return "Unknown"
        return when {
            capabilities.contains("WPA2") && capabilities.contains("WPA3") -> "WPA2/WPA3"
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("OWE") -> "OWE (Enhanced Open)"
            capabilities.contains("ESS") && !capabilities.contains("WPA") && !capabilities.contains("WEP") -> "Open"
            else -> capabilities
        }
    }

    private fun formatIpAddress(ipInt: Int): String {
        return "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
    }
}
