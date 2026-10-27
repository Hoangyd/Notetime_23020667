package com.example.notes.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.NotesApplication
import com.example.notes.data.CategoryPlan
import com.example.notes.data.CategoryTotal
import com.example.notes.data.DayCompletion
import com.example.notes.data.HabitEntity
import com.example.notes.data.HabitWithStats
import com.example.notes.domain.Category
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Heatmap hiển thị bao nhiêu tuần gần nhất. */
const val HEATMAP_WEEKS = 16

/** Mọi thứ màn Thống kê cần để vẽ. */
data class InsightsState(
    val today: Long = TimeUtils.today(),
    val weekStart: Long = TimeUtils.weekStart(TimeUtils.today()), // thứ Hai của tuần đang xem
    val habits: List<HabitWithStats> = emptyList(),
    val weekly: List<DayCompletion> = emptyList(), // 7 ngày: số việc đến hạn / đã xong
    val timeByCategory: List<CategoryTotal> = emptyList(), // thời gian thực tế theo danh mục
    val planVsActual: List<CategoryPlan> = emptyList(), // kế hoạch so với thực tế
    val heatmap: Map<Long, Int> = emptyMap(), // ngày -> số hoạt động
    val loaded: Boolean = false
) {
    // Các số tổng hợp, tính từ dữ liệu ở trên.
    val weekDue: Int get() = weekly.sumOf { it.due }
    val weekDone: Int get() = weekly.sumOf { it.done }
    val weekRate: Float get() = if (weekDue == 0) 0f else weekDone.toFloat() / weekDue
    val trackedMs: Long get() = timeByCategory.sumOf { it.totalMs }
    val bestStreak: Int get() = habits.maxOfOrNull { it.longestStreak } ?: 0
}

/**
 * ViewModel của màn Thống kê.
 * Dữ liệu chia hai loại: loại phụ thuộc TUẦN đang xem (cột hoàn thành, thời gian, kế hoạch)
 * và loại không phụ thuộc (thói quen, heatmap).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(private val app: NotesApplication) : ViewModel() {
    private val today = TimeUtils.today()

    // Tuần đang xem, tính bằng ngày đầu tuần (thứ Hai). Đổi giá trị này để xem tuần khác.
    private val weekStart = MutableStateFlow(TimeUtils.weekStart(today))

    /** Gói các dữ liệu phụ thuộc vào tuần. */
    private data class WeekData(
        val weekStart: Long,
        val weekly: List<DayCompletion>,
        val time: List<CategoryTotal>,
        val plan: List<CategoryPlan>
    )

    /** Mỗi khi đổi tuần, tải lại 3 loại dữ liệu của tuần đó. */
    private val weekData = weekStart.flatMapLatest { start ->
        val from = TimeUtils.dayStart(start)
        val to = TimeUtils.dayStart(start + 7)
        combine(
            app.insightsRepository.observeWeeklyCompletion(start),
            app.insightsRepository.observeTimeByCategory(from, to),
            app.insightsRepository.observePlannedVsActual(from, to)
        ) { weekly, time, plan -> WeekData(start, weekly, time, plan) }
    }

    /** Ghép tất cả các nguồn thành một InsightsState. */
    val state: StateFlow<InsightsState> = combine(
        app.habitRepository.observeHabits(today),
        weekData,
        app.insightsRepository.observeHeatmap(TimeUtils.weekStart(today) - (HEATMAP_WEEKS - 1) * 7L, today)
    ) { habits, week, heatmap ->
        InsightsState(
            today = today,
            weekStart = week.weekStart,
            habits = habits,
            weekly = week.weekly,
            timeByCategory = week.time,
            planVsActual = week.plan,
            heatmap = heatmap,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    // ----- Chuyển tuần -----

    /** direction = -1: tuần trước; +1: tuần sau. */
    fun shiftWeek(direction: Int) {
        weekStart.value += 7L * direction
    }

    fun thisWeek() {
        weekStart.value = TimeUtils.weekStart(today)
    }

    // ----- Thói quen -----

    /** Đánh dấu / bỏ đánh dấu thói quen đã làm vào ngày [day]. */
    fun toggleHabit(habitId: Long, day: Long) {
        viewModelScope.launch {
            app.habitRepository.toggle(habitId, day)
        }
    }

    fun saveHabit(habit: HabitEntity) {
        viewModelScope.launch {
            try {
                app.habitRepository.saveHabit(habit)
            } catch (e: IllegalArgumentException) {
                app.messenger.post(e.message ?: "Không thể lưu thói quen")
            }
        }
    }

    fun archiveHabit(habit: HabitEntity) {
        viewModelScope.launch {
            app.habitRepository.archiveHabit(habit.id)
            app.messenger.post(
                UiMessage(
                    text = "Đã lưu trữ “${habit.name}”",
                    actionLabel = "Hoàn tác",
                    onAction = {
                        viewModelScope.launch { app.habitRepository.archiveHabit(habit.id, archived = false) }
                    }
                )
            )
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            app.habitRepository.deleteHabit(habit.id)
            app.messenger.post("Đã xoá thói quen “${habit.name}”")
        }
    }

    // ----- Ghi nhận thời gian -----

    /** Ghi nhận [minutes] phút đã dành cho [category], tính đến thời điểm hiện tại. */
    fun logTime(category: Category, minutes: Int) {
        if (minutes <= 0) return
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            app.insightsRepository.logTime(category, start = end - minutes * 60_000L, end = end)
            app.messenger.post("Đã ghi nhận $minutes phút")
        }
    }
}
