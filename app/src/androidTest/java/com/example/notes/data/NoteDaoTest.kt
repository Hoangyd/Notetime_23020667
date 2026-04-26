package com.example.notes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var database: NotesDatabase
    private lateinit var noteDao: NoteDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = database.noteDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertThenRead_returnsSavedNote() = runBlocking {
        val now = System.currentTimeMillis()
        noteDao.upsert(
            NoteEntity(
                title = "Grocery",
                content = "Milk and bread",
                category = "Home",
                isPinned = false,
                isArchived = false,
                createdAt = now,
                updatedAt = now
            )
        )

        val notes = noteDao.observeNotes(
            search = "",
            includeArchived = false,
            category = null
        ).first()

        assertEquals(1, notes.size)
        assertEquals("Grocery", notes.first().title)
        assertEquals("Home", notes.first().category)
    }

    @Test
    fun archiveFilter_hidesArchivedByDefault() = runBlocking {
        val now = System.currentTimeMillis()
        noteDao.upsert(
            NoteEntity(
                title = "Visible",
                content = "Active note",
                category = "Work",
                isPinned = false,
                isArchived = false,
                createdAt = now,
                updatedAt = now
            )
        )
        noteDao.upsert(
            NoteEntity(
                title = "Hidden",
                content = "Archived note",
                category = "Work",
                isPinned = false,
                isArchived = true,
                createdAt = now,
                updatedAt = now
            )
        )

        val activeOnly = noteDao.observeNotes(
            search = "",
            includeArchived = false,
            category = null
        ).first()
        val withArchived = noteDao.observeNotes(
            search = "",
            includeArchived = true,
            category = null
        ).first()

        assertEquals(1, activeOnly.size)
        assertFalse(activeOnly.first().isArchived)
        assertEquals(2, withArchived.size)
        assertTrue(withArchived.any { it.isArchived })
    }
}
