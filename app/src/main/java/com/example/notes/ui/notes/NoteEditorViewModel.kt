package com.example.notes.ui.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.NoteItemEntity
import com.example.notes.data.TaskEntity
import com.example.notes.domain.Note
import com.example.notes.domain.TaskFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Một dòng của checklist trong form. `key` chỉ để Compose phân biệt các dòng, không lưu vào database. */
data class ChecklistRow(val key: Long, val text: String, val done: Boolean)

/** Nội dung ghi chú đang soạn (bản nháp trong bộ nhớ). */
data class NoteForm(
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val labels: List<String> = emptyList(),
    val color: Int = 0, // 0 = màu mặc định
    val pinned: Boolean = false,
    val checklist: Boolean = false, // true = ghi chú dạng checklist (dùng `rows`), false = văn bản (dùng `content`)
    val rows: List<ChecklistRow> = emptyList(),
    val linkedTaskId: Long? = null,
    val linkedDate: Long? = null
) {
    /** Form có trống hoàn toàn không? (để quyết định có cần lưu hay không) */
    val isBlank: Boolean
        get() = title.isBlank() && content.isBlank() && rows.all { it.text.isBlank() }
}

/**
 * ViewModel của form soạn ghi chú. Điểm đặc biệt: TỰ LƯU NHÁP.
 *
 * Cách hoạt động:
 *  - Mỗi lần người dùng sửa form → hẹn giờ 0,7 giây rồi lưu vào database ([scheduleAutosave]).
 *    Nếu người dùng gõ tiếp trước khi hết giờ, hẹn giờ cũ bị huỷ và hẹn lại.
 *  - Ghi chú MỚI được lưu với cờ "nháp" (isDraft = true) nên chưa hiện trong danh sách chính,
 *    nhưng nếu app bị tắt đột ngột thì lần sau vẫn khôi phục được.
 *  - Khi bấm "Xong"/Quay lại → [finish] bỏ cờ nháp (ghi chú chính thức), hoặc xoá nếu form trống.
 */
