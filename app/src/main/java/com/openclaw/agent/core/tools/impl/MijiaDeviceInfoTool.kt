package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.mijia.MijiaApiClient
import com.openclaw.agent.core.mijia.MiotSpecCache
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

class MijiaDeviceInfoTool(
    private val apiClient: MijiaApiClient,
    private val specCache: MiotSpecCache
) : Tool {
    override val name = "mijia_device_info"
    override val description = """Query MIoT spec for a Xiaomi device model to discover available properties and actions.
Use this when you need to know what properties a device supports before calling mijia_control.
The 'model' can be obtained from mijia_list_devices results."""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("model") {
                put("type", "string")
                put("description", "Device model string, e.g. 'lumi.switch.b2nacn02', 'zhimi.airpurifier.ma2'")
            }
        }
        put("required", buildJsonArray { add("model") })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val model = args["model"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, content = "", errorMessage = "Missing 'model' parameter")

        return try {
            val spec = specCache.getSpec(model)

            val sb = StringBuilder()
            sb.appendLine("## ${spec.name} (${spec.model})")
            sb.appendLine()

            if (spec.properties.isNotEmpty()) {
                sb.appendLine("### 可读写属性 (Properties)")
                spec.properties.forEach { prop ->
                    val rwStr = when (prop.rw) {
                        "r" -> "只读"
                        "w" -> "只写"
                        "rw" -> "读写"
                        else -> prop.rw
                    }
                    val rangeStr = prop.range?.let { r ->
                        " [${r.joinToString(", ")}]"
                    } ?: ""
                    val unitStr = prop.unit?.let { " ($it)" } ?: ""
                    sb.appendLine("- **${prop.name}** (${prop.type}$unitStr, $rwStr$rangeStr)")
                    if (prop.description.isNotBlank()) sb.appendLine("  ${prop.description}")
                    prop.valueList?.let { values ->
                        sb.appendLine("  枚举值: ${values.joinToString(", ") { "${it.value}=${it.description}" }}")
                    }
                }
            }

            if (spec.actions.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("### 可执行动作 (Actions)")
                spec.actions.forEach { action ->
                    sb.appendLine("- **${action.name}**: ${action.description}")
                }
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "获取设备信息失败: ${e.message}")
        }
    }
}
