package com.example.notes.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.EventEntity
import com.example.notes.data.Scheduling
import com.example.notes.data.TimeBlock
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ba chế độ xem lịch. */
enum class CalendarMode { DAY, WEEK, MONTH }

/** Mọi thứ màn Lịch cần để vẽ. */
data class CalendarState(
    val mode: CalendarMode = CalendarMode.WEEK,
    val selectedDay: Long = TimeUtils.today(), // ngày đang được chọn
    val firstDay: Long = TimeUtils.weekStart(TimeUtils.today()), // ngày đầu tiên đang hiển thị
    val dayCount: Int = 7, // số ngày đang hiển thị (1 / 7 / 28-42)
    val blocks: List<TimeBlock> = emptyList(), // các khối lịch trong khoảng đang hiển thị
    val conflictKeys: Set<String> = emptySet() // khoá của các khối bị trùng giờ
)

/**
 * ViewModel của màn Lịch.
 *
 * Hai thứ người dùng điều khiển là "chế độ xem" và "ngày đang chọn". Từ hai thứ đó ta tính ra
 * khoảng ngày cần hiển thị, rồi lấy các khối lịch trong khoảng ấy từ database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val app: NotesApplication) : ViewModel() {
    private val mode = MutableStateFlow(CalendarMode.WEEK)
    private val selectedDay = MutableStateFlow(TimeUtils.today())

    /** Khoảng ngày cần hiển thị. */
    private data class VisibleRange(val mode: CalendarMode, val selected: Long, val first: Long, val count: Int)

    /** Tính khoảng ngày hiển thị từ (chế độ, ngày chọn). */
    private val visibleRange = combine(mode, selectedDay) { currentMode, day ->
        when (currentMode) {
            // Chế độ ngày: chỉ một ngày.
            CalendarMode.DAY -> VisibleRange(currentMode, day, first = day, count = 1)

            // Chế độ tuần: từ thứ Hai của tuần chứa ngày chọn, 7 ngày.
            CalendarMode.WEEK -> VisibleRange(currentMode, day, first = TimeUtils.weekStart(day), count = 7)

            // Chế độ tháng: bắt đầu từ thứ Hai của tuần chứa mùng 1, đủ số hàng để chứa hết tháng.
            CalendarMode.MONTH -> {
                val firstOfMonth = LocalDate.ofEpochDay(day).withDayOfMonth(1)
                val gridStart = TimeUtils.weekStart(firstOfMonth.toEpochDay())
                val lastDayOfMonth = firstOfMonth.plusMonths(1).toEpochDay() - 1
                val weekRows = ((lastDayOfMonth - gridStart) / 7 + 1).toInt()
                VisibleRange(currentMode, day, first = gridStart, count = weekRows * 7)
            }
        }
    }

    /** Trạng thái màn hình: mỗi khi khoảng ngày đổi, tải lại các khối lịch tương ứng. */
    val state: StateFlow<CalendarState> = visibleRange.flatMapLatest { range ->
        val from = TimeUtils.dayStart(range.first)
        val to = TimeUtils.dayStart(range.first + range.count)
        app.calendarRepository.observeBlocks(from, to).map { blocks ->
            CalendarState(
                mode = range.mode,
                selectedDay = range.selected,
                firstDay = range.first,
                dayCount = range.count,
                blocks = blocks,
                conflictKeys = Scheduling.conflictKeys(blocks)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    // ----- Điều hướng trên lịch -----

    fun setMode(value: CalendarMode) {
        mode.value = value
    }

    fun selectDay(day: Long) {
        selectedDay.value = day
    }

    fun goToday() {
        selectedDay.value = TimeUtils.today()
    }

    /** Sang trước (direction = -1) hoặc sau (+1): lùi/tiến một ngày, một tuần hoặc một tháng. */
    fun step(direction: Int) {
        val day = selectedDay.value
        selectedDay.value = when (mode.value) {
            CalendarMode.DAY -> day + direction
            CalendarMode.WEEK -> day + 7L * direction
            CalendarMode.MONTH -> LocalDate.ofEpochDay(day).plusMonths(direction.toLong()).toEpochDay()
        }
    }

    // ----- Sự kiện lịch -----

    /** Tải một sự kiện để sửa (suspend: gọi từ coroutine). */
    suspend fun loadEvent(id: Long): EventEntity? = app.calendarRepository.getEvent(id)

    fun saveEvent(event: EventEntity) {
        viewModelScope.launch {
            try {
                val result = app.calendarRepository.saveEvent(event)
                if (result.hasConflict) {
                    app.messenger.post("Đã lưu, nhưng trùng giờ với: ${result.conflicts.joinToString { it.title }}")
                } else {
                    app.messenger.post(if (event.id > 0) "Đã cập nhật lịch" else "Đã thêm lịch")
                }
            } catch (e: IllegalArgumentException) {
                app.messenger.post(e.message ?: "Không thể lưu lịch")
            }
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            app.calendarRepository.deleteEvent(id)
            app.messenger.post("Đã xoá lịch")
        }
    }

    /**
     * Kéo thả: dời một khối đi [deltaDays] ngày và [deltaMinutes] phút.
     * Báo cho người dùng biết nếu vị trí mới bị trùng giờ.
     */
    fun moveBlock(block: TimeBlock, deltaDays: Int, deltaMinutes: Int) {
        if (deltaDays == 0 && deltaMinutes == 0) return
        viewModelScope.launch {
            val result = app.calendarRepository.shiftBlock(block, deltaDays, deltaMinutes) ?: return@launch
            if (result.hasConflict) {
                app.messenger.post("Đã dời “${block.title}”, trùng giờ với: ${result.conflicts.joinToString { it.title }}")
            } else {
                app.messenger.post("Đã dời “${block.title}”")
            }
        }
    }
}
