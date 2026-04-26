package com.example.notes.ui

import com.example.notes.domain.Note

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val categories: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val includeArchived: Boolean = false
)
