package com.example.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeLog(log: TimeLogEntity): Long

    @Query(
        """
        SELECT category, SUM(endAt - startAt) AS totalMs FROM time_logs
        WHERE startAt >= :from AND startAt < :to
        GROUP BY category
        """
    )
    fun observeActualByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>
}