class NoteEditorViewModel(
    private val app: NotesApplication,
    private val noteId: Long,
    presetTaskId: Long?,
    presetDate: Long?
) : ViewModel() {
    private val repo = app.noteRepository

    /** Form hiện tại. Giao diện đọc, chỉ ViewModel được ghi. */
    var form by mutableStateOf(NoteForm(linkedTaskId = presetTaskId, linkedDate = presetDate))
        private set

    /** false khi đang tải ghi chú cần sửa. */
    var loaded by mutableStateOf(noteId <= 0L)
        private set

    /** true nếu vừa tự lưu xong và chưa có thay đổi mới (để hiện chữ "Đã lưu nháp"). */
    var saved by mutableStateOf(false)
        private set

    /** Dòng checklist cần nhận con trỏ (vừa thêm mới). */
    var focusKey by mutableStateOf<Long?>(null)
        private set

    // id của ghi chú trong database. 0 = chưa lưu lần nào (sẽ được cấp id ở lần tự lưu đầu tiên).
    private var currentId = noteId.coerceAtLeast(0L)
    private var isDraft = true // ghi chú đang ở trạng thái nháp?
    private var isArchived = false // giữ nguyên trạng thái lưu trữ của ghi chú đang sửa
    private var nextRowKey = 1L
    private var autosaveJob: Job? = null
    private val saveMutex = Mutex() // đảm bảo không có hai lần lưu chạy cùng lúc

    /** Các công việc chưa xong, để chọn liên kết. */
    val tasks: StateFlow<List<TaskEntity>> = app.taskRepository
        .observeTasks(TaskFilter.ALL)
        .map { list -> list.map { it.task }.filter { !it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Đang sửa ghi chú có sẵn: tải từ database vào form.
        if (noteId > 0L) {
            viewModelScope.launch {
                val note = repo.getNote(noteId)
                if (note != null) {
                    val items = repo.getItems(noteId)
                    isArchived = note.isArchived
                    isDraft = note.isDraft
                    form = NoteForm(
                        title = note.title,
                        content = note.content,
                        category = note.category,
                        labels = note.labels,
                        color = note.color,
                        pinned = note.isPinned,
                        checklist = note.isChecklist,
                        rows = items.map { ChecklistRow(nextRowKey++, it.text, it.isDone) },
                        linkedTaskId = note.linkedTaskId,
                        linkedDate = note.linkedDate
                    )
                }
                loaded = true
            }
        }
    }

    // ----- Sửa form -----

    /** Cập nhật form rồi hẹn giờ tự lưu. Dùng: update { it.copy(title = "...") } */
    fun update(transform: (NoteForm) -> NoteForm) {
        form = transform(form)
        scheduleAutosave()
    }

    /** Chuyển giữa ghi chú văn bản và checklist, giữ lại nội dung đã gõ. */
    fun toggleChecklist() {
        val current = form
        form = if (!current.checklist) {
            // Văn bản -> checklist: mỗi dòng không trống thành một mục.
            val rows = current.content.lines()
                .filter { it.isNotBlank() }
                .map { ChecklistRow(nextRowKey++, it.trim(), done = false) }
            val finalRows = rows.ifEmpty { listOf(ChecklistRow(nextRowKey++, "", done = false)) }
            focusKey = finalRows.last().key
            current.copy(checklist = true, content = "", rows = finalRows)
        } else {
            // Checklist -> văn bản: nối các mục thành các dòng.
            current.copy(
                checklist = false,
                content = current.rows.filter { it.text.isNotBlank() }.joinToString("\n") { it.text },
                rows = emptyList()
            )
        }
        scheduleAutosave()
    }

    fun updateRow(key: Long, text: String) = update { current ->
        current.copy(rows = current.rows.map { if (it.key == key) it.copy(text = text) else it })
    }

    fun toggleRow(key: Long) = update { current ->
        current.copy(rows = current.rows.map { if (it.key == key) it.copy(done = !it.done) else it })
    }

    /** Thêm một dòng trống ngay sau dòng [key] (hoặc cuối danh sách nếu key = null) và đặt con trỏ vào đó. */
    fun addRowAfter(key: Long?) = update { current ->
        val newRow = ChecklistRow(nextRowKey++, "", done = false)
        focusKey = newRow.key
        val index = current.rows.indexOfFirst { it.key == key }
        val rows = if (index < 0) {
            current.rows + newRow
        } else {
            current.rows.toMutableList().apply { add(index + 1, newRow) }
        }
        current.copy(rows = rows)
    }

    fun removeRow(key: Long) = update { current ->
        val rows = current.rows.filter { it.key != key }
        // Luôn giữ ít nhất một dòng để còn chỗ gõ.
        current.copy(rows = rows.ifEmpty { listOf(ChecklistRow(nextRowKey++, "", done = false)) })
    }

    fun addLabel(raw: String) {
        val label = raw.trim().trimStart('#')
        if (label.isBlank() || label in form.labels) return
        update { it.copy(labels = it.labels + label) }
    }

    fun removeLabel(label: String) = update { it.copy(labels = it.labels - label) }

    // ----- Tự lưu -----

    private fun scheduleAutosave() {
        saved = false
        autosaveJob?.cancel() // huỷ hẹn giờ cũ nếu người dùng vẫn đang gõ
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            persist()
            saved = true
        }
    }

    /** Ghi form hiện tại vào database (tạo mới ở lần đầu, cập nhật ở các lần sau). */
    private suspend fun persist() = saveMutex.withLock {
        val f = form
        // Chưa gõ gì và chưa từng lưu thì không cần tạo ghi chú rỗng.
        if (f.isBlank && currentId == 0L) return@withLock

        val savedId = repo.save(
            Note(
                id = currentId,
                title = f.title,
                content = if (f.checklist) "" else f.content,
                category = f.category,
                isPinned = f.pinned,
                isArchived = isArchived,
                color = f.color,
                labels = f.labels,
                isChecklist = f.checklist,
                linkedTaskId = f.linkedTaskId,
                linkedDate = f.linkedDate,
                isDraft = isDraft
            )
        )
        currentId = savedId

        // Ghi các mục checklist (bỏ dòng trống); ghi chú văn bản thì không có mục nào.
        val items = if (f.checklist) {
            f.rows.filter { it.text.isNotBlank() }
                .map { NoteItemEntity(noteId = savedId, text = it.text, isDone = it.done) }
        } else {
            emptyList()
        }
        repo.replaceItems(savedId, items)
    }

    /**
     * Kết thúc soạn: lưu thành ghi chú chính thức (hoặc xoá nếu trống), rồi gọi [onDone] để quay lại.
     */
    fun finish(onDone: () -> Unit) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            if (form.isBlank) {
                if (currentId > 0L) repo.deleteNote(currentId)
            } else {
                isDraft = false // từ giờ là ghi chú thật, hiện trong danh sách
                persist()
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            if (currentId > 0L) repo.deleteNote(currentId)
            app.messenger.post("Đã xoá ghi chú")
            onDone()
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 700L
    }
}
