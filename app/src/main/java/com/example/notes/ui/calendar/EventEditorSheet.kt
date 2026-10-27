package com.example.notes.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.notes.data.EventEntity
import com.example.notes.data.categoryEnum
import com.example.notes.domain.Category
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.components.DateField
import com.example.notes.ui.components.TimeField
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label
import com.example.notes.ui.util.Fmt
import java.time.LocalDate

/**
 * Tờ giấy trượt lên để THÊM hoặc SỬA một lịch (lịch học, lịch làm, cuộc hẹn...).
 *
 * Cách hoạt động: các biến `var ... by remember { mutableStateOf(...) }` bên dưới giữ giá trị các
 * ô trong form, khởi tạo từ [initial]. Bấm "Lưu" thì ghép lại thành một EventEntity mới
 * rồi gọi [onSave]; ViewModel sẽ kiểm tra và ghi vào database.
 *
 * Về thời gian: form giữ ngày (`day`) và giờ (`startMinutes`, `endMinutes` = số phút kể từ nửa đêm),
 * khi lưu mới ghép lại thành mili-giây bằng `TimeUtils.at(day, minutes)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    initial: EventEntity,
    onDismiss: () -> Unit,
    onSave: (EventEntity) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNew = initial.id == 0L

    // ----- Giá trị các ô trong form -----
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.categoryEnum) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var day by remember { mutableStateOf(TimeUtils.epochDay(initial.startAt)) }
    var startMinutes by remember { mutableIntStateOf(TimeUtils.minutesOfDay(initial.startAt)) }
    var endMinutes by remember {
        val durationMinutes = ((initial.endAt - initial.startAt) / 60_000L).toInt()
        mutableIntStateOf((TimeUtils.minutesOfDay(initial.startAt) + durationMinutes).coerceIn(1, 24 * 60 - 1))
    }
    var repeatWeekly by remember { mutableStateOf(initial.repeatDaysMask != 0) }
    var repeatMask by remember { mutableIntStateOf(initial.repeatDaysMask) } // bit 0 = T2 ... bit 6 = CN
    var repeatUntilDay by remember { mutableStateOf(initial.repeatUntil?.let { TimeUtils.epochDay(it) }) }
    var location by remember { mutableStateOf(initial.location) }
    var description by remember { mutableStateOf(initial.description) }

    // Giờ kết thúc phải sau giờ bắt đầu (trừ khi cả ngày).
    val isTimeInvalid = !allDay && endMinutes <= startMinutes

    /** Ghép các ô thành một EventEntity, gửi đi rồi đóng tờ giấy. */
    fun submit() {
        // Nếu bật "lặp lại" mà chưa chọn thứ nào, lấy thứ của ngày đang chọn.
        val weekdayBit = 1 shl (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
        val finalMask = when {
            !repeatWeekly -> 0
            repeatMask == 0 -> weekdayBit
            else -> repeatMask
        }

        onSave(
            initial.copy(
                title = title.trim(),
                category = category.name,
                allDay = allDay,
                startAt = if (allDay) TimeUtils.dayStart(day) else TimeUtils.at(day, startMinutes),
                endAt = if (allDay) TimeUtils.dayEnd(day) else TimeUtils.at(day, endMinutes),
                location = location.trim(),
                description = description.trim(),
                repeatDaysMask = finalMask,
                repeatUntil = if (finalMask != 0) repeatUntilDay?.let { TimeUtils.at(it, 23 * 60 + 59) } else null
            )
        )
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tiêu đề và nút xoá (chỉ khi đang sửa)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "Thêm lịch" else "Sửa lịch",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (!isNew) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Text("Xoá", color = DangerRed, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tên lịch") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Chọn danh mục (quyết định màu của khối lịch)
            ChipRow {
                for (option in Category.entries) {
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            // Ngày, cả ngày, giờ
            DateField(
                label = "Ngày",
                epochDay = day,
                placeholder = "",
                clearable = false,
                onChange = { picked -> if (picked != null) day = picked },
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow(title = "Cả ngày", checked = allDay, onCheckedChange = { allDay = it })

            if (!allDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(
                        label = "Bắt đầu",
                        minutes = startMinutes,
                        placeholder = "",
                        onChange = { picked ->
                            if (picked != null) {
                                // Đổi giờ bắt đầu thì dời giờ kết thúc theo để giữ nguyên thời lượng.
                                val duration = (endMinutes - startMinutes).coerceAtLeast(15)
                                startMinutes = picked
                                endMinutes = (picked + duration).coerceAtMost(24 * 60 - 1)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TimeField(
                        label = "Kết thúc",
                        minutes = endMinutes,
                        placeholder = "",
                        onChange = { picked -> if (picked != null) endMinutes = picked },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isTimeInvalid) {
                    Text("Giờ kết thúc phải sau giờ bắt đầu", color = DangerRed, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Thời lượng: ${Fmt.minutes(endMinutes - startMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lặp lại hằng tuần
            SwitchRow(
                title = "Lặp lại hằng tuần",
                subtitle = "Phù hợp cho lịch học, ca làm cố định",
                checked = repeatWeekly,
                onCheckedChange = { repeatWeekly = it }
            )
            if (repeatWeekly) {
                ChipRow(spacing = 6) {
                    Fmt.weekdayLabels.forEachIndexed { index, label ->
                        val bit = 1 shl index // T2 = bit 0, T3 = bit 1, ...
                        FilterChip(
                            selected = repeatMask and bit != 0,
                            onClick = { repeatMask = repeatMask xor bit }, // xor: bật nếu đang tắt, tắt nếu đang bật
                            label = { Text(label) }
                        )
                    }
                }
                DateField(
                    label = "Lặp đến hết ngày",
                    epochDay = repeatUntilDay,
                    placeholder = "Không giới hạn",
                    onChange = { repeatUntilDay = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Địa điểm (tuỳ chọn)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ghi chú (tuỳ chọn)") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = ::submit,
                enabled = title.isNotBlank() && !isTimeInvalid, // chưa hợp lệ thì khoá nút Lưu
                modifier = Modifier.fillMaxWidth()
            ) { Text("Lưu") }
        }
    }
}

/** Hàng chip cuộn ngang được. */
@Composable
private fun ChipRow(spacing: Int = 8, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp)
    ) {
        content()
    }
}

/** Một dòng gồm chữ (và dòng phụ) bên trái, công tắc bật/tắt bên phải. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
