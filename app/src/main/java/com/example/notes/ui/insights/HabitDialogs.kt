package com.example.notes.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.notes.data.HabitEntity
import com.example.notes.domain.Category
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label
import com.example.notes.ui.util.Fmt

/**
 * Các biểu tượng có thể chọn cho một thói quen. Database chỉ lưu "khoá" chữ (ví dụ "book"),
 * hàm [of] đổi khoá đó thành biểu tượng để vẽ.
 */
object HabitIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "fitness" to Icons.Rounded.FitnessCenter,
        "language" to Icons.Rounded.Translate,
        "water" to Icons.Rounded.WaterDrop,
        "sleep" to Icons.Rounded.Bedtime,
        "meditate" to Icons.Rounded.SelfImprovement,
        "code" to Icons.Rounded.Code,
        "music" to Icons.Rounded.MusicNote
    )

    /** Khoá lạ hoặc rỗng thì dùng hình ngôi sao. */
    fun of(key: String): ImageVector = all[key] ?: Icons.Rounded.Star
}

// ---------------------------------------------------------------------------------------------
// Hộp thoại thêm / sửa thói quen
// ---------------------------------------------------------------------------------------------

/**
 * @param initial thói quen đang sửa; null = tạo mới
 * @param onArchive / onDelete chỉ có khi đang sửa
 */
@Composable
fun HabitDialog(
    initial: HabitEntity?,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    // Giá trị các ô trong form (khởi tạo từ thói quen đang sửa, hoặc giá trị mặc định).
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(initial?.icon?.ifBlank { "book" } ?: "book") }
    var category by remember { mutableStateOf(Category.fromKey(initial?.category)) }
    var daysMask by remember { mutableIntStateOf(initial?.daysMask ?: 0b1111111) } // mặc định: mọi ngày
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thói quen mới" else "Sửa thói quen") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên thói quen") },
                    placeholder = { Text("Đọc sách, tập thể dục…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Text("Biểu tượng", style = MaterialTheme.typography.labelLarge)
                IconPicker(selectedKey = iconKey, accent = category.color, onSelect = { iconKey = it })

                Text("Danh mục", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in Category.entries) {
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.label) },
                            leadingIcon = { Icon(option.icon, null, tint = option.color, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                Text("Lặp lại vào", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fmt.weekdayLabels.forEachIndexed { index, label ->
                        val bit = 1 shl index // T2 = bit 0, ..., CN = bit 6
                        FilterChip(
                            selected = daysMask and bit != 0,
                            onClick = { daysMask = daysMask xor bit }, // bật/tắt bit này
                            label = { Text(label) }
                        )
                    }
                }

                // Các nút chỉ có khi đang sửa
                if (initial != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onArchive != null) {
                            TextButton(onClick = { onArchive(); onDismiss() }) { Text("Lưu trữ") }
                        }
                        if (onDelete != null) {
                            TextButton(onClick = { showDeleteConfirm = true }) { Text("Xoá", color = DangerRed) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && daysMask != 0, // cần có tên và ít nhất một ngày
                onClick = {
                    val base = initial ?: HabitEntity(name = name)
                    onSave(base.copy(name = name.trim(), icon = iconKey, category = category.name, daysMask = daysMask))
                    onDismiss()
                }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xoá thói quen?") },
            text = { Text("Toàn bộ lịch sử và chuỗi ngày của thói quen này sẽ bị xoá.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false; onDismiss() }) {
                    Text("Xoá", color = DangerRed)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Huỷ") } }
        )
    }
}

/** Hàng các ô tròn biểu tượng; ô đang chọn được tô màu và có viền. */
@Composable
private fun IconPicker(selectedKey: String, accent: Color, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, vector) in HabitIcons.all) {
            val isSelected = key == selectedKey

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) accent else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(vector, contentDescription = null, tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hộp thoại ghi nhận thời gian
// ---------------------------------------------------------------------------------------------

/** Các lựa chọn nhanh cho thời lượng (phút). */
private val DurationOptions = listOf(15, 30, 45, 60, 90, 120, 180)

/** Hộp thoại "Bạn vừa dành bao lâu cho việc gì?" – chọn danh mục và thời lượng. */
@Composable
fun LogTimeDialog(
    onDismiss: () -> Unit,
    onSave: (Category, Int) -> Unit
) {
    var category by remember { mutableStateOf(Category.STUDY) }
    var minutes by remember { mutableIntStateOf(60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi nhận thời gian") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bạn vừa dành bao lâu cho việc gì? Thời gian được tính đến thời điểm hiện tại.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in Category.entries) {
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.label) },
                            leadingIcon = { Icon(option.icon, null, tint = option.color, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in DurationOptions) {
                        FilterChip(selected = minutes == option, onClick = { minutes = option }, label = { Text(Fmt.minutes(option)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(category, minutes); onDismiss() }) { Text("Ghi nhận") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}
