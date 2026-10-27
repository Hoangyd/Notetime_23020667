package com.example.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.util.Fmt

// File này chứa các mảnh giao diện nhỏ được dùng ở nhiều màn hình.
// Mẹo đọc: mỗi hàm @Composable nhận dữ liệu qua tham số và (nếu có tương tác) nhận
// các hàm "báo cáo lên trên" như onClick, onChange. Hàm không tự giữ dữ liệu quan trọng.

// ---------------------------------------------------------------------------------------------
// Vòng tròn tiến độ
// ---------------------------------------------------------------------------------------------

/**
 * Vòng tròn hiển thị tiến độ từ 0.0 đến 1.0, có thể đặt nội dung (ví dụ chữ "75%") ở giữa.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.18f), // vòng nền mờ phía sau
    content: @Composable () -> Unit = {}
) {
    // animateFloatAsState: khi `progress` đổi, giá trị `animated` trượt dần từ cũ sang mới (hiệu ứng mượt).
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f), // coerceIn: ép giá trị nằm trong khoảng 0..1
        animationSpec = tween(durationMillis = 700),
        label = "ring"
    )

    // Box xếp các con chồng lên nhau: Canvas vẽ vòng, `content()` nằm đè ở giữa.
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Canvas cho phép tự vẽ hình bằng code. Trong khối này `this.size` là kích thước vùng vẽ (pixel).
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2 // thụt vào nửa nét vẽ để nét không bị cắt ở mép
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            // 1) vòng nền: cung 360 độ
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            // 2) vòng tiến độ: bắt đầu từ -90 độ (đỉnh vòng tròn), quét theo tỉ lệ progress
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = stroke)
            }
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Tiêu đề mục, trạng thái trống, nhãn nhỏ
// ---------------------------------------------------------------------------------------------

/** Tiêu đề của một mục trong danh sách, kèm dòng mô tả nhỏ và (tuỳ chọn) một nút bên phải. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // weight(1f): chiếm hết chỗ còn lại, đẩy `action` sang mép phải.
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

/** Hiện khi danh sách trống: biểu tượng tròn + tiêu đề + gợi ý. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape) // cắt thành hình tròn
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null, // biểu tượng trang trí, không cần đọc cho người khiếm thị
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Nhãn nhỏ bo tròn có màu (ví dụ "Học tập", "Quá hạn"), có thể kèm biểu tượng. */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f)) // nền = màu chính nhưng mờ
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Một dòng gồm biểu tượng nhỏ + chữ, dùng cho thông tin phụ ("Hôm nay 18:00", "30p"). */
@Composable
fun MetaText(text: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

// ---------------------------------------------------------------------------------------------
// Ô chọn ngày / giờ
// ---------------------------------------------------------------------------------------------

/**
 * Một "ô" bấm được hiển thị giá trị (hoặc chữ gợi ý) và mở hộp chọn khi bấm.
 * Đây là phần giao diện thuần; việc mở hộp chọn nằm ở [DateField] và [TimeField].
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null // nếu có, hiện nút X để xoá giá trị
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value ?: placeholder, // chưa có giá trị thì hiện chữ gợi ý
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
        if (onClear != null && value != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Xoá", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Hộp thoại chọn ngày (lịch tháng của Material).
 * Hộp thoại trả về mili-giây theo giờ UTC nên ta đổi qua lại với "epochDay".
 */
@OptIn(ExperimentalMaterial3Api::class) // DatePicker còn "thử nghiệm" nên phải khai báo đã biết
@Composable
fun DatePickerDialogCompat(
    initialEpochDay: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(Math.floorDiv(it, MILLIS_PER_DAY)) }
                onDismiss()
            }) { Text("Chọn") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    ) {
        DatePicker(state = state)
    }
}

/** Hộp thoại chọn giờ (mặt đồng hồ). Giá trị vào/ra tính bằng "số phút kể từ nửa đêm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("Chọn") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}

/**
 * Ô chọn NGÀY. Bấm vào thì mở hộp chọn ngày.
 * [epochDay] = null nghĩa là chưa chọn. [onChange] được gọi khi người dùng chọn hoặc xoá.
 */
@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    placeholder: String,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true
) {
    // `showDialog` là trạng thái CỤC BỘ của ô này: đang mở hộp thoại hay không.
    var showDialog by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = epochDay?.let { Fmt.fullDate(it) },
        placeholder = placeholder,
        icon = Icons.Rounded.CalendarMonth,
        onClick = { showDialog = true },
        onClear = if (clearable) ({ onChange(null) }) else null,
        modifier = modifier
    )

    if (showDialog) {
        DatePickerDialogCompat(
            initialEpochDay = epochDay ?: TimeUtils.today(),
            onConfirm = { onChange(it) },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Ô chọn GIỜ. [minutes] = số phút kể từ nửa đêm (ví dụ 9 giờ 30 = 570), null = chưa chọn.
 */
@Composable
fun TimeField(
    label: String,
    minutes: Int?,
    placeholder: String,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = minutes?.let { Fmt.minutesOfDay(it) },
        placeholder = placeholder,
        icon = Icons.Rounded.Schedule,
        onClick = { showDialog = true },
        onClear = if (clearable) ({ onChange(null) }) else null,
        modifier = modifier
    )

    if (showDialog) {
        TimePickerDialog(
            initialMinutes = minutes ?: (9 * 60), // mặc định mở ở 09:00
            onConfirm = { onChange(it) },
            onDismiss = { showDialog = false }
        )
    }
}
