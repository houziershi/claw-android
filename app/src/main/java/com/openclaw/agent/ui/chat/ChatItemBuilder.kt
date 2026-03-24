package com.openclaw.agent.ui.chat

import com.openclaw.agent.data.db.entities.MessageEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sealed hierarchy of items rendered in the chat LazyColumn.
 * Produced by [ChatItemBuilder.build] from raw MessageEntity list.
 */
sealed class ChatItem {
    /** Unique key for LazyColumn stable identity */
    abstract val key: String

    /** Date separator: "今天" / "昨天" / "3月22日" */
    data class DateSeparator(val date: LocalDate, override val key: String) : ChatItem()

    /** A group of consecutive messages from the same role */
    data class MessageGroup(
        val role: String,
        val messages: List<MessageEntity>,
        /** First message timestamp (for display) */
        val timestamp: Long,
        override val key: String
    ) : ChatItem()

    /** Tool calls block (collapsed) — attached to the preceding assistant group */
    data class ToolCallsBlock(
        val toolMessages: List<MessageEntity>,
        override val key: String
    ) : ChatItem()
}

/**
 * Transforms a flat list of [MessageEntity] into a structured [ChatItem] list.
 *
 * Rules:
 * 1. Skip tool-internal messages (toolResultJson != null)
 * 2. Skip intermediate tool call placeholders (toolCallsJson != null && content starts with "[Tool call:")
 * 3. Merge consecutive same-role text messages into a [ChatItem.MessageGroup]
 * 4. Insert [ChatItem.DateSeparator] when date changes between groups
 */
object ChatItemBuilder {

    fun build(messages: List<MessageEntity>, showToolCalls: Boolean = false): List<ChatItem> {
        if (messages.isEmpty()) return emptyList()

        val result = mutableListOf<ChatItem>()
        var lastDate: LocalDate? = null
        var currentGroup: MutableList<MessageEntity>? = null
        var currentRole: String? = null

        for (msg in messages) {
            // Skip tool result messages (these are "user" role but contain tool results)
            if (msg.toolResultJson != null) continue

            // Skip intermediate tool call placeholders unless dev mode
            if (!showToolCalls && msg.toolCallsJson != null && msg.content.startsWith("[Tool call:")) continue

            val msgDate = Instant.ofEpochMilli(msg.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            // Insert date separator if date changed
            if (lastDate == null || msgDate != lastDate) {
                // Flush current group before date separator
                flushGroup(currentGroup, currentRole, result)
                currentGroup = null
                currentRole = null

                result.add(ChatItem.DateSeparator(
                    date = msgDate,
                    key = "date_${msgDate}"
                ))
                lastDate = msgDate
            }

            // Group consecutive same-role messages
            if (msg.role == currentRole && currentGroup != null) {
                currentGroup.add(msg)
            } else {
                // Flush previous group
                flushGroup(currentGroup, currentRole, result)
                // Start new group
                currentGroup = mutableListOf(msg)
                currentRole = msg.role
            }
        }

        // Flush remaining group
        flushGroup(currentGroup, currentRole, result)

        return result
    }

    private fun flushGroup(
        group: MutableList<MessageEntity>?,
        role: String?,
        result: MutableList<ChatItem>
    ) {
        if (group.isNullOrEmpty() || role == null) return
        result.add(ChatItem.MessageGroup(
            role = role,
            messages = group.toList(),
            timestamp = group.first().timestamp,
            key = "group_${group.first().id}"
        ))
    }
}
