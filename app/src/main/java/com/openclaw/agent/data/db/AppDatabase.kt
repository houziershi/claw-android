package com.openclaw.agent.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

        /** v1 → v2: add scheduled_tasks table (non-destructive) */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scheduled_tasks (
                        id TEXT NOT NULL PRIMARY KEY,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        message TEXT NOT NULL,
                        prompt TEXT,
                        repeat TEXT NOT NULL,
                        dayOfWeek INTEGER,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        lastRunAt INTEGER,
                        nextRunAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
