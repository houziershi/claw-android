package com.openclaw.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BottomSheet that displays full tool call details:
 * - Tool name + status
 * - Input parameters (JSON formatted)
 * - Full output (scrollable)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolOutputSheet(
    toolCall: ToolCallUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = ToolDisplayConfig.get(toolCall.name)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: tool icon + name + status + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    info.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Status badge
                when (toolCall.success) {
                    true -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "成功",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    false -> Icon(
                        Icons.Default.Error,
                        contentDescription = "失败",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    null -> {}
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // Tool technical name
            Text(
                text = toolCall.name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input parameters
            if (toolCall.input != null) {
                Text(
                    text = "输入参数",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatJson(toolCall.input.toString()),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Output
            if (toolCall.result != null) {
                Text(
                    text = if (toolCall.success == true) "输出结果" else "错误信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (toolCall.success == true) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = toolCall.result,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(scrollState),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/** Simple JSON formatting: add newlines and indentation */
private fun formatJson(json: String): String {
    return try {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var prevChar = ' '

        for (c in json) {
            when {
                c == '"' && prevChar != '\\' -> {
                    inString = !inString
                    sb.append(c)
                }
                inString -> sb.append(c)
                c == '{' || c == '[' -> {
                    sb.append(c)
                    sb.append('\n')
                    indent += 2
                    sb.append(" ".repeat(indent))
                }
                c == '}' || c == ']' -> {
                    sb.append('\n')
                    indent = maxOf(0, indent - 2)
                    sb.append(" ".repeat(indent))
                    sb.append(c)
                }
                c == ',' -> {
                    sb.append(c)
                    sb.append('\n')
                    sb.append(" ".repeat(indent))
                }
                c == ':' -> {
                    sb.append(": ")
                }
                else -> sb.append(c)
            }
            prevChar = c
        }
        sb.toString()
    } catch (_: Exception) {
        json
    }
}
