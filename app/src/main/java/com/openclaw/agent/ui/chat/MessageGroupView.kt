package com.openclaw.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.agent.data.db.entities.MessageEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Renders a group of consecutive messages from the same role.
 * - User messages: right-aligned, no avatar, compact
 * - Assistant messages: left-aligned, with "Claw" label on first message, timestamp
 */
@Composable
fun MessageGroupView(
    group: ChatItem.MessageGroup,
    modifier: Modifier = Modifier
) {
    val isUser = group.role == "user"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label + timestamp (assistant only, on first message)
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                Text(
                    text = "Claw",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatTime(group.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Render each message in the group
        group.messages.forEachIndexed { index, msg ->
            if (index > 0) Spacer(modifier = Modifier.height(2.dp))
            MessageBubble(
                content = msg.content,
                isUser = isUser,
                isError = msg.isError
            )
        }

        // Meta badges (assistant messages with token data)
        if (!isUser) {
            MetaBadges(messages = group.messages)
        }

        // Timestamp for user messages (after the last bubble)
        if (isUser) {
            Text(
                text = formatTime(group.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
}
