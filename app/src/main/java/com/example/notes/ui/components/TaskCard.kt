package com.example.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.notes.data.TaskWithSubtasks
import com.example.notes.data.categoryEnum
import com.example.notes.data.priorityEnum
import com.example.notes.domain.Priority
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.color
import com.example.notes.ui.theme.icon
import com.example.notes.ui.theme.label
import com.example.notes.ui.util.Fmt

/**
 * Nút tròn để đánh dấu hoàn thành: viền màu khi chưa xong, tô kín + dấu tick khi đã xong.
 */
@Composable
fun CircleCheck(
    checked: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    // Màu nền đổi mượt giữa "trong suốt" và "màu chính" khi `checked` thay đổi.
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "check-fill")

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = "Đã xong", tint = Color.White, modifier = Modifier.size(size * 0.65f))
        }
    }
}

/**
 * Thẻ hiển thị một công việc. Dùng ở màn Hôm nay và màn Công việc.
 *
 * Bố cục: [vạch màu danh mục] [nút tick] [tiêu đề + dòng thông tin + thanh tiến độ việc con] [cờ ưu tiên]
 *
 * @param item công việc kèm danh sách việc con
 * @param onToggleDone gọi khi bấm nút tick
 * @param onClick gọi khi bấm vào thẻ (để mở trình soạn)
 */
@Composable
fun TaskCard(
    item: TaskWithSubtasks,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis()
) {
    val task = item.task
    val category = task.categoryEnum
    val isOverdue = !task.isDone && task.deadline != null && task.deadline < now

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        // IntrinsicSize.Min: chiều cao của hàng = chiều cao nội dung cao nhất, để vạch màu bên trái cao bằng thẻ.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            CategoryStripe(color = category.color, faded = task.isDone)

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleCheck(checked = task.isDone, color = category.color, onClick = onToggleDone)

                // weight(1f): phần giữa chiếm hết chỗ trống, đẩy cờ ưu tiên sang phải.
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    TaskTitle(title = task.title, isDone = task.isDone)
                    TaskInfoRow(item = item, isOverdue = isOverdue, now = now)
                    if (item.subtasks.isNotEmpty()) {
                        SubtaskProgress(item = item, color = category.color)
                    }
                }

                // Chỉ hiện cờ khi ưu tiên Vừa/Cao và việc chưa xong.
                if (task.priorityEnum != Priority.LOW && !task.isDone) {
                    Icon(
                        Icons.Rounded.Flag,
                        contentDescription = "Ưu tiên ${task.priorityEnum.label}",
                        tint = task.priorityEnum.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** Vạch dọc màu của danh mục ở mép trái thẻ. Mờ đi khi việc đã xong. */
@Composable
private fun CategoryStripe(color: Color, faded: Boolean) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
            .background(color.copy(alpha = if (faded) 0.35f else 1f))
    )
}

/** Tiêu đề công việc; gạch ngang và nhạt màu khi đã xong. */
@Composable
private fun TaskTitle(title: String, isDone: Boolean) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis, // quá 2 dòng thì cắt và thêm "…"
        textDecoration = if (isDone) TextDecoration.LineThrough else null,
        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    )
}

/** Hàng thông tin: danh mục, hạn chót (đỏ nếu quá hạn), thời lượng dự kiến. */
@Composable
private fun TaskInfoRow(item: TaskWithSubtasks, isOverdue: Boolean, now: Long) {
    val task = item.task
    val category = task.categoryEnum

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pill(category.label, category.color, icon = category.icon)

        if (task.deadline != null) {
            MetaText(
                text = Fmt.deadline(task.deadline, now),
                icon = Icons.Rounded.Schedule,
                color = if (isOverdue) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (task.estimatedMinutes != null) {
            MetaText(text = Fmt.minutes(task.estimatedMinutes), icon = Icons.Rounded.Timelapse)
        }
    }
}

/** Thanh tiến độ các việc con, kèm chữ "2/5". */
@Composable
private fun SubtaskProgress(item: TaskWithSubtasks, color: Color) {
    val doneCount = item.subtasks.count { it.isDone }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { item.progress }, // 0f..1f, được tính sẵn trong TaskWithSubtasks
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
        Text(
            text = "$doneCount/${item.subtasks.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
