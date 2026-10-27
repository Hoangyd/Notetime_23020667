package com.example.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    /** One-off events overlapping the range, plus repeating events that may occur in it. */
    @Query(
        """
        SELECT * FROM events
        WHERE (repeatDaysMask = 0 AND startAt < :to AND endAt > :from)
        OR (repeatDaysMask != 0 AND startAt < :to AND (repeatUntil IS NULL OR repeatUntil >= :from))
        ORDER BY startAt
        """
    )
    fun observeCandidates(from: Long, to: Long): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE (repeatDaysMask = 0 AND startAt < :to AND endAt > :from)
        OR (repeatDaysMask != 0 AND startAt < :to AND (repeatUntil IS NULL OR repeatUntil >= :from))
        ORDER BY startAt
        """
    )
    suspend fun getCandidates(from: Long, to: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Upsert
    suspend fun upsert(event: EventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE events SET startAt = :start, endAt = :end, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTime(id: Long, start: Long, end: Long, updatedAt: Long)
}
