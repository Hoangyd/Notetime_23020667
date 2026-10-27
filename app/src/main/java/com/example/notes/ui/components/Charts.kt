package com.example.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.notes.data.DayCompletion
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.util.Fmt

// File này chứa các biểu đồ của màn Thống kê. Không dùng thư viện ngoài:
// biểu đồ cột dựng từ Box, biểu đồ tròn và heatmap tự vẽ bằng Canvas.

// ---------------------------------------------------------------------------------------------
// 1) Biểu đồ cột: tỉ lệ hoàn thành công việc từng ngày trong tuần
// ---------------------------------------------------------------------------------------------

/**
 * Bảy cột (mỗi ngày một cột). Chiều cao phần tô = tỉ lệ công việc đến hạn đã hoàn thành.
 * Trên đầu mỗi cột ghi "đã xong/tổng", dưới chân là thứ trong tuần.
 */
@Composable
fun WeeklyBarChart(
    days: List<DayCompletion>,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // Với mỗi ngày, vẽ một cột.
        days.forEach { day ->
            WeeklyBar(
                day = day,
                isToday = day.epochDay == todayEpochDay,
                barColor = barColor,
                modifier = Modifier.weight(1f) // 7 cột chia đều chiều ngang
            )
        }
    }
}

private val BarChartHeight = 120.dp
private val BarWidth = 22.dp

@Composable
private fun WeeklyBar(day: DayCompletion, isToday: Boolean, barColor: Color, modifier: Modifier) {
    // Ngày không có việc thì cột rỗng; ngày có việc nhưng chưa xong gì vẫn hiện một vạch mỏng (4%).
    val targetFraction = if (day.due == 0) 0f else day.rate.coerceAtLeast(0.04f)
    val fraction by animateFloatAsState(targetFraction, tween(600), label = "bar")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Chữ phía trên: "2/3" hoặc "–" nếu ngày đó không có việc
        Text(
            text = if (day.due == 0) "–" else "${day.done}/${day.due}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Cột: hộp nền mờ, bên trong là hộp màu cao theo `fraction`, căn sát đáy.
        Box(
            modifier = Modifier
                .size(width = BarWidth, height = BarChartHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(barColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarChartHeight * fraction)
                    .clip(RoundedCornerShape(8.dp))
                    .background(barColor)
            )
        }

        // Nhãn thứ; tô màu chính nếu là hôm nay
        Text(
            text = Fmt.weekdayShort(day.epochDay),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) barColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 2) Biểu đồ tròn (donut)
// ---------------------------------------------------------------------------------------------

/**
 * Biểu đồ tròn rỗng giữa. [segments] là danh sách (màu, giá trị); mỗi cung có độ dài tỉ lệ với giá trị.
 * Phần [center] là nội dung đặt ở giữa (ví dụ tổng thời gian).
 */
@Composable
fun DonutChart(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    strokeWidth: Dp = 22.dp,
    center: @Composable () -> Unit = {}
) {
    val total = segments.sumOf { it.second.toDouble() }.toFloat()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

            if (total <= 0f) {
                // Chưa có dữ liệu: vẽ vòng xám.
                drawArc(emptyColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }

            // Có nhiều hơn một cung thì chừa khe hở nhỏ giữa các cung cho dễ nhìn.
            val gap = if (segments.count { it.second > 0f } > 1) 3f else 0f
            var startAngle = -90f // -90 độ = đỉnh vòng tròn

            for ((color, value) in segments) {
                if (value <= 0f) continue
                val sweep = 360f * value / total // góc quét của cung này
                drawArc(
                    color = color,
                    startAngle = startAngle + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke)
                )
                startAngle += sweep
            }
        }
        center()
    }
}

// ---------------------------------------------------------------------------------------------
// 3) Heatmap hoạt động (giống biểu đồ đóng góp của GitHub)
// ---------------------------------------------------------------------------------------------

/**
 * Lưới ô vuông: mỗi CỘT là một tuần, mỗi HÀNG là một thứ (T2 ở trên cùng). Ô càng đậm càng nhiều hoạt động.
 *
 * @param counts số hoạt động của từng ngày (epochDay -> số lượng)
 * @param lastDay ngày cuối cùng muốn hiển thị (thường là hôm nay)
 * @param weeks số tuần hiển thị
 */
@Composable
fun ActivityHeatmap(
    counts: Map<Long, Int>,
    lastDay: Long,
    weeks: Int,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)

    // Cột cuối cùng là tuần chứa `lastDay`; lùi lại (weeks - 1) tuần để ra cột đầu tiên.
    val lastColumnStart = TimeUtils.weekStart(lastDay)
    val firstColumnStart = lastColumnStart - (weeks - 1) * 7L

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((7 * 18 - 4).dp)
    ) {
        val gap = 4.dp.toPx()
        // Kích thước một ô: chia đều chiều ngang, nhưng không quá 26dp.
        val cell = ((size.width - gap * (weeks - 1)) / weeks).coerceAtMost(26.dp.toPx())
        val gridWidth = cell * weeks + gap * (weeks - 1)
        val left = (size.width - gridWidth) / 2 // căn giữa lưới
        val rowStep = (size.height - cell) / 6f // khoảng cách dọc giữa 7 hàng

        for (week in 0 until weeks) {
            for (weekday in 0 until 7) {
                val day = firstColumnStart + week * 7L + weekday
                if (day > todayEpochDay) continue // không vẽ ngày trong tương lai

                val count = counts[day] ?: 0
                // 0 hoạt động -> màu xám; càng nhiều càng đậm (từ 30% đến 100%).
                val cellColor = if (count == 0) {
                    emptyColor
                } else {
                    color.copy(alpha = (0.3f + 0.7f * count / maxCount).coerceAtMost(1f))
                }
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(left + week * (cell + gap), weekday * rowStep),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.28f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4) So sánh kế hoạch và thực tế
// ---------------------------------------------------------------------------------------------

/**
 * Hai thanh ngang: thanh trên (nhạt) = thời gian đã lên lịch, thanh dưới (đậm) = thời gian thực tế.
 * Cả hai được chia cho [maxMs] để các danh mục so sánh được với nhau.
 */
@Composable
fun PlanActualBars(
    plannedMs: Long,
    actualMs: Long,
    maxMs: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    val max = maxMs.coerceAtLeast(1L).toFloat() // tránh chia cho 0
    val plannedFraction by animateFloatAsState(plannedMs / max, tween(600), label = "planned")
    val actualFraction by animateFloatAsState(actualMs / max, tween(600), label = "actual")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalBar(fraction = plannedFraction, color = color.copy(alpha = 0.35f))
        HorizontalBar(fraction = actualFraction, color = color)
    }
}

/** Một thanh ngang dài theo tỉ lệ [fraction] (0..1) trên nền xám. */
@Composable
private fun HorizontalBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f)) // chiếm `fraction` chiều rộng của hộp cha
                .height(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** Chấm màu + chú thích, dùng làm "legend" bên cạnh biểu đồ tròn. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
