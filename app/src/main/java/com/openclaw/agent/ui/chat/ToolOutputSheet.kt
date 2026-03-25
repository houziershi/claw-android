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
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing
import com.openclaw.agent.ui.theme.Success

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
        modifier = modifier,
        shape = ClawShapes.sheet,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ClawSpacing.xl)
                .padding(bottom = ClawSpacing.xxl)
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
                Spacer(Modifier.width(ClawSpacing.sm))
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(ClawSpacing.sm))
                when (toolCall.success) {
                    true -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "成功",
                        modifier = Modifier.size(16.dp),
                        tint = Success
                    )
                    false -> Icon(
                        Icons.Default.Error,
                        contentDescription = "失败",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    null -> {}
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // Technical tool name
            Text(
                text = toolCall.name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(ClawSpacing.lg))

            // Input parameters
            if (toolCall.input != null) {
                Text(
                    text = "输入参数",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(ClawSpacing.xs))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = ClawShapes.cardSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatJson(toolCall.input.toString()),
                        modifier = Modifier.padding(ClawSpacing.md),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(ClawSpacing.md))
            }

            // Output
            if (toolCall.result != null) {
                Text(
                    text = if (toolCall.success == true) "输出结果" else "错误信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (toolCall.success == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(ClawSpacing.xs))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = ClawShapes.cardSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = toolCall.result,
                        modifier = Modifier
                            .padding(ClawSpacing.md)
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
                c == ':' -> sb.append(": ")
                else -> sb.append(c)
            }
            prevChar = c
        }
        sb.toString()
    } catch (_: Exception) {
        json
    }
}
