package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "FlashlightTool"

class FlashlightTool(private val context: Context) : Tool {
    override val name = "flashlight"
    override val description = "Control the device flashlight (torch). Actions: 'on' (turn on), 'off' (turn off), 'status' (check current state), 'toggle' (switch on/off)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("on"))
                    add(JsonPrimitive("off"))
                    add(JsonPrimitive("status"))
                    add(JsonPrimitive("toggle"))
                })
                put("description", "Action: on, off, status, or toggle")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String? = findTorchCameraId()
    private var isTorchOn = false

    init {
        if (cameraId != null) {
            try {
                cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(camId: String, enabled: Boolean) {
                        if (camId == cameraId) {
                            isTorchOn = enabled
                            Log.d(TAG, "Torch state changed: ${if (enabled) "ON" else "OFF"}")
                        }
                    }

                    override fun onTorchModeUnavailable(camId: String) {
                        if (camId == cameraId) {
                            Log.w(TAG, "Torch mode unavailable for camera: $camId")
                        }
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register torch callback", e)
            }
        }
    }

    private fun findTorchCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find torch camera", e)
            null
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        if (cameraId == null) {
            return ToolResult(
                success = false, content = "", errorMessage = "This device does not have a flashlight."
            )
        }

        return when (action) {
            "on" -> setTorch(true)
            "off" -> setTorch(false)
            "status" -> getStatus()
            "toggle" -> setTorch(!isTorchOn)
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use on, off, status, or toggle."
            )
        }
    }

    private fun setTorch(enabled: Boolean): ToolResult {
        return try {
            cameraManager.setTorchMode(cameraId!!, enabled)
            isTorchOn = enabled
            val state = if (enabled) "ON 🔦" else "OFF"
            Log.d(TAG, "Flashlight set to: $state")
            ToolResult(success = true, content = "Flashlight turned $state")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode", e)
            ToolResult(
                success = false, content = "", errorMessage = "Failed to control flashlight: ${e.message}"
            )
        }
    }

    private fun getStatus(): ToolResult {
        val state = if (isTorchOn) "ON 🔦" else "OFF"
        return ToolResult(success = true, content = "Flashlight is currently $state")
    }
}
