package com.openclaw.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.ui.components.AssistantAvatar
import com.openclaw.agent.ui.theme.ClawSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Renders a group of consecutive messages from the same role.
 * - User messages: right-aligned, no avatar
 * - Assistant messages: left-aligned, avatar on first bubble, indented for subsequent
 */
@Composable
fun MessageGroupView(
    group: ChatItem.MessageGroup,
    modifier: Modifier = Modifier
) {
    val isUser = group.role == "user"

    if (isUser) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            group.messages.forEachIndexed { index, msg ->
                if (index > 0) Spacer(Modifier.height(ClawSpacing.bubbleSpacing))
                MessageBubble(
                    content = msg.content,
                    isUser = true,
                    isError = msg.isError,
                    isFirstInGroup = (index == 0)
                )
            }
            Text(
                text = formatTime(group.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            group.messages.forEachIndexed { index, msg ->
                if (index > 0) Spacer(Modifier.height(ClawSpacing.bubbleSpacing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Avatar slot: show avatar on first message, empty space on subsequent
                    Box(modifier = Modifier.size(ClawSpacing.avatarSize)) {
                        if (index == 0) AssistantAvatar()
                    }
                    Spacer(Modifier.width(ClawSpacing.avatarGap))
                    Column(modifier = Modifier.weight(1f)) {
                        MessageBubble(
                            content = msg.content,
                            isUser = false,
                            isError = msg.isError,
                            isFirstInGroup = (index == 0)
                        )
                    }
                }
            }

            // Footer: name · timestamp + meta badges
            Row(
                modifier = Modifier.padding(
                    start = ClawSpacing.avatarSize + ClawSpacing.avatarGap,
                    top = 4.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "小元宝",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = formatTime(group.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                MetaBadges(messages = group.messages)
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
}
