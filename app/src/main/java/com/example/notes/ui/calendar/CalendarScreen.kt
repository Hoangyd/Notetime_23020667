package com.example.notes.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.data.BlockKind
import com.example.notes.data.EventEntity
import com.example.notes.data.TimeBlock
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.EmptyState
import com.example.notes.ui.components.Pill
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.categoryColor
import com.example.notes.ui.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

// ===============================================================================================
// MÀN HÌNH "LỊCH"
//   • Phần đầu: tiêu đề, nút Hôm nay / trước / sau, chọn chế độ Ngày-Tuần-Tháng.
//   • Chế độ Ngày và Tuần: lưới thời gian (file TimeGrid.kt).
//   • Chế độ Tháng: lưới các ô ngày (MonthView) + danh sách lịch của ngày đang chọn.
//   • Nút "+" mở tờ giấy thêm lịch (EventEditorSheet.kt).
// ===============================================================================================

private fun CalendarMode.title(): String = when (this) {
    CalendarMode.DAY -> "Ngày"
    CalendarMode.WEEK -> "Tuần"
    CalendarMode.MONTH -> "Tháng"
}

@Composable
fun CalendarScreen(
    onOpenTask: (Long) -> Unit,
    viewModel: CalendarViewModel = appViewModel { CalendarViewModel(it) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope() // để gọi hàm suspend từ sự kiện bấm

    // Sự kiện đang được soạn trong tờ giấy: null = tờ giấy đang đóng; id = 0 = thêm mới.
    var editingEvent by remember { mutableStateOf<EventEntity?>(null) }

    /** Sự kiện mới mặc định: 9:00–10:00 của ngày đang chọn. */
    fun newEventTemplate(): EventEntity {
        val start = TimeUtils.at(state.selectedDay, 9 * 60)
        return EventEntity(title = "", startAt = start, endAt = start + 3_600_000L)
    }

    /** Bấm vào một khối: công việc -> mở form công việc; sự kiện -> mở tờ giấy sửa lịch. */
    fun openBlock(block: TimeBlock) {
        when (block.kind) {
            BlockKind.TASK -> onOpenTask(block.id)
            BlockKind.EVENT -> scope.launch { viewModel.loadEvent(block.id)?.let { editingEvent = it } }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingEvent = newEventTemplate() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Rounded.Add, contentDescription = "Thêm lịch") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            CalendarHeader(state = state, viewModel = viewModel)

            if (state.conflictKeys.isNotEmpty() && state.mode != CalendarMode.MONTH) {
                ConflictBanner(count = state.conflictKeys.size)
            }

            if (state.mode == CalendarMode.MONTH) {
                MonthView(state = state, viewModel = viewModel, onBlockClick = ::openBlock)
            } else {
                TimeGrid(
                    firstDay = state.firstDay,
                    dayCount = state.dayCount,
                    selectedDay = state.selectedDay,
                    blocks = state.blocks,
                    conflictKeys = state.conflictKeys,
                    onSelectDay = viewModel::selectDay,
                    onBlockClick = ::openBlock,
                    onMove = viewModel::moveBlock
                )
            }
        }
    }

    // Tờ giấy thêm / sửa lịch: chỉ hiện khi `editingEvent` khác null.
    editingEvent?.let { event ->
        EventEditorSheet(
            initial = event,
            onDismiss = { editingEvent = null },
            onSave = viewModel::saveEvent,
            onDelete = { viewModel.deleteEvent(event.id) }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Đầu trang
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarHeader(state: CalendarState, viewModel: CalendarViewModel) {
    // Dòng phụ dưới chữ "Lịch" thay đổi theo chế độ xem.
    val subtitle = when (state.mode) {
        CalendarMode.DAY -> Fmt.dayTitle(state.selectedDay)
        CalendarMode.WEEK -> "${Fmt.shortDay(state.firstDay)} – ${Fmt.shortDay(state.firstDay + 6)}"
        CalendarMode.MONTH -> Fmt.monthTitle(state.selectedDay)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lịch", style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = viewModel::goToday) { Text("Hôm nay") }
            IconButton(onClick = { viewModel.step(-1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Trước")
            }
            IconButton(onClick = { viewModel.step(1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Sau")
            }
        }

        // Ba nút Ngày | Tuần | Tháng
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CalendarMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = CalendarMode.entries.size)
                ) { Text(mode.title()) }
            }
        }
    }
}

/** Dải cảnh báo đỏ: có bao nhiêu mục đang trùng giờ. */
@Composable
private fun ConflictBanner(count: Int) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DangerRed.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
        Text(
            "$count mục đang trùng giờ — nhấn giữ để kéo dời",
            style = MaterialTheme.typography.labelMedium,
            color = DangerRed
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Chế độ Tháng
// ---------------------------------------------------------------------------------------------

@Composable
private fun MonthView(
    state: CalendarState,
    viewModel: CalendarViewModel,
    onBlockClick: (TimeBlock) -> Unit
) {
    val today = remember { TimeUtils.today() }
    val shownMonth = LocalDate.ofEpochDay(state.selectedDay).monthValue
    val weekRows = state.dayCount / 7 // số hàng tuần của lưới

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hàng tên thứ: T2 ... CN
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in Fmt.weekdayLabels) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Lưới các ô ngày: mỗi hàng 7 ô.
        repeat(weekRows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = state.firstDay + row * 7 + column
                    val dayStart = TimeUtils.dayStart(day)
                    val dayEnd = TimeUtils.dayEnd(day)
                    val blocksOfDay = state.blocks.filter { it.start < dayEnd && it.end > dayStart }

                    MonthCell(
                        day = day,
                        isToday = day == today,
                        isSelected = day == state.selectedDay,
                        isInShownMonth = LocalDate.ofEpochDay(day).monthValue == shownMonth,
                        blocks = blocksOfDay,
                        hasConflict = blocksOfDay.any { it.key in state.conflictKeys },
                        onClick = { viewModel.selectDay(day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SelectedDayAgenda(state = state, onBlockClick = onBlockClick)

        Box(Modifier.height(80.dp)) // chừa chỗ cho nút "+"
    }
}

/** Danh sách lịch của ngày đang chọn, nằm dưới lưới tháng. */
@Composable
private fun SelectedDayAgenda(state: CalendarState, onBlockClick: (TimeBlock) -> Unit) {
    val dayStart = TimeUtils.dayStart(state.selectedDay)
    val dayEnd = TimeUtils.dayEnd(state.selectedDay)
    val agenda = state.blocks.filter { it.start < dayEnd && it.end > dayStart }

    Text(
        Fmt.dayTitle(state.selectedDay),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )

    if (agenda.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.EventAvailable,
            title = "Ngày trống",
            subtitle = "Chưa có lịch nào. Bấm + để thêm lịch học, lịch làm hoặc cuộc hẹn."
        )
    } else {
        for (block in agenda) {
            AgendaRow(block = block, hasConflict = block.key in state.conflictKeys, onClick = { onBlockClick(block) })
        }
    }
}

/** Một ô ngày trong lưới tháng: số ngày + tối đa 3 chấm màu cho các lịch trong ngày. */
@Composable
private fun MonthCell(
    day: Long,
    isToday: Boolean,
    isSelected: Boolean,
    isInShownMonth: Boolean,
    blocks: List<TimeBlock>,
    hasConflict: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val dayNumber = LocalDate.ofEpochDay(day).dayOfMonth

    // Màu số ngày: trắng/đen tuỳ nền nếu là hôm nay; mờ nếu thuộc tháng khác.
    val numberColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimary
        isInShownMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier
            .padding(1.dp)
            .height(62.dp)
            .clip(shape)
            .then(if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(dayNumber.toString(), style = MaterialTheme.typography.labelLarge, color = numberColor)
        }

        // Các chấm màu: mỗi chấm một lịch, nhiều hơn 3 thì ghi "+N".
        Row(
            modifier = Modifier.padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (block in blocks.take(3)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(block.categoryColor()))
            }
            if (blocks.size > 3) {
                Text("+${blocks.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Gạch đỏ nhỏ nếu ngày này có lịch trùng giờ.
        if (hasConflict) {
            Box(Modifier.padding(top = 3.dp).size(width = 14.dp, height = 3.dp).clip(CircleShape).background(DangerRed))
        }
    }
}

/** Một dòng trong danh sách lịch của ngày: vạch màu, tên, giờ và các nhãn. */
@Composable
private fun AgendaRow(block: TimeBlock, hasConflict: Boolean, onClick: () -> Unit) {
    val color = block.categoryColor()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(width = 4.dp, height = 36.dp).clip(CircleShape).background(color))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(block.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (block.allDay) "Cả ngày" else "${Fmt.time(block.start)} – ${Fmt.time(block.end)}  •  ${Fmt.duration(block.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (block.kind == BlockKind.TASK) Pill("Công việc", color)
            if (block.isRepeating) Pill("Lặp", color)
            if (hasConflict) Pill("Trùng giờ", DangerRed, icon = Icons.Rounded.Warning)
        }
    }
}
