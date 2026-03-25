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
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing
import com.openclaw.agent.ui.theme.Success

/**
 * Collapsible tool call group.
 *
 * Collapsed: "▸ ⚡ 2 tools · search, volume" (single line)
 * Expanded: each tool with status, params, and result preview.
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

    var userExpanded by remember { mutableStateOf(true) }
    val isExpanded = anyRunning || userExpanded

    LaunchedEffect(anyRunning) {
        if (!anyRunning) userExpanded = false
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = ClawShapes.card
    ) {
        Column {
            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !anyRunning) { userExpanded = !userExpanded }
                    .padding(horizontal = ClawSpacing.md, vertical = ClawSpacing.sm + 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                when {
                    anyRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    allSuccess -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Success
                    )
                    anyFailed -> Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.width(ClawSpacing.sm))

                Text(text = "⚡", fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))

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

                if (!anyRunning) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Expanded detail ──────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(150)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(100))
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = ClawSpacing.md,
                        end = ClawSpacing.md,
                        bottom = ClawSpacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(ClawSpacing.xs)
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
            toolCall.isRunning -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            toolCall.success == true -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        },
        shape = ClawShapes.cardSmall
    ) {
        Column(modifier = Modifier.padding(ClawSpacing.sm + 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    toolCall.isRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                    toolCall.success == true -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Success
                    )
                    else -> Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.width(6.dp))

                Icon(
                    info.icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))

                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val paramText = toolCall.input?.let { input ->
                    info.paramSummary(input.mapValues { (_, v) -> v.toString().trim('"') })
                }
                if (!paramText.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
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

            if (!toolCall.isRunning && toolCall.result != null) {
                Spacer(Modifier.height(4.dp))
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
