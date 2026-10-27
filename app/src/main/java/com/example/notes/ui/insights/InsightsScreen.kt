package com.example.notes.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.data.HabitEntity
import com.example.notes.data.HabitWithStats
import com.example.notes.domain.Category
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.ActivityHeatmap
import com.example.notes.ui.components.DonutChart
import com.example.notes.ui.components.EmptyState
import com.example.notes.ui.components.LegendDot
import com.example.notes.ui.components.PlanActualBars
import com.example.notes.ui.components.SectionHeader
import com.example.notes.ui.components.WeeklyBarChart
import com.example.notes.ui.theme.WarningAmber
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.label
import com.example.notes.ui.util.Fmt

// ===============================================================================================
// MÀN HÌNH "THỐNG KÊ & THÓI QUEN"
//
// Là một danh sách cuộn gồm các khối (mỗi khối là một hàm bên dưới):
//   StatTiles → Thói quen (HabitCard) → Hoàn thành công việc (cột) → Thời gian theo danh mục (tròn)
//   → Kế hoạch so với thực tế → Hoạt động (heatmap)
// ===============================================================================================

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = appViewModel { InsightsViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Hộp thoại thói quen: null = đóng; HabitEntity có id = 0 = tạo mới; có id > 0 = đang sửa.
    var habitDialog by remember { mutableStateOf<HabitEntity?>(null) }
    var showLogTime by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Thống kê", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Thói quen, tiến độ và thời gian của bạn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { StatTiles(state) }

            // ----- Thói quen -----
            item {
                SectionHeader(
                    title = "Thói quen",
                    subtitle = "Đánh dấu mỗi ngày để giữ chuỗi",
                    action = {
                        TextButton(onClick = { habitDialog = HabitEntity(name = "") }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Thêm", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                )
            }
            if (state.habits.isEmpty() && state.loaded) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.LocalFireDepartment,
                        title = "Chưa có thói quen nào",
                        subtitle = "Tạo thói quen như đọc sách, tập thể dục, học ngoại ngữ để theo dõi chuỗi ngày."
                    )
                }
            }
            items(state.habits, key = { it.habit.id }) { habit ->
                HabitCard(
                    stats = habit,
                    today = state.today,
                    onToggle = { day -> viewModel.toggleHabit(habit.habit.id, day) },
                    onEdit = { habitDialog = habit.habit }
                )
            }

            // ----- Biểu đồ -----
            item { WeeklyCompletionCard(state, viewModel) }

            item {
                ChartCard(
                    title = "Thời gian theo danh mục",
                    subtitle = "Thời gian thực tế bạn đã ghi nhận trong tuần",
                    header = {
                        OutlinedButton(onClick = { showLogTime = true }) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Ghi nhận", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                ) { TimeByCategory(state) }
            }

            item {
                ChartCard(
                    title = "Kế hoạch so với thực tế",
                    subtitle = "Thanh nhạt: đã lên lịch • thanh đậm: thực tế"
                ) { PlanVsActual(state) }
            }

            item {
                ChartCard(
                    title = "Hoạt động",
                    subtitle = "$HEATMAP_WEEKS tuần gần nhất (việc hoàn thành + thói quen)"
                ) {
                    ActivityHeatmap(counts = state.heatmap, lastDay = state.today, weeks = HEATMAP_WEEKS, todayEpochDay = state.today)
                    HeatmapLegend()
                }
            }
        }
    }

    // Hộp thoại thêm / sửa thói quen
    habitDialog?.let { habit ->
        val isNew = habit.id == 0L
        HabitDialog(
            initial = if (isNew) null else habit,
            onDismiss = { habitDialog = null },
            onSave = viewModel::saveHabit,
            onArchive = if (isNew) null else ({ viewModel.archiveHabit(habit) }),
            onDelete = if (isNew) null else ({ viewModel.deleteHabit(habit) })
        )
    }

    if (showLogTime) {
        LogTimeDialog(onDismiss = { showLogTime = false }, onSave = viewModel::logTime)
    }
}

// ---------------------------------------------------------------------------------------------
// 3 ô số liệu ở đầu trang
// ---------------------------------------------------------------------------------------------

