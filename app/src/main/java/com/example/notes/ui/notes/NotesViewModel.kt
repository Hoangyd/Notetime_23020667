package com.example.notes.ui.notes

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.NoteItemEntity
import com.example.notes.domain.Note
import com.example.notes.ui.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Một chip lọc ở đầu danh sách: hoặc là một DANH MỤC ("c:Học tập") hoặc là một NHÃN ("l:vui").
 * [key] là mã duy nhất dùng để biết chip nào đang được chọn.
 */
data class NoteFilter(val key: String, val text: String, val isLabel: Boolean)

/** Mọi thứ màn danh sách ghi chú cần để vẽ. */
data class NotesState(
    val pinned: List<Note> = emptyList(), // ghi chú đã ghim (hiện trên cùng)
    val others: List<Note> = emptyList(), // ghi chú còn lại
    val items: Map<Long, List<NoteItemEntity>> = emptyMap(), // các mục checklist, theo id ghi chú
    val filters: List<NoteFilter> = emptyList(), // các chip lọc có thể chọn
    val selectedFilter: String? = null,
    val search: String = "",
    val showArchived: Boolean = false, // đang xem mục "Lưu trữ"
    val grid: Boolean = true, // true = dạng lưới 2 cột, false = danh sách 1 cột
    val draft: Note? = null, // bản nháp chưa hoàn tất (nếu có) để hiện banner khôi phục
    val loaded: Boolean = false
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

/**
 * ViewModel của màn danh sách ghi chú: tìm kiếm, lọc, ghim, lưu trữ, xoá, xuất/nhập JSON.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesViewModel(private val app: NotesApplication) : ViewModel() {
    private val repo = app.noteRepository

    // Các "ô nhớ" cho lựa chọn của người dùng.
    private val search = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow<String?>(null)
    private val showArchived = MutableStateFlow(false)
    private val grid = MutableStateFlow(true)

    /** Ghi chú lấy từ database, tải lại khi chữ tìm kiếm hoặc chế độ lưu trữ đổi. */
    private val notesFlow = combine(search.debounce(150), showArchived) { text, archived -> text to archived }
        .flatMapLatest { (text, archived) ->
            repo.observeNotes(text, includeArchived = archived, category = null)
        }

    /** Gom 4 lựa chọn của người dùng thành một đối tượng. */
    private data class UiPrefs(val search: String, val filter: String?, val showArchived: Boolean, val grid: Boolean)

    private val prefsFlow = combine(search, selectedFilter, showArchived, grid) { s, f, a, g ->
        UiPrefs(s, f, a, g)
    }

    /**
     * Trạng thái màn hình: ghép ghi chú + checklist + bản nháp + lựa chọn của người dùng,
     * rồi lọc / chia nhóm (ghim, khác) để màn hình chỉ việc vẽ.
     */
    val state: StateFlow<NotesState> = combine(
        notesFlow,
        repo.observeAllItems(),
        repo.observeLatestDraft(),
        prefsFlow
    ) { notes, items, draft, prefs ->
        // Ở chế độ "Lưu trữ" chỉ giữ ghi chú đã lưu trữ.
        val visible = if (prefs.showArchived) notes.filter { it.isArchived } else notes

        val filters = buildFilterChips(visible)

        // Nếu chip đang chọn không còn tồn tại (ví dụ xoá hết ghi chú của danh mục đó) thì coi như không chọn.
        val activeFilter = prefs.filter?.takeIf { key -> filters.any { it.key == key } }
        val filtered = visible.filter { note -> matchesFilter(note, activeFilter) }

        NotesState(
            pinned = filtered.filter { it.isPinned },
            others = filtered.filter { !it.isPinned },
            items = items,
            filters = filters,
            selectedFilter = activeFilter,
            search = prefs.search,
            showArchived = prefs.showArchived,
            grid = prefs.grid,
            draft = draft,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    /** Tạo danh sách chip lọc từ danh mục và nhãn có trong các ghi chú đang hiện. */
    private fun buildFilterChips(notes: List<Note>): List<NoteFilter> {
        val categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        val labels = notes.flatMap { it.labels }.distinct().sorted()
        return categories.map { NoteFilter("c:$it", it, isLabel = false) } +
            labels.map { NoteFilter("l:$it", it, isLabel = true) }
    }

    /** Ghi chú có khớp chip đang chọn không? (null = không lọc) */
    private fun matchesFilter(note: Note, filterKey: String?): Boolean = when {
        filterKey == null -> true
        filterKey.startsWith("c:") -> note.category == filterKey.removePrefix("c:")
        else -> filterKey.removePrefix("l:") in note.labels
    }

    // ----- Người dùng đổi lựa chọn -----

    fun setSearch(value: String) {
        search.value = value
    }

    /** Bấm chip: chọn nó; bấm lại chip đang chọn thì bỏ chọn. */
    fun toggleFilter(key: String) {
        selectedFilter.value = if (selectedFilter.value == key) null else key
    }

    fun toggleArchivedView() {
        showArchived.value = !showArchived.value
    }

    fun toggleGrid() {
        grid.value = !grid.value
    }

    // ----- Hành động trên ghi chú -----

    fun togglePinned(noteId: Long) {
        viewModelScope.launch { repo.togglePinned(noteId) }
    }

    /** Lưu trữ / khôi phục, kèm thông báo "Hoàn tác" (bấm sẽ đảo lại). */
    fun toggleArchived(note: Note) {
        viewModelScope.launch {
            repo.toggleArchived(note.id)
            app.messenger.post(
                UiMessage(
                    text = if (note.isArchived) "Đã khôi phục ghi chú" else "Đã lưu trữ ghi chú",
                    actionLabel = "Hoàn tác",
                    onAction = { viewModelScope.launch { repo.toggleArchived(note.id) } }
                )
            )
        }
    }

    /** Tick / bỏ tick một mục checklist ngay trên thẻ ghi chú. */
    fun toggleItem(item: NoteItemEntity) {
        viewModelScope.launch { repo.setItemDone(item.id, !item.isDone) }
    }

    /** Xoá ghi chú; thông báo "Hoàn tác" tạo lại ghi chú cùng các mục checklist. */
    fun delete(note: Note, items: List<NoteItemEntity>) {
        viewModelScope.launch {
            repo.deleteNote(note.id)
            app.messenger.post(
                UiMessage(
                    text = "Đã xoá ghi chú",
                    actionLabel = "Hoàn tác",
                    onAction = {
                        viewModelScope.launch {
                            val newId = repo.save(note.copy(id = 0L)) // id = 0 -> tạo mới
                            repo.replaceItems(newId, items)
                        }
                    }
                )
            )
        }
    }

    fun discardDraft(draft: Note) {
        viewModelScope.launch { repo.deleteNote(draft.id) }
    }

    // ----- Xuất / nhập JSON -----
    // `uri` là địa chỉ tệp do hệ thống cấp sau khi người dùng chọn tệp trong trình quản lý tệp.
    // Đọc/ghi tệp là việc chậm nên chạy ở luồng IO (Dispatchers.IO), không chặn giao diện.

    fun exportToUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = repo.exportAsJson()
                val stream = contentResolver.openOutputStream(uri) ?: error("Không mở được tệp đích")
                stream.bufferedWriter().use { it.write(json) }
                app.messenger.post("Đã xuất ghi chú")
            } catch (e: Throwable) {
                app.messenger.post("Xuất thất bại: ${e.message ?: "lỗi không xác định"}")
            }
        }
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openInputStream(uri) ?: error("Không mở được tệp nguồn")
                val json = stream.bufferedReader().use { it.readText() }
                val count = repo.importFromJson(json)
                app.messenger.post("Đã nhập $count ghi chú")
            } catch (e: Throwable) {
                app.messenger.post("Nhập thất bại: ${e.message ?: "tệp không hợp lệ"}")
            }
        }
    }
}
