package com.example.notes.data

import com.example.notes.domain.Category
import com.example.notes.domain.Priority
import com.example.notes.domain.TaskFilter
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/** Everything the Today screen needs. */
data class TodayOverview(
    val epochDay: Long,
    /** Tasks due or scheduled today (done and not done). */
    val today: List<TaskWithSubtasks>,
    /** Unfinished tasks whose deadline passed before today. */
    val overdue: List<TaskWithSubtasks>,
    val upcoming: List<TaskWithSubtasks>,
    /** Best next tasks to work on, most important first. */
    val suggestions: List<TaskEntity>
) {
    val totalCount: Int get() = today.size
    val doneCount: Int get() = today.count { it.task.isDone }
    val progress: Float get() = if (today.isEmpty()) 0f else doneCount.toFloat() / today.size
}

class TaskRepository(
    private val taskDao: TaskDao,
    private val noteDao: NoteDao
) {
    fun observeTasks(
        filter: TaskFilter = TaskFilter.ALL,
        search: String = "",
        category: Category? = null,
        priority: Priority? = null,
        epochDay: Long = TimeUtils.today(),
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TaskWithSubtasks>> = taskDao.observeFiltered(
        search = search.trim(),
        category = category?.name,
        priority = priority?.level,
        mode = filter.mode,
        dayStart = TimeUtils.dayStart(epochDay, zone),
        dayEnd = TimeUtils.dayEnd(epochDay, zone),
        now = now
    )

    fun observeTask(id: Long): Flow<TaskWithSubtasks?> = taskDao.observeById(id)

    fun observeToday(
        epochDay: Long = TimeUtils.today(),
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<TodayOverview> {
        val start = TimeUtils.dayStart(epochDay, zone)
        val end = TimeUtils.dayEnd(epochDay, zone)
        return combine(
            taskDao.observeForDay(start, end),
            taskDao.observeOverdue(start),
            taskDao.observeUpcoming(end, UPCOMING_LIMIT)
        ) { today, overdue, upcoming ->
            val candidates = (overdue + today + upcoming).map { it.task }.distinctBy { it.id }
            TodayOverview(
                epochDay = epochDay,
                today = today,
                overdue = overdue,
                upcoming = upcoming,
                suggestions = TaskPrioritizer.suggest(candidates, now)
            )
        }
    }

    /** Creates or updates a task. Returns its id. */
    suspend fun saveTask(task: TaskEntity): Long {
        require(task.title.isNotBlank()) { "Task title is required" }
        val start = task.scheduledStart
        val end = task.scheduledEnd
        require((start == null) == (end == null)) { "Set both start and end, or neither" }
        require(start == null || end == null || end > start) { "End must be after start" }

        val now = System.currentTimeMillis()
        val existing = if (task.id > 0) taskDao.getById(task.id) else null
        val entity = task.copy(
            id = existing?.id ?: 0L,
            title = task.title.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return taskDao.upsert(entity).takeIf { it != UPDATED } ?: entity.id
    }

    suspend fun deleteTask(taskId: Long) {
        noteDao.clearTaskLink(taskId)
        taskDao.deleteById(taskId)
    }

    /** Re-creates a deleted task with its subtasks (undo). Returns the new id. */
    suspend fun restoreTask(deleted: TaskWithSubtasks): Long {
        val id = taskDao.upsert(deleted.task.copy(id = 0L))
        deleted.subtasks.forEach { taskDao.upsertSubtask(it.copy(id = 0L, taskId = id)) }
        return id
    }

    suspend fun setDone(taskId: Long, done: Boolean) {
        val now = System.currentTimeMillis()
        taskDao.setDone(taskId, done, if (done) now else null, now)
        if (done) taskDao.setAllSubtasksDone(taskId, true)
    }

    suspend fun toggleDone(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        setDone(taskId, !task.isDone)
    }

    suspend fun addSubtask(taskId: Long, title: String): Long {
        require(title.isNotBlank()) { "Subtask title is required" }
        val position = taskDao.maxSubtaskPosition(taskId) + 1
        val id = taskDao.upsertSubtask(SubtaskEntity(taskId = taskId, title = title.trim(), position = position))
        // A new open subtask means the parent is no longer finished.
        syncParentWithSubtasks(taskId)
        return id
    }

    suspend fun toggleSubtask(subtaskId: Long) {
        val subtask = taskDao.getSubtask(subtaskId) ?: return
        taskDao.setSubtaskDone(subtaskId, !subtask.isDone)
        syncParentWithSubtasks(subtask.taskId)
    }

    suspend fun deleteSubtask(subtaskId: Long) {
        val subtask = taskDao.getSubtask(subtaskId) ?: return
        taskDao.deleteSubtask(subtaskId)
        syncParentWithSubtasks(subtask.taskId)
    }

    /** Parent is done exactly when it has subtasks and all of them are done. */
    private suspend fun syncParentWithSubtasks(taskId: Long) {
        val subtasks = taskDao.getSubtasks(taskId)
        if (subtasks.isEmpty()) return
        val parent = taskDao.getById(taskId) ?: return
        val allDone = subtasks.all { it.isDone }
        if (allDone != parent.isDone) {
            val now = System.currentTimeMillis()
            taskDao.setDone(taskId, allDone, if (allDone) now else null, now)
        }
    }

    private companion object {
        const val UPCOMING_LIMIT = 10
    }
}
