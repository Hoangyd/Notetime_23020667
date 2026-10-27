package com.example.notes.data

import com.example.notes.domain.Category
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.ZoneId

data class DayCompletion(val epochDay: Long, val due: Int, val done: Int) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

data class CategoryPlan(val category: Category, val plannedMs: Long, val actualMs: Long)

class InsightsRepository(
    private val insightsDao: InsightsDao,
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
    private val habitDao: HabitDao,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    /** Per-day tasks due vs done for the 7 days starting at [weekStartDay] (a Monday). */
    fun observeWeeklyCompletion(weekStartDay: Long): Flow<List<DayCompletion>> {
        val z = zone()
        val from = TimeUtils.dayStart(weekStartDay, z)
        val to = TimeUtils.dayStart(weekStartDay + 7, z)
        return taskDao.observeDueBetween(from, to).map { tasks ->
            val byDay = tasks.groupBy { TimeUtils.epochDay(it.deadline!!, z) }
            (0L until 7L).map { offset ->
                val day = weekStartDay + offset
                val dayTasks = byDay[day].orEmpty()
                DayCompletion(day, dayTasks.size, dayTasks.count { it.isDone })
            }
        }
    }

    /** Activity per day (tasks finished + habit check-ins) for a GitHub-style heatmap. */
    fun observeHeatmap(fromDay: Long, toDay: Long): Flow<Map<Long, Int>> {
        val z = zone()
        return combine(
            taskDao.observeCompletedAtBetween(TimeUtils.dayStart(fromDay, z), TimeUtils.dayEnd(toDay, z)),
            habitDao.observeLogCountsBetween(fromDay, toDay)
        ) { completedAt, habitCounts ->
            val result = mutableMapOf<Long, Int>()
            completedAt.forEach { result.merge(TimeUtils.epochDay(it, z), 1, Int::plus) }
            habitCounts.forEach { result.merge(it.epochDay, it.count, Int::plus) }
            result
        }
    }

    /** Actual time spent per category in [from, to) from logged time. */
    fun observeTimeByCategory(from: Long, to: Long): Flow<List<CategoryTotal>> =
        insightsDao.observeActualByCategory(from, to)

    /** Planned (calendar blocks) vs actual (logged) time per category in [from, to). */
    fun observePlannedVsActual(from: Long, to: Long): Flow<List<CategoryPlan>> = combine(
        eventDao.observeCandidates(from, to),
        taskDao.observeScheduledBetween(from, to),
        insightsDao.observeActualByCategory(from, to)
    ) { events, tasks, actual ->
        val planned = Scheduling.buildBlocks(events, tasks, from, to, zone())
            .filter { !it.allDay }
            .groupBy { it.category }
            .mapValues { (_, blocks) -> blocks.sumOf { it.durationMs } }
        val actualByCategory = actual.associate { it.category to it.totalMs }

        Category.entries.map { category ->
            CategoryPlan(
                category = category,
                plannedMs = planned[category.name] ?: 0L,
                actualMs = actualByCategory[category.name] ?: 0L
            )
        }
    }

    suspend fun logTime(
        category: Category,
        start: Long,
        end: Long,
        taskId: Long? = null,
        note: String = ""
    ): Long {
        require(end > start) { "End must be after start" }
        return insightsDao.upsertTimeLog(
            TimeLogEntity(taskId = taskId, category = category.name, startAt = start, endAt = end, note = note)
        )
    }
}
