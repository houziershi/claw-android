package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.mijia.MijiaApiClient
import com.openclaw.agent.core.mijia.MijiaApiException
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

class MijiaListDevicesTool(
    private val apiClient: MijiaApiClient
) : Tool {
    override val name = "mijia_list_devices"
    override val description = """List all Xiaomi Mijia smart home devices with current online status.
Returns: JSON array of {name, did, model, online, room} for each device.
Use this first before controlling any device to get valid device names and their online status."""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        put("required", buildJsonArray {})
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (!apiClient.isAuthenticated()) {
            return ToolResult(
                success = false,
                content = "",
                errorMessage = "AUTH_REQUIRED: 未登录小米账号。请前往 设置 → 米家账号 完成登录后再试。"
            )
        }
        return try {
            val devices = apiClient.getAllDevices()
            if (devices.isEmpty()) {
                return ToolResult(success = true, content = "[]（未找到任何米家设备）")
            }
            val snapshot = buildJsonArray {
                devices.forEach { device ->
                    addJsonObject {
                        put("name", device.name)
                        put("did", device.did)
                        put("model", device.model)
                        put("online", device.isOnline)
                        put("room", device.roomName)
                    }
                }
            }
            ToolResult(success = true, content = snapshot.toString())
        } catch (e: MijiaApiException) {
            ToolResult(success = false, content = "", errorMessage = "米家 API 错误: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "获取设备列表失败: ${e.message}")
        }
    }
}
