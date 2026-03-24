package com.openclaw.agent.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps tool names to friendly display names, icons, and parameter extractors.
 * Unknown tools fall back to sensible defaults.
 */
data class ToolDisplayInfo(
    val displayName: String,
    val icon: ImageVector,
    /** Extract the most relevant parameter(s) for a compact summary */
    val paramSummary: (input: Map<String, Any?>) -> String?
)

object ToolDisplayConfig {

    private val configs = mapOf(
        "web_search" to ToolDisplayInfo(
            displayName = "搜索",
            icon = Icons.Default.Search,
            paramSummary = { it["query"]?.toString()?.take(40) }
        ),
        "web_fetch" to ToolDisplayInfo(
            displayName = "网页获取",
            icon = Icons.Default.Language,
            paramSummary = { it["url"]?.toString()?.take(50) }
        ),
        "get_current_time" to ToolDisplayInfo(
            displayName = "获取时间",
            icon = Icons.Default.Schedule,
            paramSummary = { null }
        ),
        "get_device_info" to ToolDisplayInfo(
            displayName = "设备信息",
            icon = Icons.Default.PhoneAndroid,
            paramSummary = { null }
        ),
        "clipboard" to ToolDisplayInfo(
            displayName = "剪贴板",
            icon = Icons.Default.ContentPaste,
            paramSummary = { it["action"]?.toString() }
        ),
        "volume" to ToolDisplayInfo(
            displayName = "音量",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            paramSummary = { input ->
                val action = input["action"]?.toString()
                val level = input["level"]?.toString()
                if (action == "set" && level != null) "→ $level" else action
            }
        ),
        "alarm" to ToolDisplayInfo(
            displayName = "闹钟",
            icon = Icons.Default.Alarm,
            paramSummary = { input ->
                val time = input["time"]?.toString()
                val label = input["label"]?.toString()
                listOfNotNull(time, label).joinToString(" ").takeIf { it.isNotBlank() }
            }
        ),
        "bluetooth" to ToolDisplayInfo(
            displayName = "蓝牙",
            icon = Icons.Default.Bluetooth,
            paramSummary = { it["action"]?.toString() }
        ),
        "memory_read" to ToolDisplayInfo(
            displayName = "读取记忆",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            paramSummary = { it["file"]?.toString()?.substringAfterLast('/') }
        ),
        "memory_write" to ToolDisplayInfo(
            displayName = "写入记忆",
            icon = Icons.Default.Edit,
            paramSummary = { it["file"]?.toString()?.substringAfterLast('/') }
        ),
        "memory_search" to ToolDisplayInfo(
            displayName = "搜索记忆",
            icon = Icons.AutoMirrored.Filled.ManageSearch,
            paramSummary = { it["query"]?.toString()?.take(30) }
        ),
        "memory_list" to ToolDisplayInfo(
            displayName = "记忆列表",
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            paramSummary = { null }
        )
    )

    private val defaultInfo = ToolDisplayInfo(
        displayName = "",
        icon = Icons.Default.Build,
        paramSummary = { null }
    )

    fun get(toolName: String): ToolDisplayInfo {
        return configs[toolName] ?: defaultInfo.copy(displayName = toolName)
    }

    /**
     * Build a collapsed summary like "搜索, 音量 +2 more"
     */
    fun buildCollapsedSummary(toolCalls: List<ToolCallUiState>): String {
        if (toolCalls.isEmpty()) return ""
        if (toolCalls.size == 1) {
            val info = get(toolCalls[0].name)
            return info.displayName
        }

        val displayNames = toolCalls.map { get(it.name).displayName }.distinct()
        return when {
            displayNames.size <= 2 -> displayNames.joinToString(", ")
            else -> {
                val shown = displayNames.take(2).joinToString(", ")
                "$shown +${displayNames.size - 2} more"
            }
        }
    }
}
