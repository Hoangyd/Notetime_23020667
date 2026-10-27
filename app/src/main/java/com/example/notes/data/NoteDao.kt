package com.example.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE isDraft = 0
        AND (:includeArchived = 1 OR isArchived = 0)
        AND (:category IS NULL OR category = :category)
        AND (
            title LIKE '%' || :search || '%'
            OR content LIKE '%' || :search || '%'
            OR category LIKE '%' || :search || '%'
            OR labels LIKE '%' || :search || '%'
        )
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun observeNotes(search: String, includeArchived: Boolean, category: String?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeNoteById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getByIdNow(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE linkedTaskId = :taskId AND isDraft = 0 ORDER BY updatedAt DESC")
    fun observeNotesForTask(taskId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE linkedDate = :epochDay AND isDraft = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotesForDate(epochDay: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDraft = 1 ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestDraft(): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<NoteEntity>

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE notes
        SET isArchived = :isArchived,
            isPinned = CASE WHEN :isArchived = 1 THEN 0 ELSE isPinned END,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET linkedTaskId = NULL WHERE linkedTaskId = :taskId")
    suspend fun clearTaskLink(taskId: Long)

    // Checklist items

    @Query("SELECT * FROM note_items ORDER BY noteId, position, id")
    fun observeAllItems(): Flow<List<NoteItemEntity>>

    @Query("SELECT * FROM note_items WHERE noteId = :noteId ORDER BY position, id")
    suspend fun getItemsNow(noteId: Long): List<NoteItemEntity>

    @Query("SELECT * FROM note_items ORDER BY noteId, position, id")
    suspend fun getAllItemsNow(): List<NoteItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<NoteItemEntity>)

    @Query("DELETE FROM note_items WHERE noteId = :noteId")
    suspend fun deleteItemsForNote(noteId: Long)

    @Query("UPDATE note_items SET isDone = :isDone WHERE id = :itemId")
    suspend fun setItemDone(itemId: Long, isDone: Boolean)
}
