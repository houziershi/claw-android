package com.openclaw.agent.data.db

import androidx.room.*
import com.openclaw.agent.data.db.entities.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1 ORDER BY hour, minute")
    fun getAllEnabled(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY hour, minute")
    suspend fun getAll(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getAllEnabledSync(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getById(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTaskEntity)

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE scheduled_tasks SET lastRunAt = :time, nextRunAt = :nextRun WHERE id = :id")
    suspend fun updateRunTime(id: String, time: Long, nextRun: Long)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
