package com.openclaw.agent.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.db.entities.ScheduledTaskEntity
import com.openclaw.agent.data.db.entities.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class, ScheduledTaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao

    companion object {
        const val DATABASE_NAME = "openclaw_db"
    }
}
