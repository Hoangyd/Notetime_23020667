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

    fun observeNote(noteId: Long): Flow<Note?> {
        return noteDao.observeNoteById(noteId).map { entity ->
            entity?.toDomain()
        }
    }

    fun observeNotesForTask(taskId: Long): Flow<List<Note>> =
        noteDao.observeNotesForTask(taskId).map { list -> list.map { it.toDomain() } }

    fun observeNotesForDate(epochDay: Long): Flow<List<Note>> =
        noteDao.observeNotesForDate(epochDay).map { list -> list.map { it.toDomain() } }

    /** Saves [note] (create when id is 0 or unknown). Returns its id. */
    suspend fun save(note: Note): Long {
        val now = System.currentTimeMillis()
        val existing = if (note.id > 0) noteDao.getByIdNow(note.id) else null
        val entity = note.toEntity().copy(
            id = existing?.id ?: 0L,
            title = note.title.trim(),
            content = note.content.trim(),
            category = note.category.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return noteDao.upsert(entity).takeIf { it != UPDATED } ?: entity.id
    }

    /**
     * Autosave. Creates (or updates) a hidden draft row so a half-written note survives
     * process death. Call [save] with isDraft = false to publish it.
     */
    suspend fun saveDraft(note: Note): Long = save(note.copy(isDraft = true))

    fun observeLatestDraft(): Flow<Note?> = noteDao.observeLatestDraft().map { it?.toDomain() }

    suspend fun getNote(noteId: Long): Note? = noteDao.getByIdNow(noteId)?.toDomain()

    suspend fun getItems(noteId: Long): List<NoteItemEntity> = noteDao.getItemsNow(noteId)

    /** Checklist items of every note, keyed by note id (for list previews). */
    fun observeAllItems(): Flow<Map<Long, List<NoteItemEntity>>> =
        noteDao.observeAllItems().map { items -> items.groupBy { it.noteId } }

    /** Replaces the checklist of [noteId] with [items] (positions follow list order). */
    suspend fun replaceItems(noteId: Long, items: List<NoteItemEntity>) {
        noteDao.deleteItemsForNote(noteId)
        noteDao.insertItems(
            items.filter { it.text.isNotBlank() }
                .mapIndexed { index, item -> item.copy(id = 0L, noteId = noteId, position = index) }
        )
    }

    suspend fun setItemDone(itemId: Long, isDone: Boolean) = noteDao.setItemDone(itemId, isDone)

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
        val notes = noteDao.getAllNow().filter { !it.isDraft }
        val itemsByNote = noteDao.getAllItemsNow().groupBy { it.noteId }
        val array = JSONArray()

        for (note in notes) {
            val items = JSONArray()
            itemsByNote[note.id].orEmpty().forEach { item ->
                items.put(
                    JSONObject()
                        .put("text", item.text)
                        .put("isDone", item.isDone)
                        .put("position", item.position)
                )
            }
            val item = JSONObject()
                .put("id", note.id)
                .put("title", note.title)
                .put("content", note.content)
                .put("category", note.category)
                .put("isPinned", note.isPinned)
                .put("isArchived", note.isArchived)
                .put("createdAt", note.createdAt)
                .put("updatedAt", note.updatedAt)
                .put("color", note.color)
                .put("labels", note.labels)
                .put("isChecklist", note.isChecklist)
                .put("linkedTaskId", note.linkedTaskId ?: JSONObject.NULL)
                .put("linkedDate", note.linkedDate ?: JSONObject.NULL)
                .put("items", items)
            array.put(item)
        }

        return array.toString()
    }

    suspend fun importFromJson(payload: String): Int {
        val parsed = JSONArray(payload)
        val now = System.currentTimeMillis()
        var imported = 0

        for (index in 0 until parsed.length()) {
            val item = parsed.optJSONObject(index) ?: continue
            val importedId = item.optLong("id", 0L)
            val rowId = noteDao.upsert(
                NoteEntity(
                    id = importedId,
                    title = item.optString("title", ""),
                    content = item.optString("content", ""),
                    category = item.optString("category", ""),
                    isPinned = item.optBoolean("isPinned", false),
                    isArchived = item.optBoolean("isArchived", false),
                    createdAt = item.optLong("createdAt", now),
                    updatedAt = item.optLong("updatedAt", now),
                    color = item.optInt("color", 0),
                    labels = item.optString("labels", ""),
                    isChecklist = item.optBoolean("isChecklist", false),
                    linkedTaskId = if (item.isNull("linkedTaskId")) null else item.optLong("linkedTaskId"),
                    linkedDate = if (item.isNull("linkedDate")) null else item.optLong("linkedDate")
                )
            )
            val noteId = rowId.takeIf { it != UPDATED } ?: importedId

            val itemsJson = item.optJSONArray("items")
            if (itemsJson != null) {
                val items = (0 until itemsJson.length()).mapNotNull { i ->
                    val obj = itemsJson.optJSONObject(i) ?: return@mapNotNull null
                    NoteItemEntity(
                        noteId = noteId,
                        text = obj.optString("text", ""),
                        isDone = obj.optBoolean("isDone", false),
                        position = obj.optInt("position", i)
                    )
                }
                replaceItems(noteId, items)
            }
            imported++
        }

        return imported
    }
}
