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
    version = 4,
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

        /** v2 → v3: add group chat fields (non-destructive) */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // MessageEntity: add senderType, senderName, mentions
                db.execSQL("ALTER TABLE messages ADD COLUMN senderType TEXT NOT NULL DEFAULT 'user'")
                db.execSQL("ALTER TABLE messages ADD COLUMN senderName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN mentions TEXT")
                // SessionEntity: add chatType
                db.execSQL("ALTER TABLE sessions ADD COLUMN chatType TEXT NOT NULL DEFAULT 'single'")
            }
        }

        /** v3 → v4: add content blocks, model, cache tokens, and cost columns */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN contentBlocks TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN model TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN cacheReadTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN cacheWriteTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN costTotal REAL")
            }
        }
    }
}