@Composable
private fun StatTiles(state: InsightsState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.TaskAlt,
                color = MaterialTheme.colorScheme.primary,
                value = "${(state.weekRate * 100).toInt()}%",
                label = "Hoàn thành tuần này"
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LocalFireDepartment,
                color = WarningAmber,
                value = "${state.bestStreak}",
                label = "Chuỗi ngày dài nhất"
            )
        }
        StatTile(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Timer,
            color = Category.WORK.color,
            value = if (state.trackedMs == 0L) "0" else Fmt.duration(state.trackedMs),
            label = "Thời gian ghi nhận"
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, color: Color, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Khung chung cho các biểu đồ
// ---------------------------------------------------------------------------------------------

/** Thẻ có tiêu đề, dòng phụ, (tuỳ chọn) một nút bên phải tiêu đề, và nội dung bên dưới. */
@Composable
private fun ChartCard(
    title: String,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                header?.invoke()
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hoàn thành công việc trong tuần
// ---------------------------------------------------------------------------------------------

@Composable
private fun WeeklyCompletionCard(state: InsightsState, viewModel: InsightsViewModel) {
    val isCurrentWeek = state.weekStart == TimeUtils.weekStart(state.today)

    ChartCard(
        title = "Hoàn thành công việc",
        subtitle = "${Fmt.shortDay(state.weekStart)} – ${Fmt.shortDay(state.weekStart + 6)}",
        header = {
            // Nút "Tuần này" (khi đang xem tuần khác) và hai mũi tên chuyển tuần.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isCurrentWeek) TextButton(onClick = viewModel::thisWeek) { Text("Tuần này") }
                IconButton(onClick = { viewModel.shiftWeek(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Tuần trước")
                }
                IconButton(onClick = { viewModel.shiftWeek(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Tuần sau")
                }
            }
        }
    ) {
        if (state.weekDue == 0) {
            Text(
                "Tuần này chưa có công việc nào có hạn chót.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "${state.weekDone}/${state.weekDue} việc • ${(state.weekRate * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge
            )
        }
        WeeklyBarChart(days = state.weekly, todayEpochDay = state.today)
    }
}

// ---------------------------------------------------------------------------------------------
// Thẻ thói quen
// ---------------------------------------------------------------------------------------------

/**
 * Thẻ một thói quen: biểu tượng, tên, chuỗi ngày, nút tick hôm nay, và dải 7 ngày gần nhất
 * (bấm vào từng ngày để đánh dấu bù).
 */
@Composable
private fun HabitCard(
    stats: HabitWithStats,
    today: Long,
    onToggle: (Long) -> Unit,
    onEdit: () -> Unit
) {
    val habit = stats.habit
    val color = Category.fromKey(habit.category).color

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onEdit
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Biểu tượng tròn
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(HabitIcons.of(habit.icon), contentDescription = null, tint = color)
                }

                // Tên + chuỗi ngày
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(habit.name, style = MaterialTheme.typography.titleSmall)
                    StreakLine(stats)
                }

                // Nút tick lớn cho hôm nay
                TodayCheckButton(done = stats.doneToday, color = color, onClick = { onToggle(today) })
            }

            LastSevenDays(stats = stats, today = today, color = color, onToggle = onToggle)
        }
    }
}

/** "🔥 5 ngày • dài nhất 12 • 85%/30 ngày" */
@Composable
private fun StreakLine(stats: HabitWithStats) {
    val hasStreak = stats.currentStreak > 0

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = if (hasStreak) WarningAmber else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "${stats.currentStreak} ngày",
            style = MaterialTheme.typography.labelLarge,
            color = if (hasStreak) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "• dài nhất ${stats.longestStreak} • ${(stats.rate30Days * 100).toInt()}%/30 ngày",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayCheckButton(done: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Rounded.Check, contentDescription = "Đã làm hôm nay", tint = Color.White)
    }
}

/**
 * 7 vòng tròn nhỏ cho 7 ngày gần nhất (cũ -> mới):
 * đặc = đã làm; nhạt = ngày có lịch nhưng chưa làm; xám = ngày không nằm trong lịch của thói quen.
 */
@Composable
private fun LastSevenDays(stats: HabitWithStats, today: Long, color: Color, onToggle: (Long) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (daysAgo in 6 downTo 0) {
            val day = today - daysAgo
            val isDone = day in stats.doneDays
            val isScheduled = TimeUtils.isDayInMask(stats.habit.daysMask, day)

            val circleColor = when {
                isDone -> color
                isScheduled -> color.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Fmt.weekdayShort(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(circleColor)
                        .clickable { onToggle(day) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Thời gian theo danh mục, kế hoạch vs thực tế, heatmap
// ---------------------------------------------------------------------------------------------

@Composable
private fun TimeByCategory(state: InsightsState) {
    // Thời gian (ms) của từng danh mục; danh mục không có dữ liệu = 0.
    val totals = Category.entries.map { category ->
        category to (state.timeByCategory.firstOrNull { it.category == category.name }?.totalMs ?: 0L)
    }
    val grandTotal = totals.sumOf { it.second }

    if (grandTotal == 0L) {
        Text(
            "Chưa có dữ liệu. Bấm “Ghi nhận” sau khi học/làm để xem biểu đồ.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // Biểu đồ tròn, ở giữa ghi tổng thời gian
        DonutChart(segments = totals.map { (category, ms) -> category.color to ms.toFloat() }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.duration(grandTotal), style = MaterialTheme.typography.titleMedium)
                Text("tổng", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Chú thích: chấm màu + tên + thời gian + phần trăm
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((category, ms) in totals) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(category.color, category.label, modifier = Modifier.weight(1f))
                    Text(
                        text = if (ms == 0L) "–" else "${Fmt.duration(ms)} · ${ms * 100 / grandTotal}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanVsActual(state: InsightsState) {
    // Chỉ hiện các danh mục có kế hoạch hoặc có thời gian thực tế.
    val rows = state.planVsActual.filter { it.plannedMs > 0 || it.actualMs > 0 }

    if (rows.isEmpty()) {
        Text(
            "Xếp giờ công việc trên lịch và ghi nhận thời gian thực tế để so sánh.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Giá trị lớn nhất để các thanh cùng tỉ lệ với nhau.
    val maxMs = rows.maxOf { maxOf(it.plannedMs, it.actualMs) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in rows) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(row.category.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${Fmt.duration(row.actualMs)} / ${Fmt.duration(row.plannedMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PlanActualBars(plannedMs = row.plannedMs, actualMs = row.actualMs, maxMs = maxMs, color = row.category.color)
            }
        }
    }
}

/** Chú thích "Ít ... Nhiều" bên dưới heatmap. */
@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Ít", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (alpha in listOf(0f, 0.35f, 0.65f, 1f)) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (alpha == 0f) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    )
            )
        }
        Text("  Nhiều", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
