package com.openclaw.agent.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String,
    val systemPrompt: String? = null,
    val messageCount: Int = 0
)
