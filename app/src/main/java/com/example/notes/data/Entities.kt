package com.example.notes.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.notes.domain.Category
import com.example.notes.domain.EventType
import com.example.notes.domain.Priority

@Entity(
    tableName = "tasks",
    indices = [Index("deadline"), Index("isDone"), Index("scheduledStart")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val category: String = Category.PERSONAL.name,
    val priority: Int = Priority.MEDIUM.level,
    /** Epoch millis; null = no deadline. */
    val deadline: Long? = null,
    val estimatedMinutes: Int? = null,
    /** Time-block on the calendar; both null when the task is not scheduled. */
    val scheduledStart: Long? = null,
    val scheduledEnd: Long? = null,
    val isDone: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

val TaskEntity.categoryEnum: Category get() = Category.fromKey(category)
val TaskEntity.priorityEnum: Priority get() = Priority.fromLevel(priority)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val title: String,
    val isDone: Boolean = false,
    val position: Int = 0
)

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>
) {
    /** 0f..1f. Driven by subtasks when present, otherwise by the task's own done flag. */
    val progress: Float
        get() = if (subtasks.isEmpty()) {
            if (task.isDone) 1f else 0f
        } else {
            subtasks.count { it.isDone }.toFloat() / subtasks.size
        }
}

@Entity(
    tableName = "events",
    indices = [Index("startAt"), Index("endAt")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val type: String = EventType.OTHER.name,
    val category: String = Category.PERSONAL.name,
    /** ARGB colour override; 0 = use the category colour. */
    val color: Int = 0,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
    /** Weekly repeat days (bit 0 = Monday ... bit 6 = Sunday); 0 = does not repeat. */
    val repeatDaysMask: Int = 0,
    /** Last moment (epoch millis) a repeating event may start; null = forever. */
    val repeatUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

val EventEntity.typeEnum: EventType get() = EventType.fromKey(type)
val EventEntity.categoryEnum: Category get() = Category.fromKey(category)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String = Category.PERSONAL.name,
    val color: Int = 0,
    val icon: String = "",
    /** Scheduled weekdays (bit 0 = Monday ... bit 6 = Sunday). Default: every day. */
    val daysMask: Int = 0b1111111,
    /** Minutes after midnight for a reminder; null = none. */
    val reminderMinute: Int? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("epochDay")]
)
data class HabitLogEntity(
    val habitId: Long,
    val epochDay: Long,
    val completedAt: Long = System.currentTimeMillis()
)

/** Actual time spent, used to compare against the plan. */
@Entity(
    tableName = "time_logs",
    indices = [Index("startAt"), Index("taskId")]
)
data class TimeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long? = null,
    val category: String = Category.PERSONAL.name,
    val startAt: Long,
    val endAt: Long,
    val note: String = ""
)

@Entity(
    tableName = "note_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class NoteItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val text: String,
    val isDone: Boolean = false,
    val position: Int = 0
)

/** What a Room `@Upsert` returns when it updated an existing row instead of inserting one. */
internal const val UPDATED = -1L

data class CategoryTotal(val category: String, val totalMs: Long)

data class DayCount(val epochDay: Long, val count: Int)
