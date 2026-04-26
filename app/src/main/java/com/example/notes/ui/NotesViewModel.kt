package com.example.notes.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.data.NoteRepository
import com.example.notes.domain.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NoteRepository
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val includeArchived = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val notesFlow = combine(
        searchQuery,
        selectedCategory,
        includeArchived
    ) { search, category, archived ->
        QueryFilter(search = search, category = category, includeArchived = archived)
    }.flatMapLatest { filter ->
        repository.observeNotes(
            search = filter.search,
            includeArchived = filter.includeArchived,
            category = filter.category
        )
    }

    val uiState: StateFlow<NotesUiState> = combine(
        notesFlow,
        repository.observeCategories(),
        searchQuery,
        selectedCategory,
        includeArchived
    ) { notes, categories, search, category, archived ->
        NotesUiState(
            notes = notes,
            categories = categories,
            searchQuery = search,
            selectedCategory = category,
            includeArchived = archived
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotesUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateSelectedCategory(category: String?) {
        selectedCategory.value = category
    }

    fun toggleArchiveFilter() {
        includeArchived.value = !includeArchived.value
    }

    fun observeNote(noteId: Long): Flow<Note?> {
        return repository.observeNote(noteId)
    }

    fun saveNote(
        noteId: Long?,
        title: String,
        content: String,
        category: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (title.isBlank() && content.isBlank()) {
                _messages.emit("Add a title or body before saving.")
                onResult(false)
                return@launch
            }

            repository.saveNote(
                noteId = noteId,
                title = title,
                content = content,
                category = category
            )

            _messages.emit(if (noteId == null) "Note created." else "Note updated.")
            onResult(true)
        }
    }

    fun togglePinned(noteId: Long) {
        viewModelScope.launch {
            repository.togglePinned(noteId)
        }
    }

    fun toggleArchived(noteId: Long) {
        viewModelScope.launch {
            repository.toggleArchived(noteId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            _messages.emit("Note deleted.")
        }
    }

    fun exportToUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = repository.exportAsJson()
                val stream = contentResolver.openOutputStream(uri)
                    ?: error("Unable to open destination")
                stream.bufferedWriter().use { it.write(payload) }
                _messages.emit("Notes exported.")
            } catch (throwable: Throwable) {
                _messages.emit("Export failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: error("Unable to open source")
                val payload = input.bufferedReader().use { it.readText() }
                val count = repository.importFromJson(payload)
                _messages.emit("Imported $count notes.")
            } catch (throwable: Throwable) {
                _messages.emit("Import failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    private data class QueryFilter(
        val search: String,
        val category: String?,
        val includeArchived: Boolean
    )
}
