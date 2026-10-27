package com.example.notes.domain

enum class Category {
    STUDY, WORK, PERSONAL, HEALTH;

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.name == key } ?: PERSONAL
    }
}

enum class Priority(val level: Int) {
    LOW(0), MEDIUM(1), HIGH(2);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

enum class EventType {
    CLASS, WORK, APPOINTMENT, OTHER;

    companion object {
        fun fromKey(key: String?): EventType = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

/** Task list filters. [mode] is the value understood by the SQL query in TaskDao. */
enum class TaskFilter(val mode: Int) {
    ALL(0), TODAY(1), UPCOMING(2), OVERDUE(3), COMPLETED(4)
}

enum class DayPart {
    MORNING, AFTERNOON, EVENING;

    companion object {
        fun ofHour(hour: Int): DayPart = when {
            hour < 12 -> MORNING
            hour < 18 -> AFTERNOON
            else -> EVENING
        }
    }
}
