package com.openclaw.agent.core.tools.impl

import android.util.Log
import com.openclaw.agent.core.mijia.MijiaApiClient
import com.openclaw.agent.core.mijia.MijiaApiException
import com.openclaw.agent.core.mijia.MijiaDevice
import com.openclaw.agent.core.mijia.MiotSpecCache
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

private const val TAG = "MijiaControlTool"

class MijiaControlTool(
    private val apiClient: MijiaApiClient,
    private val specCache: MiotSpecCache
) : Tool {
    override val name = "mijia_control"
    override val description = """Get or set a property of a Xiaomi Mijia smart home device.
- action 'get': read current value of a property
- action 'set': change a property value
Always call mijia_list_devices first to find valid device names.
For unknown property names, call mijia_device_info to discover available properties."""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("get"); add("set") })
                put("description", "Action: 'get' to read, 'set' to write")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Device name (fuzzy match, e.g. '客厅灯', '空气净化器')")
            }
            putJsonObject("prop") {
                put("type", "string")
                put("description", "Property name, e.g. 'on', 'brightness', 'mode'. Use mijia_device_info to discover properties.")
            }
            putJsonObject("value") {
                put("description", "Value to set (for action=set). Use 'true'/'false' for bool, number for int/float.")
            }
        }
        put("required", buildJsonArray { add("action"); add("name"); add("prop") })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (!apiClient.isAuthenticated()) {
            return ToolResult(
                success = false, content = "",
                errorMessage = "AUTH_REQUIRED: 未登录小米账号。请前往 设置 → 米家账号 完成登录。"
            )
        }

        val action = args["action"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, content = "", errorMessage = "Missing 'action' parameter")
        val deviceName = args["name"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, content = "", errorMessage = "Missing 'name' parameter")
        val propName = args["prop"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, content = "", errorMessage = "Missing 'prop' parameter")

        return try {
            // Find device by fuzzy name match
            val devices = apiClient.getAllDevices()
            val device = fuzzyFindDevice(devices, deviceName)
                ?: return ToolResult(
                    success = false, content = "",
                    errorMessage = "未找到设备 '$deviceName'。可用设备: ${devices.map { it.name }.joinToString(", ")}"
                )

            if (!device.isOnline) {
                return ToolResult(
                    success = false, content = "",
                    errorMessage = "设备 '${device.name}' 当前离线，无法操作。"
                )
            }

            // Resolve property to siid/piid
            val spec = specCache.getSpec(device.model)
            val prop = specCache.fuzzyMatchProperty(spec, propName)
                ?: return ToolResult(
                    success = false, content = "",
                    errorMessage = "未找到属性 '$propName'。请调用 mijia_device_info(model='${device.model}') 查看支持的属性。"
                )

            when (action) {
                "get" -> {
                    if (!prop.rw.contains("r")) {
                        return ToolResult(success = false, content = "", errorMessage = "属性 '${prop.name}' 不支持读取")
                    }
                    val results = apiClient.getDevicesProp(listOf(mapOf("did" to device.did, "siid" to prop.siid, "piid" to prop.piid)))
                    val result = results.firstOrNull()
                    val code = result?.get("code")?.jsonPrimitive?.intOrNull ?: -1
                    if (code != 0) {
                        return ToolResult(success = false, content = "", errorMessage = "读取失败 (code=$code)")
                    }
                    val value = result?.get("value")
                    val unit = prop.unit?.let { " $it" } ?: ""
                    ToolResult(success = true, content = "${device.name} 的 ${prop.name} = $value$unit")
                }
                "set" -> {
                    if (!prop.rw.contains("w")) {
                        return ToolResult(success = false, content = "", errorMessage = "属性 '${prop.name}' 不支持写入")
                    }
                    val rawValue = args["value"]
                        ?: return ToolResult(success = false, content = "", errorMessage = "Missing 'value' for action=set")

                    val typedValue = coerceValue(rawValue, prop.type)
                    val results = apiClient.setDevicesProp(listOf(mapOf(
                        "did" to device.did, "siid" to prop.siid, "piid" to prop.piid, "value" to typedValue
                    )))
                    val result = results.firstOrNull()
                    val code = result?.get("code")?.jsonPrimitive?.intOrNull ?: -1
                    if (code != 0 && code != 1) {
                        return ToolResult(success = false, content = "", errorMessage = "设置失败 (code=$code)")
                    }
                    val note = if (code == 1) "（网关已接收，结果未确认）" else ""
                    ToolResult(success = true, content = "✅ 已将 ${device.name} 的 ${prop.name} 设为 $typedValue$note")
                }
                else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action")
            }
        } catch (e: MijiaApiException) {
            ToolResult(success = false, content = "", errorMessage = "米家 API 错误: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Control error", e)
            ToolResult(success = false, content = "", errorMessage = "操作失败: ${e.message}")
        }
    }

    private fun fuzzyFindDevice(devices: List<MijiaDevice>, query: String): MijiaDevice? {
        val q = query.trim()
        return devices.firstOrNull { it.name == q }
            ?: devices.firstOrNull { it.name.contains(q) || q.contains(it.name) }
            ?: devices.firstOrNull { it.did == q }
    }

    private fun coerceValue(rawValue: JsonElement, type: String): Any {
        return when (type) {
            "bool" -> when {
                rawValue is JsonPrimitive && rawValue.booleanOrNull != null -> rawValue.boolean
                rawValue.jsonPrimitive.content.lowercase() == "true" -> true
                rawValue.jsonPrimitive.content.lowercase() == "false" -> false
                rawValue.jsonPrimitive.content == "1" -> true
                rawValue.jsonPrimitive.content == "0" -> false
                else -> rawValue.jsonPrimitive.content.toBoolean()
            }
            "int", "uint" -> rawValue.jsonPrimitive.content.toInt()
            "float" -> rawValue.jsonPrimitive.content.toDouble()
            else -> rawValue.jsonPrimitive.content
        }
    }
}
