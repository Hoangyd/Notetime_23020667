package com.example.notes.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.data.TaskWithSubtasks
import com.example.notes.domain.Category
import com.example.notes.domain.Priority
import com.example.notes.domain.TaskFilter
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.EmptyState
import com.example.notes.ui.components.TaskCard
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.SuccessGreen
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label

// ===============================================================================================
// MÀN HÌNH "CÔNG VIỆC"
// Từ trên xuống: tiêu đề → ô tìm kiếm → chip lọc trạng thái → chip lọc danh mục/ưu tiên → danh sách.
// Mỗi dòng danh sách có thể vuốt: phải = hoàn thành, trái = xoá.
// ===============================================================================================

/** Tên hiển thị của từng bộ lọc trạng thái. */
private fun TaskFilter.title(): String = when (this) {
    TaskFilter.ALL -> "Tất cả"
    TaskFilter.TODAY -> "Hôm nay"
    TaskFilter.UPCOMING -> "Sắp tới"
    TaskFilter.OVERDUE -> "Quá hạn"
    TaskFilter.COMPLETED -> "Hoàn thành"
}

@Composable
fun TasksScreen(
    onOpenTask: (Long) -> Unit,
    onNewTask: () -> Unit,
    viewModel: TasksViewModel = appViewModel { TasksViewModel(it) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Rounded.Add, contentDescription = "Thêm công việc") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tiêu đề + ô tìm kiếm
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Công việc", style = MaterialTheme.typography.headlineMedium)
                SearchField(value = state.search, onChange = viewModel::setSearch)
            }

            // Hai hàng chip lọc
            FilterRows(state = state, viewModel = viewModel)

            // Danh sách hoặc thông báo trống
            if (state.tasks.isEmpty() && state.loaded) {
                EmptyTasks(isFiltering = isFiltering(state))
            } else {
                TaskList(
                    tasks = state.tasks,
                    onToggleDone = viewModel::toggleDone,
                    onDelete = viewModel::delete,
                    onOpen = onOpenTask
                )
            }
        }
    }
}

/** Có đang áp dụng bộ lọc / tìm kiếm nào không? (để chọn lời nhắn khi danh sách trống) */
private fun isFiltering(state: TasksState): Boolean =
    state.search.isNotBlank() || state.category != null || state.priority != null || state.filter != TaskFilter.ALL

@Composable
private fun EmptyTasks(isFiltering: Boolean) {
    EmptyState(
        icon = Icons.Rounded.TaskAlt,
        title = if (isFiltering) "Không có công việc phù hợp" else "Chưa có công việc nào",
        subtitle = if (isFiltering) "Thử đổi bộ lọc hoặc từ khoá tìm kiếm." else "Bấm dấu + để tạo công việc đầu tiên."
    )
}

// ---------------------------------------------------------------------------------------------
// Ô tìm kiếm và các chip lọc
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Tìm công việc…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            // Nút X xoá nhanh, chỉ hiện khi đã gõ chữ.
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Xoá tìm kiếm")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun FilterRows(state: TasksState, viewModel: TasksViewModel) {
    Column(
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Hàng 1: lọc theo trạng thái. horizontalScroll cho phép vuốt ngang khi không đủ chỗ.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (option in TaskFilter.entries) {
                FilterChip(
                    selected = state.filter == option,
                    onClick = { viewModel.setFilter(option) },
                    label = { Text(option.title()) }
                )
            }
        }

        // Hàng 2: lọc theo ưu tiên (menu thả xuống) và theo danh mục.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PriorityMenu(selected = state.priority, onSelect = viewModel::setPriority)

            for (option in Category.entries) {
                val isSelected = state.category == option
                FilterChip(
                    selected = isSelected,
                    // Bấm lại chip đang chọn thì bỏ chọn (về null = mọi danh mục).
                    onClick = { viewModel.setCategory(if (isSelected) null else option) },
                    label = { Text(option.label) },
                    leadingIcon = { Icon(option.icon, null, tint = option.color, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

/** Chip "Ưu tiên" mở ra một menu thả xuống để chọn mức. */
@Composable
private fun PriorityMenu(selected: Priority?, onSelect: (Priority?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selected != null,
            onClick = { menuOpen = true },
            label = { Text(if (selected != null) "Ưu tiên: ${selected.label}" else "Ưu tiên") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = selected?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Mọi mức") },
                onClick = { onSelect(null); menuOpen = false }
            )
            for (option in Priority.entries.reversed()) { // Cao → Vừa → Thấp
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = option.color) },
                    onClick = { onSelect(option); menuOpen = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Danh sách và dòng có thể vuốt
// ---------------------------------------------------------------------------------------------

@Composable
private fun TaskList(
    tasks: List<TaskWithSubtasks>,
    onToggleDone: (Long) -> Unit,
    onDelete: (TaskWithSubtasks) -> Unit,
    onOpen: (Long) -> Unit
) {
    // Tính "bây giờ" một lần cho cả danh sách (để so sánh hạn chót), tính lại khi danh sách đổi.
    val now = remember(tasks) { System.currentTimeMillis() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // `key` giúp Compose nhận ra đúng dòng nào là công việc nào khi danh sách thay đổi
        // (quan trọng cho hiệu ứng xoá/di chuyển và để giữ trạng thái vuốt của từng dòng).
        items(tasks, key = { it.task.id }) { item ->
            SwipeableTask(
                item = item,
                now = now,
                onToggleDone = { onToggleDone(item.task.id) },
                onDelete = { onDelete(item) },
                onClick = { onOpen(item.task.id) }
            )
        }
    }
}

/**
 * Một công việc có thể vuốt:
 *  - Vuốt PHẢI → hoàn thành (hoặc mở lại nếu đã xong). Dòng tự trượt về chỗ cũ.
 *  - Vuốt TRÁI → xoá (có thông báo Hoàn tác).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTask(
    item: TaskWithSubtasks,
    now: Long,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    // rememberUpdatedState: đảm bảo khi vuốt xong luôn gọi phiên bản MỚI NHẤT của hàm,
    // không phải bản cũ bị "đóng băng" lúc dòng này được tạo ra.
    val latestToggle by rememberUpdatedState(onToggleDone)
    val latestDelete by rememberUpdatedState(onDelete)

    val dismissState = rememberSwipeToDismissBoxState(
        // Được gọi khi người dùng vuốt đủ xa. Trả về true = để dòng biến mất, false = trượt về chỗ cũ.
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    latestToggle()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    latestDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        // Phần "nền" lộ ra phía sau khi vuốt.
        backgroundContent = { SwipeBackground(direction = dismissState.dismissDirection, isDone = item.task.isDone) }
    ) {
        // Phần nội dung chính (thẻ công việc) trượt theo ngón tay.
        TaskCard(item = item, now = now, onToggleDone = onToggleDone, onClick = onClick)
    }
}

/** Nền xanh + dấu tick khi vuốt phải, nền đỏ + thùng rác khi vuốt trái. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, isDone: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) {
        // Chưa vuốt: không vẽ gì (để thẻ trông bình thường).
        Box(Modifier.fillMaxSize())
        return
    }

    val isCompleteSwipe = direction == SwipeToDismissBoxValue.StartToEnd
    val color: Color = if (isCompleteSwipe) SuccessGreen else DangerRed
    val icon: ImageVector = when {
        !isCompleteSwipe -> Icons.Rounded.Delete
        isDone -> Icons.AutoMirrored.Rounded.Undo // việc đã xong: vuốt phải = mở lại
        else -> Icons.Rounded.Check
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(horizontal = 22.dp),
        contentAlignment = if (isCompleteSwipe) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}
