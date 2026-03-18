package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CurrentTimeTool : Tool {
    override val name = "get_current_time"
    override val description = "Get the current date and time, including timezone information."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.getDefault())
        val tz = TimeZone.getDefault()
        val now = Date()
        return ToolResult(
            success = true,
            content = "Current time: ${sdf.format(now)}, Timezone: ${tz.id} (${tz.getDisplayName(false, TimeZone.SHORT)})"
        )
    }
}
