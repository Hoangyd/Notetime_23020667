package com.example.notes.ui.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.TaskEntity
import com.example.notes.data.categoryEnum
import com.example.notes.data.priorityEnum
import com.example.notes.domain.Category
import com.example.notes.domain.Note
import com.example.notes.domain.Priority
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Nội dung đang được người dùng nhập trong form. Đây là bản "nháp" – chưa lưu vào database
 * cho tới khi bấm Lưu. Thời gian được tách thành "ngày" + "số phút kể từ nửa đêm" cho dễ chọn.
 */
data class TaskForm(
    val title: String = "",
    val description: String = "",
    val category: Category = Category.PERSONAL,
    val priority: Priority = Priority.MEDIUM,

    // Hạn chót: ngày (null = không có hạn) và giờ trong ngày (mặc định 18:00).
    val deadlineDay: Long? = null,
    val deadlineMinutes: Int = 18 * 60,

    val estimatedMinutes: Int? = null, // thời lượng dự kiến; null = chưa đặt

    // "Xếp vào lịch": đặt khối giờ cụ thể trên lịch.
    val scheduleEnabled: Boolean = false,
    val scheduleDay: Long = TimeUtils.today(),
    val startMinutes: Int = 9 * 60,
    val endMinutes: Int = 10 * 60
)

/** Một việc con để hiển thị. `id` âm nghĩa là việc con tạm (chưa lưu) của một công việc mới. */
data class SubtaskItem(val id: Long, val title: String, val done: Boolean)

/**
 * ViewModel của form soạn công việc. Dùng cho cả TẠO MỚI (taskId = 0) và SỬA (taskId > 0).
 */
