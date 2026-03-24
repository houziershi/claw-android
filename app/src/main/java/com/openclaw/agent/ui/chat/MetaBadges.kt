package com.openclaw.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.agent.data.db.entities.MessageEntity

/**
 * Compact metadata badges shown below an assistant message group.
 * Format: "↑1.2K ↓856 $0.0123 sonnet-4-6"
 *
 * Only shown for assistant messages that have token data.
 */
@Composable
fun MetaBadges(
    messages: List<MessageEntity>,
    modifier: Modifier = Modifier
) {
    // Aggregate from the last message in the group (which holds cumulative usage)
    val lastMsg = messages.lastOrNull() ?: return
    val inputTokens = lastMsg.inputTokens
    val outputTokens = lastMsg.outputTokens
    val model = lastMsg.model
    val cost = lastMsg.costTotal

    // Only show if we have at least some data
    if (inputTokens == null && outputTokens == null && model == null) return

    Row(
        modifier = modifier
            .padding(start = 4.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val badgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        val badgeStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        // Input tokens
        if (inputTokens != null && inputTokens > 0) {
            Text(
                text = "↑${formatTokenCount(inputTokens)}",
                style = badgeStyle,
                color = badgeColor
            )
        }

        // Output tokens
        if (outputTokens != null && outputTokens > 0) {
            Text(
                text = "↓${formatTokenCount(outputTokens)}",
                style = badgeStyle,
                color = badgeColor
            )
        }

        // Cost
        if (cost != null && cost > 0) {
            Text(
                text = formatCost(cost),
                style = badgeStyle,
                color = badgeColor
            )
        }

        // Model name (truncated)
        if (!model.isNullOrBlank()) {
            Text(
                text = formatModelName(model),
                style = badgeStyle,
                color = badgeColor
            )
        }
    }
}

/** Format token count: 1234 → "1.2K", 500 → "500" */
private fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

/** Format cost: 0.0123 → "$0.012", 0.15 → "$0.15" */
private fun formatCost(cost: Double): String {
    return when {
        cost < 0.001 -> String.format("$%.4f", cost)
        cost < 0.01 -> String.format("$%.3f", cost)
        else -> String.format("$%.2f", cost)
    }
}

/** Truncate model name: "claude-sonnet-4-6-20250929" → "sonnet-4-6" */
private fun formatModelName(model: String): String {
    // Common patterns to shorten
    val name = model
        .removePrefix("claude-")
        .removePrefix("anthropic/")
        .replace(Regex("-\\d{8}$"), "")  // remove date suffix
    return if (name.length > 15) name.take(15) else name
}
