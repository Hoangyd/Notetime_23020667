package com.example.notes.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.data.BlockKind
import com.example.notes.data.TaskEntity
import com.example.notes.data.TaskWithSubtasks
import com.example.notes.data.TimeBlock
import com.example.notes.data.categoryEnum
import com.example.notes.domain.DayPart
import com.example.notes.domain.Note
import com.example.notes.domain.Priority
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.CircleCheck
import com.example.notes.ui.components.EmptyState
import com.example.notes.ui.components.Pill
import com.example.notes.ui.components.ProgressRing
import com.example.notes.ui.components.SectionHeader
import com.example.notes.ui.components.TaskCard
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.categoryColor
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label
import com.example.notes.ui.util.Fmt

// ===============================================================================================
// MÀN HÌNH "HÔM NAY"
//
// Cấu trúc từ trên xuống (mỗi phần là một hàm riêng bên dưới):
//   1. HeaderCard        – lời chào, ngày, vòng tiến độ
//   2. suggestionSection – "Nên làm tiếp"
//   3. timelineSection   – lịch trình theo sáng / chiều / tối
//   4. todoSection       – việc đến hạn hôm nay nhưng chưa xếp giờ
//   5. notesSection      – ghi chú gắn với hôm nay
//   6. overdueSection    – việc quá hạn
//   7. upcomingSection   – việc sắp tới
// ===============================================================================================

