package com.example.notes.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.notes.data.BlockKind
import com.example.notes.data.TimeBlock
import com.example.notes.domain.TimeUtils
import com.example.notes.ui.theme.DangerRed
import com.example.notes.ui.theme.categoryColor
import com.example.notes.ui.util.Fmt
import java.time.LocalDate
import kotlin.math.roundToInt

// ===============================================================================================
// LƯỚI THỜI GIAN (chế độ Ngày và Tuần của màn Lịch)
//
// Hình dung: một bảng có 24 hàng (24 giờ) và N cột (N ngày).
//   • Mỗi khối lịch là một hình chữ nhật đặt vào đúng cột (ngày) và đúng độ cao (giờ).
//   • "Vị trí dọc"  = số phút kể từ nửa đêm × (độ cao một giờ ÷ 60).
//   • "Độ cao khối" = thời lượng (phút) × (độ cao một giờ ÷ 60).
//   • Nhấn giữ một khối rồi kéo = dời lịch (kéo thả).
//
// Cấu trúc các hàm (từ ngoài vào trong):
//   TimeGrid ─┬─ DayHeader          (T2, 24 ...)
//             ├─ AllDayRow          (sự kiện cả ngày)
//             └─ vùng cuộn ─┬─ HourLabels   (cột giờ bên trái)
//                           └─ DayColumn ─── EventBlock (từng khối, có kéo thả)
// ===============================================================================================

private val HourHeight = 56.dp // độ cao của một giờ trên lưới
private val GutterWidth = 42.dp // bề rộng cột nhãn giờ bên trái
private const val SnapMinutes = 15 // kéo thả được "hít" vào bội số 15 phút
private const val FirstVisibleHour = 7 // mở lịch thì tự cuộn tới 7 giờ sáng

