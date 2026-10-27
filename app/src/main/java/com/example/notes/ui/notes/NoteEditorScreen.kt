package com.example.notes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notes.domain.Category
import com.example.notes.ui.appViewModel
import com.example.notes.ui.components.DateField
import com.example.notes.ui.components.PickerField
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.LocalDarkTheme
import com.example.notes.ui.theme.NotePalette
import com.example.notes.ui.theme.label
import com.example.notes.ui.theme.noteContainerColor

// ===============================================================================================
// FORM SOẠN GHI CHÚ
//
// Khác form công việc: ở đây KHÔNG có nút "Lưu". Mọi thay đổi được tự lưu nháp sau 0,7 giây
// (xem NoteEditorViewModel). Bấm "Xong" hoặc Quay lại thì ghi chú thành chính thức.
// Màu nền của cả màn hình đổi theo màu ghi chú đã chọn.
// ===============================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    presetTaskId: Long?,
    presetDate: Long?,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = appViewModel(key = "note-editor-$noteId-$presetTaskId-$presetDate") {
        NoteEditorViewModel(it, noteId, presetTaskId, presetDate)
    }
) {
    val form = viewModel.form
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    val dark = LocalDarkTheme.current
    val backgroundColor = noteContainerColor(form.color, dark, MaterialTheme.colorScheme.background)

    // Nút Back của điện thoại cũng phải "kết thúc soạn" (lưu) thay vì thoát ngay.
    BackHandler { viewModel.finish(onBack) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            NoteTopBar(
                saved = viewModel.saved,
                pinned = form.pinned,
                isChecklist = form.checklist,
                onBack = { viewModel.finish(onBack) },
                onTogglePin = { viewModel.update { it.copy(pinned = !it.pinned) } },
                onToggleChecklist = viewModel::toggleChecklist,
                onDelete = { viewModel.delete(onBack) },
                onDone = { viewModel.finish(onBack) }
            )
        }
    ) { padding ->
        if (!viewModel.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tiêu đề (chữ lớn, không viền)
            BorderlessField(
                value = form.title,
                onValueChange = { newTitle -> viewModel.update { it.copy(title = newTitle) } },
                placeholder = "Tiêu đề",
                textStyle = MaterialTheme.typography.headlineSmall
            )

            // Nội dung: checklist hoặc văn bản tuỳ chế độ
            if (form.checklist) {
                ChecklistEditor(viewModel)
            } else {
                BorderlessField(
                    value = form.content,
                    onValueChange = { newText -> viewModel.update { it.copy(content = newText) } },
                    placeholder = "Viết ghi chú…",
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 8
                )
            }

            ColorPicker(selected = form.color, onSelect = { color -> viewModel.update { it.copy(color = color) } })

            CategorySection(category = form.category, onChange = { text -> viewModel.update { it.copy(category = text) } })

            LabelsSection(labels = form.labels, onAdd = viewModel::addLabel, onRemove = viewModel::removeLabel)

            LinksSection(
                tasks = tasks.map { it.id to it.title },
                linkedTaskId = form.linkedTaskId,
                linkedDate = form.linkedDate,
                onTaskChange = { id -> viewModel.update { it.copy(linkedTaskId = id) } },
                onDateChange = { day -> viewModel.update { it.copy(linkedDate = day) } }
            )

            Box(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Thanh trên cùng
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteTopBar(
    saved: Boolean,
    pinned: Boolean,
    isChecklist: Boolean,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleChecklist: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    TopAppBar(
        title = {
            // Chữ "Đã lưu nháp" chỉ hiện sau khi tự lưu xong.
            if (saved) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp), tint = inactiveColor)
                    Text("Đã lưu nháp", style = MaterialTheme.typography.labelMedium, color = inactiveColor)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
            }
        },
        actions = {
            IconButton(onClick = onTogglePin) {
                Icon(Icons.Rounded.PushPin, contentDescription = "Ghim", tint = if (pinned) activeColor else inactiveColor)
            }
            IconButton(onClick = onToggleChecklist) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                    contentDescription = "Checklist",
                    tint = if (isChecklist) activeColor else inactiveColor
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Xoá", tint = DangerRed)
            }
            Button(onClick = onDone, modifier = Modifier.padding(end = 8.dp)) { Text("Xong") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

// ---------------------------------------------------------------------------------------------
// Ô nhập không viền, và editor checklist
// ---------------------------------------------------------------------------------------------

/** Ô nhập trông như chữ viết thẳng lên giấy: nền trong suốt, không có đường viền. */
@Composable
private fun BorderlessField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    minLines: Int = 1
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        },
        textStyle = textStyle,
        minLines = minLines,
        colors = transparentFieldColors()
    )
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)

