package com.example.notes.data

import com.example.notes.domain.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class NoteRepository(
    private val noteDao: NoteDao
) {
    fun observeNotes(search: String, includeArchived: Boolean, category: String?): Flow<List<Note>> {
        val normalizedCategory = category?.takeIf { it.isNotBlank() }
        return noteDao.observeNotes(
            search = search.trim(),
            includeArchived = includeArchived,
            category = normalizedCategory
        ).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeCategories(): Flow<List<String>> {
        return noteDao.observeCategories()
    }

    fun observeNote(noteId: Long): Flow<Note?> {
        return noteDao.observeNoteById(noteId).map { entity ->
            entity?.toDomain()
        }
    }

    suspend fun saveNote(noteId: Long?, title: String, content: String, category: String) {
        val now = System.currentTimeMillis()
        val normalizedCategory = category.trim()

        if (noteId == null) {
            noteDao.upsert(
                NoteEntity(
                    title = title.trim(),
                    content = content.trim(),
                    category = normalizedCategory,
                    isPinned = false,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
            return
        }

        val existing = noteDao.getByIdNow(noteId)
        val updated = if (existing == null) {
            NoteEntity(
                id = noteId,
                title = title.trim(),
                content = content.trim(),
                category = normalizedCategory,
                isPinned = false,
                isArchived = false,
                createdAt = now,
                updatedAt = now
            )
        } else {
            existing.copy(
                title = title.trim(),
                content = content.trim(),
                category = normalizedCategory,
                updatedAt = now
            )
        }

        noteDao.upsert(updated)
    }

    suspend fun deleteNote(noteId: Long) {
        noteDao.deleteById(noteId)
    }

    suspend fun togglePinned(noteId: Long) {
        val existing = noteDao.getByIdNow(noteId) ?: return
        noteDao.setPinned(noteId, !existing.isPinned, System.currentTimeMillis())
    }

    suspend fun toggleArchived(noteId: Long) {
        val existing = noteDao.getByIdNow(noteId) ?: return
        noteDao.setArchived(noteId, !existing.isArchived, System.currentTimeMillis())
    }

    suspend fun exportAsJson(): String {
        val notes = noteDao.getAllNow()
        val array = JSONArray()

        for (note in notes) {
            val item = JSONObject()
                .put("id", note.id)
                .put("title", note.title)
                .put("content", note.content)
                .put("category", note.category)
                .put("isPinned", note.isPinned)
                .put("isArchived", note.isArchived)
                .put("createdAt", note.createdAt)
                .put("updatedAt", note.updatedAt)
            array.put(item)
        }

        return array.toString()
    }

    suspend fun importFromJson(payload: String): Int {
        val parsed = JSONArray(payload)
        val now = System.currentTimeMillis()
        val notes = mutableListOf<NoteEntity>()

        for (index in 0 until parsed.length()) {
            val item = parsed.optJSONObject(index) ?: continue
            notes += NoteEntity(
                id = item.optLong("id", 0L),
                title = item.optString("title", ""),
                content = item.optString("content", ""),
                category = item.optString("category", ""),
                isPinned = item.optBoolean("isPinned", false),
                isArchived = item.optBoolean("isArchived", false),
                createdAt = item.optLong("createdAt", now),
                updatedAt = item.optLong("updatedAt", now)
            )
        }

        if (notes.isNotEmpty()) {
            noteDao.upsertAll(notes)
        }

        return notes.size
    }
}