@Composable
fun TimeGrid(
    firstDay: Long,
    dayCount: Int,
    selectedDay: Long,
    blocks: List<TimeBlock>,
    conflictKeys: Set<String>,
    onSelectDay: (Long) -> Unit,
    onBlockClick: (TimeBlock) -> Unit,
    onMove: (block: TimeBlock, deltaDays: Int, deltaMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { TimeUtils.today() }
    val days = remember(firstDay, dayCount) { (0 until dayCount).map { firstDay + it } }

    // Trạng thái cuộn dọc; ban đầu cuộn sẵn tới 7 giờ sáng cho tiện.
    val density = LocalDensity.current
    val scrollState = rememberScrollState(initial = with(density) { (HourHeight * FirstVisibleHour).roundToPx() })

    // Tách khối "cả ngày" (hiện ở hàng riêng phía trên) khỏi khối có giờ cụ thể.
    val allDayBlocks = blocks.filter { it.allDay }
    val timedBlocks = blocks.filter { !it.allDay }

    Column(modifier = modifier.fillMaxSize()) {
        // Hàng tiêu đề các ngày (chừa cột giờ bên trái bằng padding)
        Row(modifier = Modifier.fillMaxWidth().padding(start = GutterWidth)) {
            for (day in days) {
                DayHeader(
                    day = day,
                    isToday = day == today,
                    isSelected = day == selectedDay,
                    onClick = { onSelectDay(day) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (allDayBlocks.isNotEmpty()) {
            AllDayRow(days = days, allDayBlocks = allDayBlocks, onBlockClick = onBlockClick)
        }

        // Vùng cuộn dọc chứa cột giờ + các cột ngày
        val lineColor = MaterialTheme.colorScheme.outlineVariant
        val hourHeightPx = with(density) { HourHeight.toPx() }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 6.dp)
        ) {
            HourLabels()

            // Các cột ngày. drawBehind vẽ 25 đường kẻ ngang (mỗi giờ một đường) làm nền.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(HourHeight * 24)
                    .drawBehind {
                        for (hour in 0..24) {
                            val y = hour * hourHeightPx
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                    }
            ) {
                for (day in days) {
                    DayColumn(
                        day = day,
                        isToday = day == today,
                        blocks = timedBlocks,
                        conflictKeys = conflictKeys,
                        canMoveAcrossDays = dayCount > 1, // chế độ Ngày chỉ có 1 cột nên không kéo ngang
                        onBlockClick = onBlockClick,
                        onMove = onMove,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Phần tiêu đề và cột giờ
// ---------------------------------------------------------------------------------------------

/** Một ô tiêu đề: tên thứ ("T2") và số ngày trong vòng tròn (tô đặc nếu là hôm nay). */
@Composable
private fun DayHeader(day: Long, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val dayOfMonth = LocalDate.ofEpochDay(day).dayOfMonth

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            Fmt.weekdayShort(day),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    // Ngày đang chọn (không phải hôm nay) có viền tròn.
                    if (isSelected && !isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Hàng các sự kiện "cả ngày": mỗi cột hiện tối đa 2 nhãn màu. */
@Composable
private fun AllDayRow(days: List<Long>, allDayBlocks: List<TimeBlock>, onBlockClick: (TimeBlock) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = GutterWidth, top = 2.dp)) {
        for (day in days) {
            val dayStart = TimeUtils.dayStart(day)
            val dayEnd = TimeUtils.dayEnd(day)
            val blocksOfDay = allDayBlocks.filter { it.start < dayEnd && it.end > dayStart }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (block in blocksOfDay.take(2)) {
                    Text(
                        text = block.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(block.categoryColor())
                            .clickable { onBlockClick(block) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Cột nhãn giờ bên trái: 01:00, 02:00, ... 23:00. */
@Composable
private fun HourLabels() {
    Column(modifier = Modifier.width(GutterWidth)) {
        for (hour in 0 until 24) {
            Box(modifier = Modifier.height(HourHeight), contentAlignment = Alignment.TopEnd) {
                Text(
                    text = if (hour == 0) "" else "%02d:00".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Đẩy chữ lên nửa dòng để căn giữa vào đường kẻ giờ.
                    modifier = Modifier.padding(end = 6.dp).offset(y = (-6).dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Một cột ngày
// ---------------------------------------------------------------------------------------------

/**
 * Cột của một ngày: đặt các khối lịch của ngày đó vào đúng vị trí.
 * Hai khối chồng lên nhau về thời gian sẽ được xếp cạnh nhau (xem [assignLanes]).
 */
@Composable
private fun DayColumn(
    day: Long,
    isToday: Boolean,
    blocks: List<TimeBlock>,
    conflictKeys: Set<String>,
    canMoveAcrossDays: Boolean,
    onBlockClick: (TimeBlock) -> Unit,
    onMove: (TimeBlock, Int, Int) -> Unit,
    modifier: Modifier
) {
    val dayStart = TimeUtils.dayStart(day)
    val dayEnd = TimeUtils.dayEnd(day)

    // Chỉ lấy các khối có giao với ngày này.
    val dayBlocks = remember(blocks, day) { blocks.filter { it.start < dayEnd && it.end > dayStart } }
    val lanes = remember(dayBlocks) { assignLanes(dayBlocks) }

    val separatorColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current

    // BoxWithConstraints cho biết bề rộng cột (maxWidth) để chia cho các làn.
    BoxWithConstraints(
        modifier = modifier
            .drawBehind { drawLine(separatorColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f) } // đường dọc ngăn cột
            .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)) else Modifier)
    ) {
        val columnWidth = maxWidth
        val columnWidthPx = with(density) { columnWidth.toPx() }

        for (block in dayBlocks) {
            val (lane, laneCount) = lanes[block.key] ?: (0 to 1)

            // Nếu khối kéo dài qua nửa đêm thì chỉ vẽ phần nằm trong ngày này.
            val shownStart = maxOf(block.start, dayStart)
            val shownEnd = minOf(block.end, dayEnd)
            val startMinute = (shownStart - dayStart) / 60_000f
            val lengthMinutes = (shownEnd - shownStart) / 60_000f

            val laneWidth = columnWidth / laneCount

            EventBlock(
                block = block,
                hasConflict = block.key in conflictKeys,
                columnWidthPx = columnWidthPx,
                canMoveAcrossDays = canMoveAcrossDays,
                onClick = { onBlockClick(block) },
                onMove = onMove,
                modifier = Modifier
                    .width(laneWidth - 2.dp)
                    // offset: dịch khối tới đúng làn (ngang) và đúng giờ (dọc)
                    .offset(x = laneWidth * lane + 1.dp, y = HourHeight * (startMinute / 60f))
                    // Khối quá ngắn vẫn cao tối thiểu 24dp để còn đọc/bấm được.
                    .height((HourHeight * (lengthMinutes / 60f)).coerceAtLeast(24.dp))
            )
        }

        if (isToday) NowLine()
    }
}

/**
 * Chia "làn" để các khối trùng giờ nằm cạnh nhau thay vì đè lên nhau.
 *
 * Kết quả: với mỗi khối (theo `key`) trả về (số thứ tự làn, tổng số làn của nhóm).
 * Ví dụ hai khối trùng nhau: khối A -> (0, 2), khối B -> (1, 2): mỗi khối rộng một nửa cột.
 *
 * Thuật toán: duyệt các khối theo giờ bắt đầu. Các khối liên tiếp chồng thời gian với nhau
 * tạo thành một "nhóm"; trong nhóm, mỗi khối vào làn đầu tiên đã trống.
 */
private fun assignLanes(blocks: List<TimeBlock>): Map<String, Pair<Int, Int>> {
    val result = mutableMapOf<String, Pair<Int, Int>>()

    var groupMembers = mutableListOf<TimeBlock>() // các khối trong nhóm hiện tại
    val laneEndTimes = mutableListOf<Long>() // laneEndTimes[i] = giờ kết thúc khối cuối cùng ở làn i
    val laneOfBlock = mutableMapOf<String, Int>()
    var groupEnd = Long.MIN_VALUE // giờ kết thúc muộn nhất của nhóm

    /** Chốt nhóm hiện tại: ghi kết quả cho từng khối rồi bắt đầu nhóm mới. */
    fun finishGroup() {
        for (member in groupMembers) {
            result[member.key] = laneOfBlock.getValue(member.key) to laneEndTimes.size
        }
        groupMembers = mutableListOf()
        laneEndTimes.clear()
        laneOfBlock.clear()
    }

    for (block in blocks.sortedWith(compareBy({ it.start }, { it.end }))) {
        // Khối này bắt đầu sau khi cả nhóm đã kết thúc -> chốt nhóm cũ, bắt đầu nhóm mới.
        if (groupMembers.isNotEmpty() && block.start >= groupEnd) finishGroup()

        groupEnd = if (groupMembers.isEmpty()) block.end else maxOf(groupEnd, block.end)

        // Tìm làn đầu tiên đã trống (khối cuối của làn đó đã kết thúc trước khi khối này bắt đầu).
        var lane = laneEndTimes.indexOfFirst { it <= block.start }
        if (lane < 0) {
            laneEndTimes += block.end // không có làn trống -> mở làn mới
            lane = laneEndTimes.lastIndex
        } else {
            laneEndTimes[lane] = block.end
        }

        laneOfBlock[block.key] = lane
        groupMembers += block
    }
    if (groupMembers.isNotEmpty()) finishGroup()

    return result
}

/** Đường đỏ ngang đánh dấu thời điểm hiện tại (chỉ hiện ở cột hôm nay). */
@Composable
private fun NowLine() {
    val minutesNow = remember { TimeUtils.minutesOfDay(System.currentTimeMillis()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = HourHeight * (minutesNow / 60f))
            .height(2.dp)
            .background(DangerRed)
    )
}

// ---------------------------------------------------------------------------------------------
// Một khối lịch, có thể kéo thả
// ---------------------------------------------------------------------------------------------

/**
 * Một khối lịch. Bấm = mở. Nhấn giữ rồi kéo = dời.
 *
 * Quy trình kéo thả:
 *  1. Nhấn giữ -> `isDragging = true`, khối nổi lên (có bóng).
 *  2. Ngón tay di chuyển -> `dragOffset` cộng dồn quãng đường, khối đi theo ngón tay.
 *  3. Thả tay -> đổi quãng đường (pixel) thành "số phút" (làm tròn bội 15) và "số ngày",
 *     gọi [onMove]. ViewModel sẽ ghi vào database.
 *  4. Khi database cập nhật, khối được vẽ lại ở vị trí mới và `dragOffset` được đặt về 0.
 */
@Composable
private fun EventBlock(
    block: TimeBlock,
    hasConflict: Boolean,
    columnWidthPx: Float,
    canMoveAcrossDays: Boolean,
    onClick: () -> Unit,
    onMove: (TimeBlock, Int, Int) -> Unit,
    modifier: Modifier
) {
    val color = block.categoryColor()
    val hourHeightPx = with(LocalDensity.current) { HourHeight.toPx() }

    // Trạng thái kéo thả (cục bộ cho từng khối).
    var dragOffset by remember { mutableStateOf(Offset.Zero) } // quãng đường đã kéo (pixel)
    var isDragging by remember { mutableStateOf(false) }
    var isWaitingForSave by remember { mutableStateOf(false) } // đã thả tay, chờ database cập nhật

    // Luôn dùng phiên bản mới nhất của `block` và `onMove` bên trong cử chỉ kéo
    // (cử chỉ được tạo một lần nên nếu không dùng cách này sẽ giữ bản cũ).
    val latestBlock by rememberUpdatedState(block)
    val latestOnMove by rememberUpdatedState(onMove)

    // Khi giờ của khối đổi (database đã cập nhật) -> bỏ phần dịch tạm thời.
    LaunchedEffect(block.start, block.end) {
        dragOffset = Offset.Zero
        isWaitingForSave = false
    }
    // Phòng trường hợp database không cập nhật (lỗi): sau 0,9 giây trả khối về chỗ cũ.
    LaunchedEffect(isWaitingForSave) {
        if (isWaitingForSave) {
            kotlinx.coroutines.delay(900)
            dragOffset = Offset.Zero
            isWaitingForSave = false
        }
    }

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) } // đi theo ngón tay
            .zIndex(if (isDragging) 10f else 0f) // khối đang kéo nằm trên cùng
            .then(if (isDragging) Modifier.shadow(10.dp, shape) else Modifier)
            .clip(shape)
            .background(color.copy(alpha = if (isDragging) 0.95f else if (block.isDone) 0.12f else 0.22f))
            .then(if (hasConflict) Modifier.border(1.5.dp, DangerRed, shape) else Modifier)
            .clickable(onClick = onClick)
            .pointerInput(block.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { isDragging = true },
                    onDrag = { change, amount ->
                        change.consume()
                        val newX = if (canMoveAcrossDays) dragOffset.x + amount.x else 0f
                        dragOffset = Offset(newX, dragOffset.y + amount.y)
                    },
                    onDragEnd = {
                        isDragging = false
                        // pixel -> phút: (pixel ÷ pixel mỗi giờ) × 60, rồi làm tròn về bội của 15 phút.
                        val rawMinutes = dragOffset.y / hourHeightPx * 60f
                        val deltaMinutes = (rawMinutes / SnapMinutes).roundToInt() * SnapMinutes
                        // pixel -> ngày: chia cho bề rộng một cột.
                        val deltaDays = if (canMoveAcrossDays) (dragOffset.x / columnWidthPx).roundToInt() else 0

                        if (deltaMinutes == 0 && deltaDays == 0) {
                            dragOffset = Offset.Zero // kéo quá ít, coi như không dời
                        } else {
                            isWaitingForSave = true
                            latestOnMove(latestBlock, deltaDays, deltaMinutes)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    }
                )
            }
    ) {
        // Vạch màu đậm bên trái khối
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color)
        )
        BlockText(block = block, hasConflict = hasConflict, isDragging = isDragging, color = color)
    }
}

/** Chữ bên trong khối: tiêu đề (kèm biểu tượng) và khoảng giờ. */
@Composable
private fun BlockText(block: TimeBlock, hasConflict: Boolean, isDragging: Boolean, color: Color) {
    // Khi đang kéo nền khối đậm nên đổi chữ sang màu trắng cho dễ đọc.
    val titleColor = if (isDragging) Color.White else MaterialTheme.colorScheme.onSurface
    val timeColor = if (isDragging) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (block.kind == BlockKind.TASK) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            }
            Text(
                text = block.title,
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (block.isDone) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (hasConflict) {
                Icon(Icons.Rounded.Warning, contentDescription = "Trùng giờ", tint = DangerRed, modifier = Modifier.size(12.dp))
            }
        }
        Text(
            text = "${Fmt.time(block.start)}–${Fmt.time(block.end)}",
            style = MaterialTheme.typography.labelSmall,
            color = timeColor,
            maxLines = 1
        )
    }
}
