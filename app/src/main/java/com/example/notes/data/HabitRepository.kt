package com.example.notes.data

import com.example.notes.domain.StreakCalculator
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HabitWithStats(
    val habit: HabitEntity,
    val currentStreak: Int,
    val longestStreak: Int,
    val doneToday: Boolean,
    val scheduledToday: Boolean,
    /** Completion rate over the last 30 days (0f..1f). */
    val rate30Days: Float,
    val doneDays: Set<Long>
)

class HabitRepository(
    private val habitDao: HabitDao
) {
    fun observeHabits(today: Long = TimeUtils.today()): Flow<List<HabitWithStats>> = combine(
        habitDao.observeActive(),
        habitDao.observeAllLogs()
    ) { habits, logs ->
        val daysByHabit = logs.groupBy({ it.habitId }, { it.epochDay }).mapValues { it.value.toSet() }
        habits.map { habit ->
            val done = daysByHabit[habit.id].orEmpty()
            val createdDay = TimeUtils.epochDay(habit.createdAt)
            HabitWithStats(
                habit = habit,
                currentStreak = StreakCalculator.current(done, habit.daysMask, today),
                longestStreak = StreakCalculator.longest(done, habit.daysMask),
                doneToday = today in done,
                scheduledToday = TimeUtils.isDayInMask(habit.daysMask, today),
                rate30Days = StreakCalculator.completionRate(
                    done, habit.daysMask, maxOf(createdDay, today - 29), today
                ),
                doneDays = done
            )
        }
    }

    suspend fun saveHabit(habit: HabitEntity): Long {
        require(habit.name.isNotBlank()) { "Habit name is required" }
        require(habit.daysMask and 0b1111111 != 0) { "Pick at least one day" }
        val existing = if (habit.id > 0) habitDao.getById(habit.id) else null
        val entity = habit.copy(
            id = existing?.id ?: 0L,
            name = habit.name.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        return habitDao.upsert(entity).takeIf { it != UPDATED } ?: entity.id
    }

    suspend fun archiveHabit(id: Long, archived: Boolean = true) = habitDao.setArchived(id, archived)

    suspend fun deleteHabit(id: Long) = habitDao.deleteById(id)

    /** Marks or unmarks [epochDay]. Returns true when the day is now completed. */
    suspend fun toggle(habitId: Long, epochDay: Long = TimeUtils.today()): Boolean {
        return if (habitDao.hasLog(habitId, epochDay)) {
            habitDao.deleteLog(habitId, epochDay)
            false
        } else {
            habitDao.insertLog(HabitLogEntity(habitId, epochDay))
            true
        }
    }
}
