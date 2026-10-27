package com.example.notes.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.TaskWithSubtasks
import com.example.notes.domain.Category
import com.example.notes.domain.Priority
import com.example.notes.domain.TaskFilter
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.UiMessage
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

/** Mọi thứ màn danh sách công việc cần để vẽ: bộ lọc đang chọn + danh sách kết quả. */
data class TasksState(
    val filter: TaskFilter = TaskFilter.ALL, // Tất cả / Hôm nay / Sắp tới / Quá hạn / Hoàn thành
    val search: String = "", // chữ trong ô tìm kiếm
    val category: Category? = null, // null = mọi danh mục
    val priority: Priority? = null, // null = mọi mức ưu tiên
    val tasks: List<TaskWithSubtasks> = emptyList(),
    val loaded: Boolean = false // false = đang tải lần đầu
)

/**
 * ViewModel của màn danh sách công việc.
 *
 * Ý tưởng: giữ 4 "ô nhớ" cho 4 bộ lọc. Khi người dùng đổi bộ lọc nào, ô đó đổi giá trị,
 * danh sách tự tải lại theo bộ lọc mới.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TasksViewModel(private val app: NotesApplication) : ViewModel() {

    // 4 ô nhớ cho bộ lọc (MutableStateFlow: giữ một giá trị và báo cho người nghe khi nó đổi).
    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val search = MutableStateFlow("")
    private val category = MutableStateFlow<Category?>(null)
    private val priority = MutableStateFlow<Priority?>(null)

    /** Gom 4 bộ lọc thành một đối tượng để truyền đi cho gọn. */
    private data class Query(
        val filter: TaskFilter,
        val search: String,
        val category: Category?,
        val priority: Priority?
    )

    // Mỗi khi MỘT trong 4 bộ lọc đổi, tạo ra một Query mới.
    // debounce(200): đợi người dùng ngừng gõ 0,2 giây rồi mới tìm (tránh truy vấn mỗi phím bấm).
    private val queryFlow = combine(filter, search.debounce(200), category, priority) { f, s, c, p ->
        Query(f, s, c, p)
    }

    /**
     * Trạng thái màn hình = kết quả truy vấn + giá trị các bộ lọc.
     * `flatMapLatest`: mỗi khi có Query mới, bỏ truy vấn cũ và lắng nghe truy vấn mới.
     */
    val state: StateFlow<TasksState> = combine(
        queryFlow.flatMapLatest { q ->
            app.taskRepository.observeTasks(
                filter = q.filter,
                search = q.search,
                category = q.category,
                priority = q.priority,
                epochDay = TimeUtils.today()
            )
        },
        filter, search, category, priority
    ) { tasks, f, s, c, p ->
        TasksState(filter = f, search = s, category = c, priority = p, tasks = tasks, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    // ----- Các hàm đơn giản để màn hình báo "người dùng vừa đổi bộ lọc" -----

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun setSearch(value: String) {
        search.value = value
    }

    fun setCategory(value: Category?) {
        category.value = value
    }

    fun setPriority(value: Priority?) {
        priority.value = value
    }

    // ----- Hành động trên công việc -----

    /** Đổi trạng thái xong / chưa xong. */
    fun toggleDone(taskId: Long) {
        viewModelScope.launch {
            app.taskRepository.toggleDone(taskId)
        }
    }

    /** Xoá công việc và hiện thông báo có nút "Hoàn tác" để khôi phục. */
    fun delete(item: TaskWithSubtasks) {
        viewModelScope.launch {
            app.taskRepository.deleteTask(item.task.id)
            app.messenger.post(
                UiMessage(
                    text = "Đã xoá “${item.task.title}”",
                    actionLabel = "Hoàn tác",
                    onAction = { viewModelScope.launch { app.taskRepository.restoreTask(item) } }
                )
            )
        }
    }
}
