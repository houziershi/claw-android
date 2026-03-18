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
    val isError: Boolean = false
)
