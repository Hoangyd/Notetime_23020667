package com.example.notes.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.domain.Category
import com.example.notes.domain.Note
import com.example.notes.domain.Priority
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.CircleCheck
import com.example.notes.ui.components.DateField
import com.example.notes.ui.components.TimeField
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label
import com.example.notes.ui.util.Fmt

// ===============================================================================================
// FORM SOẠN CÔNG VIỆC (tạo mới / sửa)
//
// Quy trình: người dùng sửa các ô → mỗi lần sửa gọi viewModel.update { ... } → form đổi →
// giao diện vẽ lại với giá trị mới. Chỉ khi bấm "Lưu" mới ghi vào database.
//
// Các khối: tên + mô tả, danh mục, ưu tiên, hạn chót, thời lượng, xếp vào lịch, việc con, ghi chú liên kết.
// ===============================================================================================

/** Các lựa chọn nhanh cho thời lượng dự kiến (đơn vị: phút). */
private val EstimateOptions = listOf(15, 30, 45, 60, 90, 120, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    taskId: Long,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNoteForTask: (Long) -> Unit,
    // `key` riêng cho từng công việc để hai form khác nhau không dùng chung một ViewModel.
    viewModel: TaskEditorViewModel = appViewModel(key = "task-editor-$taskId") { TaskEditorViewModel(it, taskId) }
) {
    val form = viewModel.form // form hiện tại (đọc từ ViewModel)
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val linkedNotes by viewModel.linkedNotes.collectAsStateWithLifecycle(initialValue = emptyList())

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditorTopBar(
                isNew = viewModel.isNew,
                onBack = onBack,
                onDelete = { showDeleteDialog = true },
                onSave = { viewModel.save(onSaved = onBack) }
            )
        }
    ) { padding ->
        // Đang tải dữ liệu để sửa: hiện khung trống thay vì form rỗng.
        if (!viewModel.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding() // đẩy nội dung lên khi bàn phím hiện
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tên và mô tả
            OutlinedTextField(
                value = form.title,
                onValueChange = { newTitle -> viewModel.update { it.copy(title = newTitle) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tên công việc") },
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { newText -> viewModel.update { it.copy(description = newText) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mô tả (tuỳ chọn)") },
                minLines = 2,
                shape = RoundedCornerShape(16.dp)
            )

            CategorySection(selected = form.category) { picked -> viewModel.update { it.copy(category = picked) } }

            PrioritySection(selected = form.priority) { picked -> viewModel.update { it.copy(priority = picked) } }

            DeadlineSection(
                day = form.deadlineDay,
                minutes = form.deadlineMinutes,
                onDayChange = viewModel::setDeadlineDay,
                onMinutesChange = { m -> viewModel.update { it.copy(deadlineMinutes = m) } }
            )

            EstimateSection(selected = form.estimatedMinutes) { picked ->
                viewModel.update { it.copy(estimatedMinutes = picked) }
            }

            ScheduleSection(form = form, viewModel = viewModel)

            SubtasksSection(
                subtasks = subtasks,
                onAdd = viewModel::addSubtask,
                onToggle = viewModel::toggleSubtask,
                onRemove = viewModel::removeSubtask
            )

            // Ghi chú liên kết chỉ có ý nghĩa khi công việc đã tồn tại.
            if (!viewModel.isNew) {
                LinkedNotesSection(
                    notes = linkedNotes,
                    onOpenNote = onOpenNote,
                    onNewNote = { onNewNoteForTask(taskId) }
                )
            }

            Box(Modifier.height(24.dp)) // khoảng trống cuối trang
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onDeleted = onBack)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Thanh trên cùng và hộp thoại xác nhận xoá
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(isNew: Boolean, onBack: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit) {
    TopAppBar(
        title = { Text(if (isNew) "Công việc mới" else "Sửa công việc") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
            }
        },
        actions = {
            // Công việc mới chưa có gì để xoá nên không hiện nút xoá.
            if (!isNew) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Xoá", tint = DangerRed)
                }
            }
            Button(onClick = onSave, modifier = Modifier.padding(end = 8.dp)) { Text("Lưu") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xoá công việc?") },
        text = { Text("Công việc và các việc con sẽ bị xoá vĩnh viễn.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Xoá", color = DangerRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Các khối của form. Mỗi khối là một thẻ có tiêu đề (EditorSection) chứa các ô nhập.
// ---------------------------------------------------------------------------------------------

/** Khung chung: thẻ bo góc + tiêu đề màu chính + nội dung bên trong. */
@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun CategorySection(selected: Category, onSelect: (Category) -> Unit) {
    EditorSection("Danh mục") {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in Category.entries) {
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) },
                    leadingIcon = { Icon(option.icon, null, tint = option.color, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrioritySection(selected: Priority, onSelect: (Priority) -> Unit) {
    EditorSection("Mức ưu tiên") {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Priority.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Priority.entries.size),
                    icon = {} // bỏ dấu tick mặc định, ta tự đặt cờ màu bên dưới
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Flag, null, tint = option.color, modifier = Modifier.size(16.dp))
                        Text(option.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadlineSection(
    day: Long?,
    minutes: Int,
    onDayChange: (Long?) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    EditorSection("Hạn chót") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(
                label = "Ngày",
                epochDay = day,
                placeholder = "Không có hạn",
                onChange = onDayChange,
                modifier = Modifier.weight(1f)
            )
            // Chỉ cần chọn giờ khi đã có ngày.
            if (day != null) {
                TimeField(
                    label = "Giờ",
                    minutes = minutes,
                    placeholder = "",
                    onChange = { picked -> if (picked != null) onMinutesChange(picked) },
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun EstimateSection(selected: Int?, onSelect: (Int?) -> Unit) {
    EditorSection("Thời lượng dự kiến") {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (minutes in EstimateOptions) {
                FilterChip(
                    selected = selected == minutes,
                    // Bấm lại chip đang chọn thì bỏ chọn.
                    onClick = { onSelect(if (selected == minutes) null else minutes) },
                    label = { Text(Fmt.minutes(minutes)) }
                )
            }
        }
    }
}

@Composable
private fun ScheduleSection(form: TaskForm, viewModel: TaskEditorViewModel) {
    EditorSection("Xếp vào lịch") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Đặt giờ làm cụ thể", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Hiện thành khối thời gian trên lịch và phát hiện trùng giờ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = form.scheduleEnabled, onCheckedChange = viewModel::setScheduleEnabled)
        }

        // Các ô ngày / giờ chỉ hiện khi công tắc đang bật.
        if (form.scheduleEnabled) {
            DateField(
                label = "Ngày làm",
                epochDay = form.scheduleDay,
                placeholder = "",
                clearable = false,
                onChange = { picked -> if (picked != null) viewModel.update { it.copy(scheduleDay = picked) } },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = "Bắt đầu",
                    minutes = form.startMinutes,
                    placeholder = "",
                    onChange = { picked ->
                        if (picked != null) {
                            // Đổi giờ bắt đầu thì dời giờ kết thúc theo để giữ nguyên thời lượng.
                            viewModel.update {
                                val duration = (it.endMinutes - it.startMinutes).coerceAtLeast(15)
                                it.copy(startMinutes = picked, endMinutes = (picked + duration).coerceAtMost(24 * 60 - 1))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "Kết thúc",
                    minutes = form.endMinutes,
                    placeholder = "",
                    onChange = { picked -> if (picked != null) viewModel.update { it.copy(endMinutes = picked) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SubtasksSection(
    subtasks: List<SubtaskItem>,
    onAdd: (String) -> Unit,
    onToggle: (SubtaskItem) -> Unit,
    onRemove: (SubtaskItem) -> Unit
) {
    // Chữ đang gõ trong ô "Thêm việc con" – trạng thái cục bộ, không cần đưa lên ViewModel.
    var draft by remember { mutableStateOf("") }

    /** Thêm việc con từ chữ đang gõ rồi xoá ô nhập. */
    fun submitDraft() {
        onAdd(draft)
        draft = ""
    }

    EditorSection("Việc con") {
        // Thanh tiến độ khi đã có việc con
        if (subtasks.isNotEmpty()) {
            val doneCount = subtasks.count { it.done }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / subtasks.size },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    strokeCap = StrokeCap.Round
                )
                Text("$doneCount/${subtasks.size}", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Danh sách việc con
        for (subtask in subtasks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircleCheck(
                    checked = subtask.done,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onToggle(subtask) },
                    size = 22.dp
                )
                Text(
                    subtask.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
                    color = if (subtask.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onRemove(subtask) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Xoá việc con", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Ô thêm việc con mới
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Thêm việc con…") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitDraft() }),
            trailingIcon = {
                IconButton(onClick = { submitDraft() }, enabled = draft.isNotBlank()) {
                    Icon(Icons.Rounded.Add, contentDescription = "Thêm")
                }
            }
        )
    }
}

@Composable
private fun LinkedNotesSection(notes: List<Note>, onOpenNote: (Long) -> Unit, onNewNote: () -> Unit) {
    EditorSection("Ghi chú liên kết") {
        for (note in notes) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNote(note.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    note.title.ifBlank { note.content.take(40) }.ifBlank { "Ghi chú" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        OutlinedButton(onClick = onNewNote) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Ghi chú mới cho việc này", modifier = Modifier.padding(start = 6.dp))
        }
    }
}
