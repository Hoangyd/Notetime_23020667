package com.example.notes.domain

data class Note(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val color: Int = 0,
    val labels: List<String> = emptyList(),
    val isChecklist: Boolean = false,
    val linkedTaskId: Long? = null,
    val linkedDate: Long? = null,
    val isDraft: Boolean = false
)