@Composable
fun TodayScreen(
    onOpenTask: (Long) -> Unit,
    onNewTask: () -> Unit,
    onNewNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenCalendar: () -> Unit,
    viewModel: TodayViewModel = appViewModel { TodayViewModel(it) }
) {
    // Đọc trạng thái từ ViewModel. Mỗi khi `state` đổi, hàm này tự chạy lại và vẽ lại giao diện.
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Trạng thái cục bộ của màn hình: đang mở ô "Thêm nhanh" hay không.
    var showQuickAdd by remember { mutableStateOf(false) }

    // Khi người dùng quay lại app (ON_RESUME), kiểm tra lại xem đã sang ngày mới chưa.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDay() }

    // "Bây giờ" – tính lại mỗi khi dữ liệu đổi, dùng để so sánh với hạn chót.
    val now = remember(state) { System.currentTimeMillis() }
    val overview = state.overview

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showQuickAdd = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Thêm nhanh") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        // LazyColumn: danh sách cuộn dọc. Mỗi `item { }` là một dòng, `items(list) { }` là nhiều dòng.
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                HeaderCard(
                    epochDay = state.epochDay,
                    doneCount = overview?.doneCount ?: 0,
                    totalCount = overview?.totalCount ?: 0,
                    overdueCount = overview?.overdue?.size ?: 0,
                    progress = overview?.progress ?: 0f
                )
            }

            suggestionSection(
                suggestions = overview?.suggestions.orEmpty(),
                now = now,
                onDone = viewModel::toggleTask, // viewModel::toggleTask = tham chiếu tới hàm của ViewModel
                onOpen = onOpenTask
            )

            timelineSection(
                blocks = state.blocks,
                conflictKeys = state.conflictKeys,
                now = now,
                onToggleTask = viewModel::toggleTask,
                onOpenTask = onOpenTask,
                onOpenCalendar = onOpenCalendar
            )

            // Việc đến hạn hôm nay mà chưa được xếp vào giờ cụ thể.
            todoSection(
                tasks = overview?.today.orEmpty().filter { it.task.scheduledStart == null },
                now = now,
                onToggle = viewModel::toggleTask,
                onOpen = onOpenTask
            )

            notesSection(notes = state.notes, onOpenNote = onOpenNote)

            overdueSection(overview?.overdue.orEmpty(), now, viewModel::toggleTask, onOpenTask)

            upcomingSection(overview?.upcoming.orEmpty(), now, viewModel::toggleTask, onOpenTask)
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onAddTask = viewModel::quickAddTask,
            onAddNote = viewModel::quickAddNote,
            onOpenFullTask = {
                showQuickAdd = false
                onNewTask()
            },
            onOpenFullNote = {
                showQuickAdd = false
                onNewNote()
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 1. Thẻ đầu trang
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeaderCard(
    epochDay: Long,
    doneCount: Int,
    totalCount: Int,
    overdueCount: Int,
    progress: Float
) {
    val hour = remember { TimeUtils.hourOf(System.currentTimeMillis()) }
    // Nền chuyển màu từ màu chính sang màu phụ theo đường chéo.
    val background = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Bên trái: lời chào, ngày, tóm tắt
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Fmt.greeting(hour), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    Fmt.dayTitle(epochDay),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        totalCount == 0 -> "Hôm nay chưa có việc nào"
                        doneCount == totalCount -> "Tuyệt vời! Đã xong tất cả"
                        else -> "Đã xong $doneCount/$totalCount việc"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                if (overdueCount > 0) {
                    Pill("$overdueCount việc quá hạn", Color.White, icon = Icons.Rounded.Warning)
                }
            }

            // Bên phải: vòng tiến độ với phần trăm ở giữa
            ProgressRing(
                progress = progress,
                size = 92.dp,
                strokeWidth = 10.dp,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            ) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Nên làm tiếp
// ---------------------------------------------------------------------------------------------

/**
 * Hàm mở rộng của LazyListScope: thêm các dòng vào LazyColumn. Viết như vậy để mỗi mục là một hàm riêng.
 */
private fun LazyListScope.suggestionSection(
    suggestions: List<TaskEntity>,
    now: Long,
    onDone: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    if (suggestions.isEmpty()) return // không có gợi ý thì không thêm dòng nào

    item(key = "suggest-header") {
        SectionHeader("Nên làm tiếp", subtitle = "Gợi ý theo hạn chót và mức ưu tiên")
    }
    item(key = "suggest") {
        SuggestionCard(suggestions, now, onDone, onOpen)
    }
}

/** Lý do gợi ý việc này (để hiện nhãn nhỏ). Trả về null nếu không có lý do nổi bật. */
private fun suggestionReason(task: TaskEntity, now: Long): Pair<String, Color>? {
    val deadline = task.deadline
    return when {
        deadline != null && deadline < now -> "Quá hạn" to DangerRed
        task.priority == Priority.HIGH.level -> "Ưu tiên cao" to Priority.HIGH.color
        deadline != null && deadline - now < 24 * 3_600_000L -> "Sắp đến hạn" to Priority.MEDIUM.color
        else -> null
    }
}

/** Thẻ lớn cho việc nên làm đầu tiên, bên dưới là 2 việc kế tiếp dạng dòng nhỏ. */
@Composable
private fun SuggestionCard(
    suggestions: List<TaskEntity>,
    now: Long,
    onDone: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    val top = suggestions.first()
    val category = top.categoryEnum
    val reason = suggestionReason(top, now)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = category.color.copy(alpha = 0.12f)),
        onClick = { onOpen(top.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Biểu tượng tròn có màu danh mục
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(category.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(top.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(category.label, category.color, icon = category.icon)
                        reason?.let { (text, color) -> Pill(text, color) }
                        // Nếu đã quá hạn thì nhãn "Quá hạn" ở trên đủ rồi, không lặp lại bằng chữ.
                        top.deadline?.takeIf { it >= now }?.let { deadline ->
                            Text(
                                Fmt.deadline(deadline, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                CircleCheck(checked = false, color = category.color, onClick = { onDone(top.id) }, size = 32.dp)
            }

            // Các gợi ý tiếp theo (số 2, 3)
            suggestions.drop(1).forEachIndexed { index, task ->
                SuggestionLine(rank = index + 2, task = task, now = now)
            }
        }
    }
}

@Composable
private fun SuggestionLine(rank: Int, task: TaskEntity, now: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$rank", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        task.deadline?.let { deadline ->
            Text(
                Fmt.deadline(deadline, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Lịch trình trong ngày (timeline)
// ---------------------------------------------------------------------------------------------

private fun DayPart.title(): String = when (this) {
    DayPart.MORNING -> "Buổi sáng"
    DayPart.AFTERNOON -> "Buổi chiều"
    DayPart.EVENING -> "Buổi tối"
}

private fun DayPart.icon(): ImageVector = when (this) {
    DayPart.MORNING -> Icons.Rounded.WbSunny
    DayPart.AFTERNOON -> Icons.Rounded.WbTwilight
    DayPart.EVENING -> Icons.Rounded.NightsStay
}

private fun LazyListScope.timelineSection(
    blocks: List<TimeBlock>,
    conflictKeys: Set<String>,
    now: Long,
    onToggleTask: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenCalendar: () -> Unit
) {
    item(key = "timeline-header") {
        SectionHeader(
            title = "Lịch trình hôm nay",
            subtitle = if (blocks.isEmpty()) null else "${blocks.size} mục",
            action = { TextButton(onClick = onOpenCalendar) { Text("Mở lịch") } }
        )
    }

    if (blocks.isEmpty()) {
        item(key = "timeline-empty") {
            EmptyState(
                icon = Icons.Rounded.EventAvailable,
                title = "Hôm nay chưa có lịch",
                subtitle = "Thêm lịch học, lịch làm hoặc xếp giờ cho công việc trong màn Lịch."
            )
        }
        return
    }

    // Tách khối "cả ngày" khỏi các khối có giờ cụ thể.
    val allDayBlocks = blocks.filter { it.allDay }
    val timedBlocks = blocks.filter { !it.allDay }

    if (allDayBlocks.isNotEmpty()) {
        item(key = "timeline-allday") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allDayBlocks.forEach { block ->
                    Pill(block.title, block.categoryColor(), icon = Icons.Rounded.EventAvailable)
                }
            }
        }
    }

    // Nhóm các khối có giờ theo buổi (sáng / chiều / tối) dựa vào giờ bắt đầu.
    val blocksByPart = timedBlocks.groupBy { DayPart.ofHour(TimeUtils.hourOf(it.start)) }

    for (part in DayPart.entries) {
        val partBlocks = blocksByPart[part].orEmpty()
        if (partBlocks.isEmpty()) continue // buổi này không có gì thì bỏ qua

        item(key = "timeline-part-${part.name}") {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(part.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(part.title(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(partBlocks, key = { "timeline-${it.key}" }) { block ->
            TimelineRow(
                block = block,
                isConflict = block.key in conflictKeys,
                isHappeningNow = now in block.start until block.end,
                onToggleTask = { onToggleTask(block.id) },
                onClick = {
                    if (block.kind == BlockKind.TASK) onOpenTask(block.id) else onOpenCalendar()
                }
            )
        }
    }
}

/**
 * Một dòng của timeline: [giờ bắt đầu/kết thúc] [đường dọc có chấm] [thẻ nội dung].
 */
@Composable
private fun TimelineRow(
    block: TimeBlock,
    isConflict: Boolean,
    isHappeningNow: Boolean,
    onToggleTask: () -> Unit,
    onClick: () -> Unit
) {
    val color = block.categoryColor()
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min), // để đường dọc cao bằng thẻ
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cột giờ
        Column(modifier = Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
            Text(Fmt.time(block.start), style = MaterialTheme.typography.labelLarge)
            Text(
                Fmt.time(block.end),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Đường dọc + chấm tròn, tự vẽ bằng drawBehind (vẽ phía sau một Box rỗng).
        Box(
            modifier = Modifier
                .width(14.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(lineColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2.dp.toPx())
                    drawCircle(color, radius = 5.dp.toPx(), center = Offset(size.width / 2, 12.dp.toPx()))
                }
        )

        // Thẻ nội dung
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (isHappeningNow) 0.22f else 0.12f)),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Chỉ công việc mới có nút tick; sự kiện lịch thì không.
                if (block.kind == BlockKind.TASK) {
                    CircleCheck(checked = block.isDone, color = color, onClick = onToggleTask, size = 24.dp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(block.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.duration(block.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isHappeningNow) Pill("Đang diễn ra", color)
                        if (isConflict) Pill("Trùng giờ", DangerRed, icon = Icons.Rounded.Warning)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4–7. Các danh sách còn lại
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.todoSection(
    tasks: List<TaskWithSubtasks>,
    now: Long,
    onToggle: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    if (tasks.isEmpty()) return
    item(key = "todo-header") { SectionHeader("Việc cần làm", subtitle = "Đến hạn hôm nay, chưa xếp giờ") }
    items(tasks, key = { "todo-${it.task.id}" }) { item ->
        TaskCard(
            item = item,
            now = now,
            onToggleDone = { onToggle(item.task.id) },
            onClick = { onOpen(item.task.id) }
        )
    }
}

private fun LazyListScope.notesSection(notes: List<Note>, onOpenNote: (Long) -> Unit) {
    if (notes.isEmpty()) return
    item(key = "notes-header") { SectionHeader("Ghi chú hôm nay") }
    items(notes, key = { "note-${it.id}" }) { note ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            onClick = { onOpenNote(note.id) }
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    note.title.ifBlank { "Ghi chú" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.content.isNotBlank()) {
                    Text(
                        note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun LazyListScope.overdueSection(
    tasks: List<TaskWithSubtasks>,
    now: Long,
    onToggle: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    if (tasks.isEmpty()) return
    item(key = "overdue-header") {
        SectionHeader(
            title = "Quá hạn",
            subtitle = "${tasks.size} việc cần xử lý",
            action = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerRed) }
        )
    }
    items(tasks, key = { "overdue-${it.task.id}" }) { item ->
        TaskCard(item, onToggleDone = { onToggle(item.task.id) }, onClick = { onOpen(item.task.id) }, now = now)
    }
}

private fun LazyListScope.upcomingSection(
    tasks: List<TaskWithSubtasks>,
    now: Long,
    onToggle: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    if (tasks.isEmpty()) return
    item(key = "upcoming-header") { SectionHeader("Sắp tới") }
    items(tasks.take(5), key = { "upcoming-${it.task.id}" }) { item ->
        TaskCard(item, onToggleDone = { onToggle(item.task.id) }, onClick = { onOpen(item.task.id) }, now = now)
    }
}
