package com.example.notes

import com.example.notes.data.BlockKind
import com.example.notes.data.EventEntity
import com.example.notes.data.Scheduling
import com.example.notes.data.TaskEntity
import com.example.notes.data.TaskPrioritizer
import com.example.notes.domain.StreakCalculator
import com.example.notes.domain.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LogicTest {
    private val zone = ZoneId.of("UTC")
    private val hour = 3_600_000L

    // 2024-01-01 is a Monday.
    private val monday = LocalDate.of(2024, 1, 1).toEpochDay()

    private fun at(day: Long, hourOfDay: Int): Long =
        TimeUtils.dayStart(day, zone) + hourOfDay * hour

    // ---- streaks ----

    @Test
    fun streak_countsConsecutiveDaysEndingToday() {
        val done = setOf(monday, monday + 1, monday + 2)
        assertEquals(3, StreakCalculator.current(done, TimeUtils.ALL_DAYS_MASK, monday + 2))
    }

    @Test
    fun streak_pendingTodayDoesNotBreakIt() {
        val done = setOf(monday, monday + 1)
        assertEquals(2, StreakCalculator.current(done, TimeUtils.ALL_DAYS_MASK, monday + 2))
    }

    @Test
    fun streak_missedScheduledDayBreaksIt() {
        val done = setOf(monday, monday + 2)
        assertEquals(1, StreakCalculator.current(done, TimeUtils.ALL_DAYS_MASK, monday + 3))
        assertEquals(0, StreakCalculator.current(done, TimeUtils.ALL_DAYS_MASK, monday + 4))
    }

    @Test
    fun streak_skipsUnscheduledDays() {
        // Mon/Wed/Fri habit, all done: the days in between must not break the streak.
        val mwf = 0b0010101
        val done = setOf(monday, monday + 2, monday + 4)
        assertEquals(3, StreakCalculator.current(done, mwf, monday + 4))
        assertEquals(3, StreakCalculator.current(done, mwf, monday + 5)) // Saturday: unscheduled
    }

    @Test
    fun longestStreak_findsBestRun() {
        val done = setOf(monday, monday + 1, monday + 3, monday + 4, monday + 5)
        assertEquals(3, StreakCalculator.longest(done, TimeUtils.ALL_DAYS_MASK))
        assertEquals(0, StreakCalculator.longest(emptySet(), TimeUtils.ALL_DAYS_MASK))
    }

    @Test
    fun completionRate_onlyCountsScheduledDays() {
        val mwf = 0b0010101
        val rate = StreakCalculator.completionRate(setOf(monday, monday + 2), mwf, monday, monday + 6)
        assertEquals(2f / 3f, rate, 0.001f)
    }

    // ---- scheduling ----

    @Test
    fun conflicts_detectsOverlapButNotTouchingBlocks() {
        val a = TaskEntity(id = 1, title = "A", scheduledStart = at(monday, 9), scheduledEnd = at(monday, 10))
        val b = TaskEntity(id = 2, title = "B", scheduledStart = at(monday, 10), scheduledEnd = at(monday, 11))
        val event = EventEntity(id = 3, title = "C", startAt = at(monday, 10), endAt = at(monday, 12))

        val blocks = Scheduling.buildBlocks(listOf(event), listOf(a, b), at(monday, 0), at(monday + 1, 0), zone)
        val conflicts = Scheduling.conflictKeys(blocks)

        assertEquals(setOf("T:2@$monday", "E:3@$monday"), conflicts)
    }

    @Test
    fun conflicts_ignoreDoneTasksAndAllDayEvents() {
        val done = TaskEntity(
            id = 1, title = "A", isDone = true,
            scheduledStart = at(monday, 9), scheduledEnd = at(monday, 11)
        )
        val open = TaskEntity(id = 2, title = "B", scheduledStart = at(monday, 10), scheduledEnd = at(monday, 12))
        val allDay = EventEntity(
            id = 3, title = "Holiday", allDay = true,
            startAt = at(monday, 0), endAt = at(monday + 1, 0)
        )

        val blocks = Scheduling.buildBlocks(listOf(allDay), listOf(done, open), at(monday, 0), at(monday + 1, 0), zone)

        assertTrue(Scheduling.conflictKeys(blocks).isEmpty())
    }

    @Test
    fun findConflicts_excludesTheItemBeingMoved() {
        val task = TaskEntity(id = 7, title = "A", scheduledStart = at(monday, 9), scheduledEnd = at(monday, 10))
        val blocks = Scheduling.buildBlocks(emptyList(), listOf(task), at(monday, 0), at(monday + 1, 0), zone)

        assertEquals(1, Scheduling.findConflicts(blocks, at(monday, 9), at(monday, 10)).size)
        assertTrue(Scheduling.findConflicts(blocks, at(monday, 9), at(monday, 10), ignoreTaskId = 7).isEmpty())
    }

    @Test
    fun weeklyEvent_expandsOnlyOnItsDaysWithinRange() {
        // Class every Mon and Wed, 08:00-09:30, starting Monday.
        val lecture = EventEntity(
            id = 1, title = "Math",
            startAt = at(monday, 8), endAt = at(monday, 8) + 90 * 60_000L,
            repeatDaysMask = 0b0000101
        )

        val blocks = Scheduling.buildBlocks(listOf(lecture), emptyList(), at(monday, 0), at(monday + 14, 0), zone)

        assertEquals(4, blocks.size)
        assertTrue(blocks.all { it.kind == BlockKind.EVENT && it.durationMs == 90 * 60_000L })
        assertEquals(listOf(monday, monday + 2, monday + 7, monday + 9), blocks.map { it.occurrenceDay })
    }

    @Test
    fun weeklyEvent_respectsRepeatUntil() {
        val lecture = EventEntity(
            id = 1, title = "Math",
            startAt = at(monday, 8), endAt = at(monday, 9),
            repeatDaysMask = 0b0000001,
            repeatUntil = at(monday + 7, 23)
        )

        val blocks = Scheduling.buildBlocks(listOf(lecture), emptyList(), at(monday, 0), at(monday + 28, 0), zone)

        assertEquals(2, blocks.size) // week 1 and week 2 only
    }

    // ---- prioritising ----

    @Test
    fun suggest_prefersOverdueThenPriorityThenSoonDeadline() {
        val now = at(monday, 12)
        val overdue = TaskEntity(id = 1, title = "overdue", priority = 1, deadline = now - 5 * hour)
        val soonHigh = TaskEntity(id = 2, title = "soonHigh", priority = 2, deadline = now + 4 * hour)
        val farLow = TaskEntity(id = 3, title = "farLow", priority = 0, deadline = now + 200 * hour)
        val done = TaskEntity(id = 4, title = "done", priority = 2, deadline = now - hour, isDone = true)

        val result = TaskPrioritizer.suggest(listOf(farLow, done, soonHigh, overdue), now)

        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
        assertFalse(result.any { it.isDone })
    }

    @Test
    fun weekStart_returnsMonday() {
        assertEquals(monday, TimeUtils.weekStart(monday + 6)) // Sunday -> Monday of same week
        assertEquals(monday, TimeUtils.weekStart(monday))
    }
}
