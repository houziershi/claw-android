package com.openclaw.agent.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject

/**
 * Collapsible tool call group.
 *
 * Collapsed: "▸ ⚡ 搜索, 音量 +2 more" (single line)
 * Expanded: shows each tool with status, params, and result preview.
 *
 * Behaviour:
 * - While ANY tool is running → forced expanded with spinner
 * - After all complete → collapses, tappable to re-expand
 * - Click on individual tool → opens ToolOutputSheet
 */
@Composable
fun CollapsibleToolCards(
    toolCalls: List<ToolCallUiState>,
    onToolClick: (ToolCallUiState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (toolCalls.isEmpty()) return

    val anyRunning = toolCalls.any { it.isRunning }
    val allSuccess = toolCalls.all { it.success == true }
    val anyFailed = toolCalls.any { it.success == false }

    // Auto-expand while running, collapse when done (user can toggle)
    var userExpanded by remember { mutableStateOf(true) }
    val isExpanded = anyRunning || userExpanded

    // Reset userExpanded when tools finish
    LaunchedEffect(anyRunning) {
        if (!anyRunning) {
            userExpanded = false
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            // ── Collapsed header ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !anyRunning) { userExpanded = !userExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                when {
                    anyRunning -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    allSuccess -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    anyFailed -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Lightning bolt + count
                Text(
                    text = "⚡",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Summary text
                Text(
                    text = if (anyRunning) {
                        val runningNames = toolCalls.filter { it.isRunning }
                            .joinToString(", ") { ToolDisplayConfig.get(it.name).displayName }
                        "$runningNames..."
                    } else {
                        "${toolCalls.size} tool${if (toolCalls.size > 1) "s" else ""} · ${ToolDisplayConfig.buildCollapsedSummary(toolCalls)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Expand arrow (hidden while running)
                if (!anyRunning) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Expanded detail ───────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(150)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(100))
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    toolCalls.forEach { tc ->
                        ToolDetailItem(
                            toolCall = tc,
                            onClick = { onToolClick(tc) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single tool detail row inside the expanded section.
 * Shows: status icon | friendly name | param summary | result preview
 */
@Composable
fun ToolDetailItem(
    toolCall: ToolCallUiState,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val info = ToolDisplayConfig.get(toolCall.name)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = when {
            toolCall.isRunning -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            toolCall.success == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status indicator
                when {
                    toolCall.isRunning -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                    toolCall.success == true -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Tool icon
                Icon(
                    info.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Friendly name
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Param summary (if available)
                val paramText = toolCall.input?.let { input ->
                    info.paramSummary(input.mapValues { (_, v) -> v.toString().trim('"') })
                }
                if (!paramText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = paramText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Result preview (completed only)
            if (!toolCall.isRunning && toolCall.result != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = toolCall.result.take(200).let {
                        if (toolCall.result.length > 200) "$it…" else it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
