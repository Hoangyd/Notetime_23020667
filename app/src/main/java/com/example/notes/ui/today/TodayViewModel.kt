package com.example.notes.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.Scheduling
import com.example.notes.data.TaskEntity
import com.example.notes.data.TimeBlock
import com.example.notes.data.TodayOverview
import com.example.notes.domain.Category
import com.example.notes.domain.Note
import com.example.notes.domain.Priority
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Toàn bộ dữ liệu màn "Hôm nay" cần để vẽ. Màn hình chỉ đọc đối tượng này, không tự tính toán.
 */
data class TodayState(
    val epochDay: Long = TimeUtils.today(),
    /** Công việc hôm nay, việc quá hạn, việc sắp tới, gợi ý. Null = đang tải. */
    val overview: TodayOverview? = null,
    /** Các khối lịch trong ngày (sự kiện + công việc đã xếp giờ), sắp theo giờ bắt đầu. */
    val blocks: List<TimeBlock> = emptyList(),
    /** Khoá của các khối đang bị trùng giờ. */
    val conflictKeys: Set<String> = emptySet(),
    /** Ghi chú gắn với ngày hôm nay. */
    val notes: List<Note> = emptyList()
)

/**
 * ViewModel của màn Hôm nay.
 *
 * Vai trò: (1) gom dữ liệu từ nhiều repository thành một [TodayState],
 *          (2) nhận các hành động của người dùng (tick việc, thêm nhanh) và chuyển cho repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val app: NotesApplication) : ViewModel() {

    // Ngày đang xem. Là MutableStateFlow để khi sang ngày mới, ta đổi giá trị và dữ liệu tự tải lại.
    private val currentDay = MutableStateFlow(TimeUtils.today())

    /**
     * Trạng thái màn hình (chỉ đọc cho bên ngoài).
     *
     * Đọc từ trên xuống:
     *  - `currentDay.flatMapLatest { day -> ... }`: mỗi khi ngày đổi, bắt đầu lắng nghe dữ liệu của ngày đó.
     *  - `combine(a, b, c) { ... }`: có 3 nguồn dữ liệu; khi BẤT KỲ nguồn nào đổi, tính lại TodayState.
     *  - `stateIn(...)`: biến luồng thành StateFlow luôn có giá trị. `WhileSubscribed(5_000)` nghĩa là
     *    ngừng lắng nghe database nếu không còn ai xem màn hình trong 5 giây.
     */
    val state: StateFlow<TodayState> = currentDay.flatMapLatest { day ->
        combine(
            app.taskRepository.observeToday(day),
            app.calendarRepository.observeBlocks(TimeUtils.dayStart(day), TimeUtils.dayEnd(day)),
            app.noteRepository.observeNotesForDate(day)
        ) { overview, blocks, notes ->
            TodayState(
                epochDay = day,
                overview = overview,
                blocks = blocks,
                conflictKeys = Scheduling.conflictKeys(blocks),
                notes = notes
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Gọi khi người dùng quay lại app, phòng trường hợp đã qua nửa đêm sang ngày mới. */
    fun refreshDay() {
        currentDay.value = TimeUtils.today()
    }

    /** Tick / bỏ tick một công việc. */
    fun toggleTask(taskId: Long) {
        // viewModelScope.launch: chạy ở nền; tự huỷ khi ViewModel không còn dùng.
        viewModelScope.launch {
            app.taskRepository.toggleDone(taskId)
        }
    }

    /** Thêm nhanh một công việc từ ô "Thêm nhanh". */
    fun quickAddTask(title: String, category: Category, priority: Priority, dueToday: Boolean) {
        if (title.isBlank()) return
        viewModelScope.launch {
            // "Hạn trong hôm nay" = cuối ngày hôm nay (23:59).
            val deadline = if (dueToday) TimeUtils.dayEnd(TimeUtils.today()) - 60_000L else null
            app.taskRepository.saveTask(
                TaskEntity(
                    title = title,
                    category = category.name,
                    priority = priority.level,
                    deadline = deadline
                )
            )
            app.messenger.post("Đã thêm công việc")
        }
    }

    /** Thêm nhanh một ghi chú: dòng đầu là tiêu đề, các dòng sau là nội dung. */
    fun quickAddNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val lines = text.trim().lines()
            app.noteRepository.save(
                Note(
                    title = lines.first().take(80),
                    content = lines.drop(1).joinToString("\n")
                )
            )
            app.messenger.post("Đã thêm ghi chú")
        }
    }
}
