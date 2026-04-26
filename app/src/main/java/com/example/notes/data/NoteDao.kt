package com.example.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE (:includeArchived = 1 OR isArchived = 0)
        AND (:category IS NULL OR category = :category)
        AND (
            title LIKE '%' || :search || '%'
            OR content LIKE '%' || :search || '%'
            OR category LIKE '%' || :search || '%'
        )
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun observeNotes(search: String, includeArchived: Boolean, category: String?): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT category FROM notes WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeNoteById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getByIdNow(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

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
}
