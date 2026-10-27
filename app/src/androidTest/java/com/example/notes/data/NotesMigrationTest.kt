package com.example.notes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesMigrationTest {

    private fun createVersion1Db(context: Context, dbName: String): String {
        context.deleteDatabase(dbName)
        val dbPath = context.getDatabasePath(dbName).path
        val version1Db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        version1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        version1Db.execSQL(
            "INSERT INTO notes (title, content, createdAt, updatedAt) VALUES ('Old', 'Version1', 1, 1)"
        )
        version1Db.version = 1
        version1Db.close()
        return dbPath
    }

    @Test
    fun migrate1To5_keepsOldNotesAndAddsNewColumnsAndTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test.db"
        val dbPath = createVersion1Db(context, dbName)

        // Opening through Room also validates the migrated schema against the entities.
        val migratedRoomDatabase = Room.databaseBuilder(context, NotesDatabase::class.java, dbName)
            .addMigrations(NotesDatabase.MIGRATION_1_2, NotesDatabase.MIGRATION_2_3, NotesDatabase.MIGRATION_3_4, NotesDatabase.MIGRATION_4_5)
            .build()
        migratedRoomDatabase.openHelper.writableDatabase.close()
        migratedRoomDatabase.close()

        val migrated = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        val rowCursor = migrated.rawQuery(
            "SELECT title, category, isPinned, isArchived, color, labels, isChecklist, isDraft FROM notes LIMIT 1",
            null
        )
        assertTrue(rowCursor.moveToFirst())
        assertEquals("Old", rowCursor.getString(0))
        assertEquals("", rowCursor.getString(1))
        assertEquals(0, rowCursor.getInt(2))
        assertEquals(0, rowCursor.getInt(3))
        assertEquals(0, rowCursor.getInt(4))
        assertEquals("", rowCursor.getString(5))
        assertEquals(0, rowCursor.getInt(6))
        assertEquals(0, rowCursor.getInt(7))
        rowCursor.close()

        val tables = mutableSetOf<String>()
        migrated.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        assertTrue(
            tables.containsAll(
                listOf(
                    "notes", "note_items", "tasks", "subtasks", "events",
                    "habits", "habit_logs", "time_logs"
                )
            )
        )

        assertFalse("achievements" in tables)

        migrated.close()
        context.deleteDatabase(dbName)
    }
}
