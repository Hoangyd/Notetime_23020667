package com.example.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /**
     * [mode]: 0 all, 1 today, 2 upcoming, 3 overdue, 4 completed (see TaskFilter).
     * Pass a null [category] / [priority] to skip that filter.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE (:search = '' OR title LIKE '%' || :search || '%' OR description LIKE '%' || :search || '%')
        AND (:category IS NULL OR category = :category)
        AND (:priority IS NULL OR priority = :priority)
        AND CASE :mode
            WHEN 1 THEN isDone = 0 AND (
                (deadline >= :dayStart AND deadline < :dayEnd)
                OR (scheduledStart >= :dayStart AND scheduledStart < :dayEnd)
            )
            WHEN 2 THEN isDone = 0 AND (deadline IS NULL OR deadline >= :dayEnd)
            WHEN 3 THEN isDone = 0 AND deadline IS NOT NULL AND deadline < :now
            WHEN 4 THEN isDone = 1
            ELSE 1
        END
        ORDER BY isDone, priority DESC, deadline IS NULL, deadline, id
        """
    )
    fun observeFiltered(
        search: String,
        category: String?,
        priority: Int?,
        mode: Int,
        dayStart: Long,
        dayEnd: Long,
        now: Long
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskWithSubtasks?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE (deadline >= :dayStart AND deadline < :dayEnd)
        OR (scheduledStart >= :dayStart AND scheduledStart < :dayEnd)
        ORDER BY isDone, scheduledStart IS NULL, scheduledStart, priority DESC, deadline
        """
    )
    fun observeForDay(dayStart: Long, dayEnd: Long): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDone = 0 AND deadline IS NOT NULL AND deadline < :before
        ORDER BY deadline
        """
    )
    fun observeOverdue(before: Long): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDone = 0 AND deadline IS NOT NULL AND deadline >= :from
        ORDER BY deadline
        LIMIT :limit
        """
    )
    fun observeUpcoming(from: Long, limit: Int): Flow<List<TaskWithSubtasks>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE scheduledStart IS NOT NULL AND scheduledEnd IS NOT NULL
        AND scheduledStart < :to AND scheduledEnd > :from
        ORDER BY scheduledStart
        """
    )
    fun observeScheduledBetween(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE scheduledStart IS NOT NULL AND scheduledEnd IS NOT NULL
        AND scheduledStart < :to AND scheduledEnd > :from
        ORDER BY scheduledStart
        """
    )
    suspend fun getScheduledBetween(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deadline >= :from AND deadline < :to")
    fun observeDueBetween(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT completedAt FROM tasks WHERE isDone = 1 AND completedAt >= :from AND completedAt < :to")
    fun observeCompletedAtBetween(from: Long, to: Long): Flow<List<Long>>

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET isDone = :isDone, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET scheduledStart = :start, scheduledEnd = :end, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSchedule(id: Long, start: Long?, end: Long?, updatedAt: Long)

    // Subtasks

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    suspend fun getSubtasks(taskId: Long): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE id = :id")
    suspend fun getSubtask(id: Long): SubtaskEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM subtasks WHERE taskId = :taskId")
    suspend fun maxSubtaskPosition(taskId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubtask(subtask: SubtaskEntity): Long

    @Query("UPDATE subtasks SET isDone = :isDone WHERE id = :id")
    suspend fun setSubtaskDone(id: Long, isDone: Boolean)

    @Query("UPDATE subtasks SET isDone = :isDone WHERE taskId = :taskId")
    suspend fun setAllSubtasksDone(taskId: Long, isDone: Boolean)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)
}
