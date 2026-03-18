package com.openclaw.agent.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val hour: Int,                   // 0-23
    val minute: Int,                 // 0-59
    val type: String,                // "simple" | "agent"
    val message: String,             // 基础提醒文本（保底通知）
    val prompt: String?,             // agent 类型的 LLM prompt
    val repeat: String,              // "once" | "daily" | "weekly" | "weekdays"
    val dayOfWeek: Int?,             // 1(Mon)-7(Sun) for weekly
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastRunAt: Long? = null,
    val nextRunAt: Long              // 下次执行时间戳 (ms)
)