/** Danh sách các mục checklist: [ô tick] [ô nhập] [nút xoá], và nút "Thêm mục". */
@Composable
private fun ChecklistEditor(viewModel: NoteEditorViewModel) {
    val rows = viewModel.form.rows

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in rows) {
            // Mỗi dòng có một FocusRequester riêng để ViewModel yêu cầu "đặt con trỏ vào dòng này".
            val focusRequester = remember(row.key) { FocusRequester() }
            LaunchedEffect(viewModel.focusKey) {
                if (viewModel.focusKey == row.key) focusRequester.requestFocus()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.done, onCheckedChange = { viewModel.toggleRow(row.key) })
                TextField(
                    value = row.text,
                    onValueChange = { viewModel.updateRow(row.key, it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Mục") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (row.done) TextDecoration.LineThrough else null
                    ),
                    singleLine = true,
                    // Phím Enter/Next trên bàn phím = thêm một mục mới ngay bên dưới.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { viewModel.addRowAfter(row.key) }),
                    colors = transparentFieldColors()
                )
                IconButton(onClick = { viewModel.removeRow(row.key) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Xoá mục", modifier = Modifier.size(18.dp))
                }
            }
        }

        AssistChip(
            onClick = { viewModel.addRowAfter(null) },
            label = { Text("Thêm mục") },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Chọn màu, danh mục, nhãn, liên kết
// ---------------------------------------------------------------------------------------------

/**
 * Hàng ô tròn màu. Màu được lưu dưới dạng số nguyên ARGB (`toArgb()`); số 0 nghĩa là "không màu".
 */
@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val dark = LocalDarkTheme.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Màu ghi chú")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (paletteColor in NotePalette) {
                val value = if (paletteColor == Color.Transparent) 0 else paletteColor.toArgb()
                val isSelected = selected == value

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(noteContainerColor(value, dark, MaterialTheme.colorScheme.surface))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CategorySection(category: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Danh mục")
        OutlinedTextField(
            value = category,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ví dụ: Học tập, Mua sắm…") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        // Gợi ý nhanh: bấm chip để điền tên danh mục có sẵn.
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in Category.entries) {
                FilterChip(
                    selected = category == option.label,
                    onClick = { onChange(option.label) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun LabelsSection(labels: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var draft by remember { mutableStateOf("") } // chữ đang gõ trong ô thêm nhãn

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Nhãn")

        if (labels.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (label in labels) {
                    // Bấm vào nhãn (có dấu X) để bỏ nhãn đó.
                    InputChip(
                        selected = false,
                        onClick = { onRemove(label) },
                        label = { Text("#$label") },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Bỏ nhãn", modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Thêm nhãn rồi bấm Xong") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAdd(draft)
                draft = ""
            })
        )
    }
}

/**
 * Liên kết ghi chú với một công việc và/hoặc một ngày.
 * [tasks] là danh sách (id, tên) của các công việc có thể chọn.
 */
@Composable
private fun LinksSection(
    tasks: List<Pair<Long, String>>,
    linkedTaskId: Long?,
    linkedDate: Long?,
    onTaskChange: (Long?) -> Unit,
    onDateChange: (Long?) -> Unit
) {
    var taskMenuOpen by remember { mutableStateOf(false) }
    val linkedTaskName = tasks.firstOrNull { it.first == linkedTaskId }?.second
        ?: linkedTaskId?.let { "Công việc #$it" } // công việc đã xong nên không còn trong danh sách

    SectionTitle("Liên kết")

    Box {
        PickerField(
            label = "Công việc",
            value = linkedTaskName,
            placeholder = "Không liên kết",
            icon = Icons.Rounded.TaskAlt,
            onClick = { taskMenuOpen = true },
            onClear = { onTaskChange(null) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = taskMenuOpen, onDismissRequest = { taskMenuOpen = false }) {
            if (tasks.isEmpty()) {
                DropdownMenuItem(text = { Text("Chưa có công việc đang mở") }, onClick = { taskMenuOpen = false }, enabled = false)
            }
            for ((id, title) in tasks) {
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        taskMenuOpen = false
                        onTaskChange(id)
                    }
                )
            }
        }
    }

    DateField(
        label = "Ngày",
        epochDay = linkedDate,
        placeholder = "Không gắn ngày",
        onChange = onDateChange,
        modifier = Modifier.fillMaxWidth()
    )
}