class TaskEditorViewModel(
    private val app: NotesApplication,
    private val taskId: Long
) : ViewModel() {

    val isNew: Boolean = taskId <= 0L

    // `form` là trạng thái mà giao diện đọc. `private set`: chỉ ViewModel được ghi, giao diện chỉ đọc.
    var form by mutableStateOf(TaskForm())
        private set

    /** false khi đang tải công việc cần sửa từ database. */
    var loaded by mutableStateOf(isNew)
        private set

    /** Bản gốc của công việc đang sửa, để giữ lại các trường không có trong form (ví dụ ngày tạo). */
    private var original: TaskEntity? = null

    // ----- Việc con -----
    // Công việc MỚI chưa có id trong database, nên việc con được giữ tạm trong bộ nhớ và chỉ ghi khi bấm Lưu.
    // Công việc ĐANG SỬA thì thêm / xoá / tick việc con ghi thẳng vào database.
    private val temporarySubtasks = MutableStateFlow<List<SubtaskItem>>(emptyList())
    private var nextTemporaryId = -1L

    val subtasks: StateFlow<List<SubtaskItem>> = (
        if (isNew) {
            temporarySubtasks
        } else {
            app.taskRepository.observeTask(taskId).map { item ->
                item?.subtasks.orEmpty().map { SubtaskItem(it.id, it.title, it.isDone) }
            }
        }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Các ghi chú đã liên kết với công việc này (công việc mới thì chưa có). */
    val linkedNotes: Flow<List<Note>> =
        if (isNew) emptyFlow() else app.noteRepository.observeNotesForTask(taskId)

    init {
        // Đang sửa: tải dữ liệu hiện có rồi đổ vào form.
        if (!isNew) {
            viewModelScope.launch {
                val item = app.taskRepository.observeTask(taskId).first()
                if (item != null) {
                    original = item.task
                    form = item.task.toForm()
                }
                loaded = true
            }
        }
    }

    // ----- Sửa form -----

    /** Cách dùng: update { it.copy(title = "...") } – tạo bản sao form với một trường đã đổi. */
    fun update(transform: (TaskForm) -> TaskForm) {
        form = transform(form)
    }

    fun setDeadlineDay(day: Long?) = update { it.copy(deadlineDay = day) }

    /** Bật/tắt "Xếp vào lịch". Khi bật, gợi ý sẵn ngày và giờ kết thúc theo thời lượng dự kiến. */
    fun setScheduleEnabled(enabled: Boolean) = update { current ->
        if (!enabled) {
            current.copy(scheduleEnabled = false)
        } else {
            val duration = current.estimatedMinutes ?: 60
            current.copy(
                scheduleEnabled = true,
                scheduleDay = current.deadlineDay ?: current.scheduleDay,
                endMinutes = (current.startMinutes + duration).coerceAtMost(24 * 60 - 1)
            )
        }
    }

    // ----- Việc con -----

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        if (isNew) {
            temporarySubtasks.value += SubtaskItem(nextTemporaryId--, title.trim(), done = false)
        } else {
            viewModelScope.launch { app.taskRepository.addSubtask(taskId, title) }
        }
    }

    fun toggleSubtask(item: SubtaskItem) {
        if (isNew) {
            temporarySubtasks.value = temporarySubtasks.value.map {
                if (it.id == item.id) it.copy(done = !it.done) else it
            }
        } else {
            viewModelScope.launch {
                app.taskRepository.toggleSubtask(item.id)
            }
        }
    }

    fun removeSubtask(item: SubtaskItem) {
        if (isNew) {
            temporarySubtasks.value = temporarySubtasks.value.filter { it.id != item.id }
        } else {
            viewModelScope.launch { app.taskRepository.deleteSubtask(item.id) }
        }
    }

    // ----- Lưu / Xoá -----

    /** Kiểm tra form, lưu vào database, rồi gọi [onSaved] (thường là quay lại màn trước). */
    fun save(onSaved: () -> Unit) {
        val f = form
        if (f.title.isBlank()) {
            app.messenger.post("Hãy nhập tên công việc")
            return
        }
        if (f.scheduleEnabled && f.endMinutes <= f.startMinutes) {
            app.messenger.post("Giờ kết thúc phải sau giờ bắt đầu")
            return
        }

        viewModelScope.launch {
            // Đổi "ngày + phút" của form thành mili-giây để lưu.
            val scheduledStart = if (f.scheduleEnabled) TimeUtils.at(f.scheduleDay, f.startMinutes) else null
            val scheduledEnd = if (f.scheduleEnabled) TimeUtils.at(f.scheduleDay, f.endMinutes) else null

            // Lấy bản gốc (nếu đang sửa) và ghi đè các trường người dùng đã nhập.
            val entity = (original ?: TaskEntity(title = f.title)).copy(
                id = if (isNew) 0L else taskId,
                title = f.title,
                description = f.description.trim(),
                category = f.category.name,
                priority = f.priority.level,
                deadline = f.deadlineDay?.let { TimeUtils.at(it, f.deadlineMinutes) },
                estimatedMinutes = f.estimatedMinutes,
                scheduledStart = scheduledStart,
                scheduledEnd = scheduledEnd
            )

            val savedId = try {
                app.taskRepository.saveTask(entity)
            } catch (e: IllegalArgumentException) {
                app.messenger.post(e.message ?: "Không thể lưu công việc")
                return@launch
            }

            // Công việc mới: giờ đã có id, ghi các việc con tạm vào database.
            if (isNew) {
                for (sub in temporarySubtasks.value) {
                    val subtaskId = app.taskRepository.addSubtask(savedId, sub.title)
                    if (sub.done) app.taskRepository.toggleSubtask(subtaskId)
                }
            }

            // Nếu đã xếp giờ, cảnh báo khi trùng với lịch khác (vẫn cho lưu).
            if (scheduledStart != null && scheduledEnd != null) {
                val conflicts = app.calendarRepository.findConflicts(scheduledStart, scheduledEnd, ignoreTaskId = savedId)
                if (conflicts.isNotEmpty()) {
                    app.messenger.post("Trùng giờ với: ${conflicts.joinToString { it.title }}")
                }
            }

            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (isNew) return
        viewModelScope.launch {
            app.taskRepository.deleteTask(taskId)
            app.messenger.post("Đã xoá công việc")
            onDeleted()
        }
    }

    /** Đổi công việc trong database thành dữ liệu form (ngược lại với `save`). */
    private fun TaskEntity.toForm(): TaskForm {
        val deadlineDay = deadline?.let { TimeUtils.epochDay(it) }
        val scheduleDay = scheduledStart?.let { TimeUtils.epochDay(it) } ?: deadlineDay ?: TimeUtils.today()
        return TaskForm(
            title = title,
            description = description,
            category = categoryEnum,
            priority = priorityEnum,
            deadlineDay = deadlineDay,
            deadlineMinutes = deadline?.let { TimeUtils.minutesOfDay(it) } ?: (18 * 60),
            estimatedMinutes = estimatedMinutes,
            scheduleEnabled = scheduledStart != null && scheduledEnd != null,
            scheduleDay = scheduleDay,
            startMinutes = scheduledStart?.let { TimeUtils.minutesOfDay(it) } ?: (9 * 60),
            endMinutes = scheduledEnd?.let { TimeUtils.minutesOfDay(it) } ?: (10 * 60)
        )
    }
}
