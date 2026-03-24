package com.openclaw.agent.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** "user" | "assistant" */
    val role: String,
    /** Plain text content for simple messages */
    val content: String,
    /** JSON-encoded list of tool_use blocks (for assistant messages) */
    val toolCallsJson: String? = null,
    /** JSON-encoded tool_result content (for tool messages) */
    val toolResultJson: String? = null,
    val timestamp: Long,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val isError: Boolean = false,
    /** Sender type for group chat: "user" | "claw" | "nanobot" */
    val senderType: String = "user",
    /** Display name of the sender */
    val senderName: String = "",
    /** JSON array of mentioned bot names, e.g. ["claw","nanobot"] */
    val mentions: String? = null,
    /** JSON-encoded List<ContentBlock> */
    val contentBlocks: String? = null,
    /** Model name used to generate this message */
    val model: String? = null,
    val cacheReadTokens: Int? = null,
    val cacheWriteTokens: Int? = null,
    val costTotal: Double? = null
)
