package com.example.notes

import android.app.Application
import com.example.notes.data.NoteRepository
import com.example.notes.data.NotesDatabase

class NotesApplication : Application() {
    val database: NotesDatabase by lazy { NotesDatabase.getDatabase(this) }
    val repository: NoteRepository by lazy { NoteRepository(database.noteDao()) }
}
