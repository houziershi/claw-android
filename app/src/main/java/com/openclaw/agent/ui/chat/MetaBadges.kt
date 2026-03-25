package com.openclaw.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.ui.theme.ClawShapes

/**
 * Compact pill-shaped metadata badges shown below an assistant message group.
 * Format: "↑1.2K" "↓856" "$0.0123" "sonnet-4-6"
 *
 * Only shown for assistant messages that have token data.
 */
@Composable
fun MetaBadges(
    messages: List<MessageEntity>,
    modifier: Modifier = Modifier
) {
    val lastMsg = messages.lastOrNull() ?: return
    val inputTokens = lastMsg.inputTokens
    val outputTokens = lastMsg.outputTokens
    val model = lastMsg.model
    val cost = lastMsg.costTotal

    if (inputTokens == null && outputTokens == null && model == null) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (inputTokens != null && inputTokens > 0) {
            MetaBadge(text = "↑${formatTokenCount(inputTokens)}")
        }
        if (outputTokens != null && outputTokens > 0) {
            MetaBadge(text = "↓${formatTokenCount(outputTokens)}")
        }
        if (cost != null && cost > 0) {
            MetaBadge(text = formatCost(cost))
        }
        if (!model.isNullOrBlank()) {
            MetaBadge(text = formatModelName(model))
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = ClawShapes.chip
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

private fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatCost(cost: Double): String {
    return when {
        cost < 0.001 -> String.format("$%.4f", cost)
        cost < 0.01 -> String.format("$%.3f", cost)
        else -> String.format("$%.2f", cost)
    }
}

private fun formatModelName(model: String): String {
    val name = model
        .removePrefix("claude-")
        .removePrefix("anthropic/")
        .replace(Regex("-\\d{8}$"), "")
    return if (name.length > 15) name.take(15) else name
}
