package com.example.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY createdAt, id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity): Long

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Logs

    @Query("SELECT * FROM habit_logs")
    fun observeAllLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT epochDay, COUNT(*) AS count FROM habit_logs WHERE epochDay BETWEEN :fromDay AND :toDay GROUP BY epochDay")
    fun observeLogCountsBetween(fromDay: Long, toDay: Long): Flow<List<DayCount>>

    @Query("SELECT COUNT(*) FROM habit_logs")
    suspend fun countLogs(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay)")
    suspend fun hasLog(habitId: Long, epochDay: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteLog(habitId: Long, epochDay: Long)
}
