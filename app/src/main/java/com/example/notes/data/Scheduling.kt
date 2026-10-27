package com.example.notes.data

import com.example.notes.domain.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class BlockKind { EVENT, TASK }

/** Something that occupies time on the calendar: an event occurrence or a scheduled task. */
data class TimeBlock(
    val kind: BlockKind,
    val id: Long,
    /** Epoch day of this occurrence (distinguishes repeats of the same event). */
    val occurrenceDay: Long,
    val title: String,
    val category: String,
    val color: Int,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val isDone: Boolean = false,
    val isRepeating: Boolean = false
) {
    val key: String get() = "${kind.name[0]}:$id@$occurrenceDay"
    val durationMs: Long get() = end - start
}

object Scheduling {
    fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        aStart < bEnd && bStart < aEnd

    /** Expands repeating events and merges scheduled tasks into blocks inside [from, to). */
    fun buildBlocks(
        events: List<EventEntity>,
        tasks: List<TaskEntity>,
        from: Long,
        to: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TimeBlock> {
        val blocks = mutableListOf<TimeBlock>()

        for (event in events) {
            if (event.repeatDaysMask == 0) {
                if (overlaps(event.startAt, event.endAt, from, to)) {
                    blocks += event.toBlock(event.startAt, event.endAt, TimeUtils.epochDay(event.startAt, zone))
                }
                continue
            }

            val startZoned = Instant.ofEpochMilli(event.startAt).atZone(zone)
            val firstDay = startZoned.toLocalDate().toEpochDay()
            val time = startZoned.toLocalTime()
            val duration = event.endAt - event.startAt
            val rangeFirstDay = TimeUtils.epochDay(from, zone) - 1 // overnight events
            val rangeLastDay = TimeUtils.epochDay(to - 1, zone)

            for (day in maxOf(firstDay, rangeFirstDay)..rangeLastDay) {
                if (!TimeUtils.isDayInMask(event.repeatDaysMask, day)) continue
                val occStart = LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (event.repeatUntil != null && occStart > event.repeatUntil) continue
                val occEnd = occStart + duration
                if (overlaps(occStart, occEnd, from, to)) {
                    blocks += event.toBlock(occStart, occEnd, day)
                }
            }
        }

        for (task in tasks) {
            val start = task.scheduledStart ?: continue
            val end = task.scheduledEnd ?: continue
            if (!overlaps(start, end, from, to)) continue
            blocks += TimeBlock(
                kind = BlockKind.TASK,
                id = task.id,
                occurrenceDay = TimeUtils.epochDay(start, zone),
                title = task.title,
                category = task.category,
                color = 0,
                start = start,
                end = end,
                isDone = task.isDone
            )
        }

        return blocks.sortedWith(compareBy({ it.start }, { it.end }, { it.key }))
    }

    /** Blocks that overlap another block. All-day events and finished tasks never conflict. */
    fun conflictKeys(blocks: List<TimeBlock>): Set<String> {
        val relevant = blocks.filter { !it.allDay && !it.isDone }.sortedBy { it.start }
        val conflicts = mutableSetOf<String>()
        for (i in relevant.indices) {
            for (j in i + 1 until relevant.size) {
                if (relevant[j].start >= relevant[i].end) break
                conflicts += relevant[i].key
                conflicts += relevant[j].key
            }
        }
        return conflicts
    }

    /** Blocks that would collide with a new item occupying [start, end). */
    fun findConflicts(
        blocks: List<TimeBlock>,
        start: Long,
        end: Long,
        ignoreEventId: Long? = null,
        ignoreTaskId: Long? = null
    ): List<TimeBlock> = blocks.filter { block ->
        !block.allDay && !block.isDone &&
            !(block.kind == BlockKind.EVENT && block.id == ignoreEventId) &&
            !(block.kind == BlockKind.TASK && block.id == ignoreTaskId) &&
            overlaps(block.start, block.end, start, end)
    }

    private fun EventEntity.toBlock(start: Long, end: Long, day: Long) = TimeBlock(
        kind = BlockKind.EVENT,
        id = id,
        occurrenceDay = day,
        title = title,
        category = category,
        color = color,
        start = start,
        end = end,
        allDay = allDay,
        isRepeating = repeatDaysMask != 0
    )
}

/** Picks which task the user should work on next. */
object TaskPrioritizer {
    private const val HOUR_MS = 3_600_000L

    fun score(task: TaskEntity, now: Long): Double {
        var score = task.priority * 30.0
        val deadline = task.deadline
        if (deadline != null) {
            val hoursLeft = (deadline - now).toDouble() / HOUR_MS
            score += when {
                hoursLeft < 0 -> 100.0 + minOf(-hoursLeft, 48.0)
                hoursLeft <= 24 -> 50.0 - hoursLeft
                hoursLeft <= 72 -> 20.0
                else -> 0.0
            }
        }
        return score
    }

    fun suggest(tasks: List<TaskEntity>, now: Long, limit: Int = 3): List<TaskEntity> =
        tasks.filter { !it.isDone }
            .sortedWith(compareByDescending<TaskEntity> { score(it, now) }.thenBy { it.deadline ?: Long.MAX_VALUE })
            .take(limit)
}
