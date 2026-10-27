package com.example.notes.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

/** Result of saving or moving an item: the id plus any blocks it now overlaps with. */
data class ScheduleResult(val id: Long, val conflicts: List<TimeBlock>) {
    val hasConflict: Boolean get() = conflicts.isNotEmpty()
}

class CalendarRepository(
    private val eventDao: EventDao,
    private val taskDao: TaskDao,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    /** Events (repeats expanded) and scheduled tasks in [from, to), ordered by start. */
    fun observeBlocks(from: Long, to: Long): Flow<List<TimeBlock>> = combine(
        eventDao.observeCandidates(from, to),
        taskDao.observeScheduledBetween(from, to)
    ) { events, tasks ->
        Scheduling.buildBlocks(events, tasks, from, to, zone())
    }

    suspend fun getBlocks(from: Long, to: Long): List<TimeBlock> =
        Scheduling.buildBlocks(
            eventDao.getCandidates(from, to),
            taskDao.getScheduledBetween(from, to),
            from,
            to,
            zone()
        )

    suspend fun getEvent(id: Long): EventEntity? = eventDao.getById(id)

    suspend fun findConflicts(
        start: Long,
        end: Long,
        ignoreEventId: Long? = null,
        ignoreTaskId: Long? = null
    ): List<TimeBlock> = Scheduling.findConflicts(getBlocks(start, end), start, end, ignoreEventId, ignoreTaskId)

    /** Saves the event even when it overlaps something; the caller decides how to warn. */
    suspend fun saveEvent(event: EventEntity): ScheduleResult {
        require(event.title.isNotBlank()) { "Event title is required" }
        require(event.endAt > event.startAt) { "End must be after start" }

        val now = System.currentTimeMillis()
        val existing = if (event.id > 0) eventDao.getById(event.id) else null
        val entity = event.copy(
            id = existing?.id ?: 0L,
            title = event.title.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val id = eventDao.upsert(entity).takeIf { it != UPDATED } ?: entity.id
        val conflicts = if (event.allDay) emptyList() else findConflicts(event.startAt, event.endAt, ignoreEventId = id)
        return ScheduleResult(id, conflicts)
    }

    suspend fun deleteEvent(id: Long) = eventDao.deleteById(id)

    /** Drag-and-drop / time edit for an event. Keeps duration when only [newStart] is given. */
    suspend fun moveEvent(id: Long, newStart: Long, newEnd: Long? = null): ScheduleResult? {
        val event = eventDao.getById(id) ?: return null
        val end = newEnd ?: (newStart + (event.endAt - event.startAt))
        require(end > newStart) { "End must be after start" }
        eventDao.setTime(id, newStart, end, System.currentTimeMillis())
        return ScheduleResult(id, findConflicts(newStart, end, ignoreEventId = id))
    }

    /** Places a task on the calendar (or moves it). Keeps duration when only [start] is given. */
    suspend fun scheduleTask(taskId: Long, start: Long, end: Long? = null): ScheduleResult? {
        val task = taskDao.getById(taskId) ?: return null
        val duration = when {
            end != null -> end - start
            task.scheduledStart != null && task.scheduledEnd != null -> task.scheduledEnd - task.scheduledStart
            task.estimatedMinutes != null -> task.estimatedMinutes * 60_000L
            else -> DEFAULT_TASK_MS
        }
        require(duration > 0) { "End must be after start" }
        taskDao.setSchedule(taskId, start, start + duration, System.currentTimeMillis())
        return ScheduleResult(taskId, findConflicts(start, start + duration, ignoreTaskId = taskId))
    }

    /**
     * Moves a calendar block by [deltaDays] and [deltaMinutes] (drag and drop). A repeating
     * event keeps its weekdays, so only the time of day changes for it.
     */
    suspend fun shiftBlock(block: TimeBlock, deltaDays: Int, deltaMinutes: Int): ScheduleResult? {
        val z = zone()
        fun shift(millis: Long, days: Int): Long = Instant.ofEpochMilli(millis).atZone(z)
            .plusDays(days.toLong()).plusMinutes(deltaMinutes.toLong()).toInstant().toEpochMilli()

        return when (block.kind) {
            BlockKind.EVENT -> {
                val event = eventDao.getById(block.id) ?: return null
                val days = if (event.repeatDaysMask != 0) 0 else deltaDays
                val newStart = shift(event.startAt, days)
                moveEvent(block.id, newStart, newStart + (event.endAt - event.startAt))
            }
            BlockKind.TASK -> {
                val days = deltaDays
                scheduleTask(block.id, shift(block.start, days), shift(block.end, days))
            }
        }
    }

    private companion object {
        const val DEFAULT_TASK_MS = 60 * 60_000L
    }
}
