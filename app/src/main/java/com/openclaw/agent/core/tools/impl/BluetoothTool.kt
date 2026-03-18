package com.openclaw.agent.core.tools.impl

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

private const val TAG = "BluetoothTool"

class BluetoothTool(private val context: Context) : Tool {
    override val name = "bluetooth"
    override val description = "Manage Bluetooth. Actions: 'status' (check on/off + paired devices), 'open' (open system Bluetooth settings to enable), 'close' (open system Bluetooth settings to disable). Note: Android 13+ does not allow apps to toggle Bluetooth directly, so open/close will navigate to system settings."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("status"); add("open"); add("close") })
                put("description", "Action: status, open, or close")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    @SuppressLint("MissingPermission")
    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            return ToolResult(success = false, content = "", errorMessage = "This device does not support Bluetooth.")
        }

        return when (action) {
            "status" -> getStatus(adapter)
            "open", "close" -> openSettings(action)
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use status, open, or close.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getStatus(adapter: BluetoothAdapter): ToolResult {
        return try {
            val isEnabled = adapter.isEnabled
            val sb = StringBuilder()
            sb.appendLine("Bluetooth: ${if (isEnabled) "ON ✅" else "OFF ❌"}")

            if (isEnabled) {
                val deviceName = try { adapter.name ?: "Unknown" } catch (e: Exception) { "Unknown" }
                sb.appendLine("Device name: $deviceName")

                // Paired devices
                val pairedDevices = try { adapter.bondedDevices } catch (e: Exception) { emptySet() }
                if (pairedDevices.isNullOrEmpty()) {
                    sb.appendLine("Paired devices: none")
                } else {
                    sb.appendLine("Paired devices (${pairedDevices.size}):")
                    pairedDevices.forEach { device ->
                        val name = try { device.name ?: "Unknown" } catch (e: Exception) { "Unknown" }
                        val address = device.address ?: "?"
                        val type = when (device.type) {
                            1 -> "Classic"
                            2 -> "BLE"
                            3 -> "Dual"
                            else -> "Unknown"
                        }
                        sb.appendLine("  • $name ($type) — $address")
                    }
                }
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Bluetooth status", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to get Bluetooth status: ${e.message}")
        }
    }

    private fun openSettings(action: String): ToolResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Bluetooth settings for: $action")

            val hint = if (action == "open") {
                "Opened Bluetooth settings. Please toggle Bluetooth ON."
            } else {
                "Opened Bluetooth settings. Please toggle Bluetooth OFF."
            }
            ToolResult(success = true, content = hint)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Bluetooth settings", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open Bluetooth settings: ${e.message}")
        }
    }
}
