package com.example.notes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesMigrationTest {

    @Test
    fun migrate1To2_addsCategoryPinnedAndArchivedColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test.db"
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

        val migratedRoomDatabase = Room.databaseBuilder(context, NotesDatabase::class.java, dbName)
            .addMigrations(NotesDatabase.MIGRATION_1_2)
            .build()
        migratedRoomDatabase.openHelper.writableDatabase.close()
        migratedRoomDatabase.close()

        val migrated = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        val rowCursor = migrated.rawQuery(
            "SELECT category, isPinned, isArchived FROM notes LIMIT 1",
            null
        )

        assertTrue(rowCursor.moveToFirst())
        assertEquals("", rowCursor.getString(0))
        assertEquals(0, rowCursor.getInt(1))
        assertEquals(0, rowCursor.getInt(2))

        rowCursor.close()
        migrated.close()
        context.deleteDatabase(dbName)
    }
}
