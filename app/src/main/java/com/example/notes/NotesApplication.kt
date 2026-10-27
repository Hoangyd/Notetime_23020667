package com.example.notes

import android.app.Application
import com.example.notes.data.CalendarRepository
import com.example.notes.data.HabitRepository
import com.example.notes.data.InsightsRepository
import com.example.notes.data.NoteRepository
import com.example.notes.data.NotesDatabase
import com.example.notes.data.TaskRepository
import com.example.notes.ui.UiMessenger

class NotesApplication : Application() {
    val database: NotesDatabase by lazy { NotesDatabase.getDatabase(this) }
    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }
    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao(), database.noteDao()) }
    val calendarRepository: CalendarRepository by lazy {
        CalendarRepository(database.eventDao(), database.taskDao())
    }
    val habitRepository: HabitRepository by lazy { HabitRepository(database.habitDao()) }
    val insightsRepository: InsightsRepository by lazy {
        InsightsRepository(
            database.insightsDao(),
            database.taskDao(),
            database.eventDao(),
            database.habitDao()
        )
    }
    val messenger = UiMessenger()
}
