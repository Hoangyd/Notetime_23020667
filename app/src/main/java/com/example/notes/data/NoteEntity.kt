package com.example.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notes.domain.Note

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    val category: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** ARGB colour; 0 = default. */
    val color: Int = 0,
    /** Comma-separated labels. */
    val labels: String = "",
    val isChecklist: Boolean = false,
    val linkedTaskId: Long? = null,
    /** Epoch day the note is attached to, or null. */
    val linkedDate: Long? = null,
    /** Autosaved but not yet confirmed by the user; hidden from lists. */
    val isDraft: Boolean = false
)

fun NoteEntity.toDomain(): Note {
    return Note(
        id = id,
        title = title,
        content = content,
        category = category,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        color = color,
        labels = labels.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        isChecklist = isChecklist,
        linkedTaskId = linkedTaskId,
        linkedDate = linkedDate,
        isDraft = isDraft
    )
}

fun Note.toEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        title = title,
        content = content,
        category = category,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        color = color,
        labels = labels.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
        isChecklist = isChecklist,
        linkedTaskId = linkedTaskId,
        linkedDate = linkedDate,
        isDraft = isDraft
    )
}
