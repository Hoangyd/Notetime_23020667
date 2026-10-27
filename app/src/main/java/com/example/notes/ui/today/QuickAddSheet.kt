package com.example.notes.ui.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.notes.domain.Category
import com.example.notes.domain.Priority
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label

/**
 * Tờ giấy trượt từ dưới lên ("bottom sheet") để thêm nhanh một công việc hoặc ghi chú.
 *
 * Các biến `var x by remember { mutableStateOf(...) }` bên dưới là TRẠNG THÁI CỤC BỘ của form:
 * khi người dùng gõ hoặc chọn, giá trị đổi và giao diện tự vẽ lại. Khi đóng tờ giấy, chúng bị bỏ đi.
 * Chỉ khi bấm "Thêm" thì dữ liệu mới được chuyển lên ViewModel (qua onAddTask / onAddNote).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onAddTask: (title: String, category: Category, priority: Priority, dueToday: Boolean) -> Unit,
    onAddNote: (String) -> Unit,
    onOpenFullTask: () -> Unit,
    onOpenFullNote: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isTask by remember { mutableStateOf(true) } // true = thêm công việc, false = thêm ghi chú
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.PERSONAL) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var dueToday by remember { mutableStateOf(true) }

    // FocusRequester dùng để tự động đặt con trỏ vào ô nhập khi mở form.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isTask) { focusRequester.requestFocus() }

    /** Gửi dữ liệu lên ViewModel rồi đóng form. */
    fun submit() {
        if (text.isBlank()) return
        if (isTask) onAddTask(text, category, priority, dueToday) else onAddNote(text)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()) // cho phép cuộn nếu màn hình nhỏ
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Thêm nhanh", style = MaterialTheme.typography.titleLarge)

            // Hai nút chọn: Công việc | Ghi chú
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isTask,
                    onClick = { isTask = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Công việc") }
                SegmentedButton(
                    selected = !isTask,
                    onClick = { isTask = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Ghi chú") }
            }

            // Ô nhập nội dung
            OutlinedTextField(
                value = text,
                onValueChange = { text = it }, // mỗi lần gõ, cập nhật biến `text`
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(if (isTask) "Bạn cần làm gì?" else "Ghi nhanh điều gì đó…") },
                minLines = if (isTask) 1 else 3,
                singleLine = isTask,
                keyboardOptions = KeyboardOptions(imeAction = if (isTask) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { submit() }) // bấm Enter trên bàn phím = thêm
            )

            // Các tuỳ chọn chỉ có ở công việc
            if (isTask) {
                TaskOptions(
                    category = category,
                    onCategoryChange = { category = it },
                    priority = priority,
                    onPriorityChange = { priority = it },
                    dueToday = dueToday,
                    onDueTodayChange = { dueToday = it }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = if (isTask) onOpenFullTask else onOpenFullNote) {
                    Text("Mở trình soạn đầy đủ")
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(onClick = ::submit, enabled = text.isNotBlank()) { Text("Thêm") }
                }
            }
        }
    }
}

/** Chọn danh mục, mức ưu tiên và "hạn trong hôm nay". */
@Composable
private fun TaskOptions(
    category: Category,
    onCategoryChange: (Category) -> Unit,
    priority: Priority,
    onPriorityChange: (Priority) -> Unit,
    dueToday: Boolean,
    onDueTodayChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hàng chip danh mục, cuộn ngang được
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Category.entries.forEach { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { onCategoryChange(option) },
                    label = { Text(option.label) },
                    leadingIcon = { Icon(option.icon, null, tint = option.color, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Hàng chip mức ưu tiên
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Priority.entries.forEach { option ->
                FilterChip(
                    selected = priority == option,
                    onClick = { onPriorityChange(option) },
                    label = { Text(option.label) },
                    leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = option.color, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Công tắc "Hạn trong hôm nay"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hạn trong hôm nay", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = dueToday, onCheckedChange = onDueTodayChange)
        }
    }
}
