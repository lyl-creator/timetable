package com.lyl.timetable.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.TimeSlot
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.courseColor
import java.time.LocalDate

private val TimeColumnWidth = 42.dp
private val RowHeight = 56.dp
private val HeaderHeight = 56.dp
private val MinDayColumnWidth = 60.dp
private val ChipRadius = 10.dp

/**
 * 周课表网格（iOS 日历观感）。
 *
 * 表头显示星期与日期，今日以强调色标识；课程块左侧带色条、淡色底、深色文字。
 * 横向整体滚动、纵向内容滚动，课程块按「星期 × 节次」绝对定位并支持跨节。
 */
@Composable
fun TimetableGrid(
    courses: List<Course>,
    days: List<Int>,
    settings: AppSettings,
    sectionCount: Int,
    today: LocalDate?,
    weekDates: List<LocalDate?>,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    onEmptyAreaClick: ((dayOfWeek: Int, section: Int) -> Unit)? = null
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val grouped = remember(courses) {
        courses.groupBy { Triple(it.dayOfWeek, it.startSection, it.endSection) }
    }

    BoxWithConstraints(modifier = modifier) {
        val dayColWidth = maxOf(
            MinDayColumnWidth,
            ((maxWidth - TimeColumnWidth) / days.size.coerceAtLeast(1))
        )
        val totalWidth = TimeColumnWidth + dayColWidth * days.size
        val bodyHeight = RowHeight * sectionCount

        Column(
            modifier = Modifier
                .horizontalScroll(hScroll)
                .width(totalWidth)
        ) {
            // ---------------- 表头 ----------------
            Row(
                modifier = Modifier.height(HeaderHeight).width(totalWidth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(TimeColumnWidth))
                days.forEachIndexed { index, day ->
                    val date = weekDates.getOrNull(index)
                    val isToday = today != null && date != null && date == today
                    DayHeaderCell(
                        dayOfWeek = day,
                        date = date,
                        isToday = isToday,
                        width = dayColWidth
                    )
                }
            }

            // ---------------- 主体 ----------------
            Box(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                ) {
                    TimeColumn(settings = settings, sectionCount = sectionCount)

                    Box(
                        modifier = Modifier
                            .width(dayColWidth * days.size)
                            .height(bodyHeight)
                    ) {
                        GridBackground(
                            days = days.size,
                            sectionCount = sectionCount,
                            dayColWidth = dayColWidth,
                            todayIndex = if (today != null) weekDates.indexOf(today) else -1
                        )

                        if (onEmptyAreaClick != null) {
                            val dayColPx = with(LocalDensity.current) { dayColWidth.toPx() }
                            val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(days.size, sectionCount, dayColPx, rowPx) {
                                        detectTapGestures { position ->
                                            val dayIdx = (position.x / dayColPx).toInt()
                                                .coerceIn(0, days.size - 1)
                                            val secIdx = (position.y / rowPx).toInt()
                                                .coerceIn(0, sectionCount - 1)
                                            onEmptyAreaClick(days[dayIdx], secIdx + 1)
                                        }
                                    }
                            )
                        }

                        grouped.forEach { (key, group) ->
                            val dayIndex = days.indexOf(key.first)
                            if (dayIndex < 0) return@forEach
                            val startIndex = key.second - 1
                            if (startIndex < 0 || startIndex >= sectionCount) return@forEach
                            val span = (key.third - key.second + 1)
                                .coerceAtMost(sectionCount - startIndex)
                            if (span <= 0) return@forEach

                            Box(
                                modifier = Modifier
                                    .offset(x = dayColWidth * dayIndex, y = RowHeight * startIndex)
                                    .width(dayColWidth)
                                    .height(RowHeight * span)
                                    .padding(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    group.forEach { course ->
                                        Box(Modifier.weight(1f)) {
                                            CourseChip(
                                                course = course,
                                                compact = group.size > 1,
                                                onClick = { onCourseClick(course) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridBackground(
    days: Int,
    sectionCount: Int,
    dayColWidth: Dp,
    todayIndex: Int
) {
    val p = AppTheme.colors
    Canvas(Modifier.fillMaxSize()) {
        val colW = dayColWidth.toPx()
        val rowH = RowHeight.toPx()
        if (todayIndex >= 0) {
            drawRect(
                color = p.accent.copy(alpha = if (p.isLight) 0.05f else 0.10f),
                topLeft = Offset(colW * todayIndex, 0f),
                size = Size(colW, size.height)
            )
        }
        for (i in 0..sectionCount) {
            val y = rowH * i
            drawLine(p.gridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        for (j in 0..days) {
            val x = colW * j
            drawLine(p.gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
    }
}

@Composable
private fun DayHeaderCell(
    dayOfWeek: Int,
    date: LocalDate?,
    isToday: Boolean,
    width: Dp
) {
    val p = AppTheme.colors
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(46.dp)
                .width(width - 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isToday) p.accentSoft(if (p.isLight) 0.12f else 0.20f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "周${Course.DAY_SHORT[dayOfWeek - 1]}",
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isToday) p.accent else p.textSecondary
                )
                if (date != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) p.accent else p.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(settings: AppSettings, sectionCount: Int) {
    val p = AppTheme.colors
    Column(Modifier.width(TimeColumnWidth)) {
        for (section in 1..sectionCount) {
            val slot: TimeSlot? = settings.timeSlotOf(section)
            Column(
                modifier = Modifier
                    .height(RowHeight)
                    .width(TimeColumnWidth)
                    .padding(end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = section.toString(),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.textSecondary
                )
                if (slot != null) {
                    Text(
                        text = slot.startTime,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = p.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 单个课程块：左侧色条 + 淡色底 + 深色文字（iOS 日历观感） */
@Composable
private fun CourseChip(
    course: Course,
    compact: Boolean,
    onClick: () -> Unit
) {
    val p = AppTheme.colors
    val palette = courseColor(course.colorIndex)
    val bg = palette.background(p.isLight)
    val fg = palette.foreground(p.isLight)
    val bar = palette.accent(p.isLight)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ChipRadius))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxSize()
                .background(bar)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 4.dp)
        ) {
            Text(
                text = course.name,
                fontSize = if (compact) 10.sp else 11.sp,
                lineHeight = if (compact) 11.5.sp else 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = if (compact) 2 else if (course.sectionCount >= 3) 4 else 3,
                overflow = TextOverflow.Ellipsis
            )
            if (course.location.isNotBlank() && (!compact || course.sectionCount >= 2)) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = course.location,
                    fontSize = 9.sp,
                    lineHeight = 10.5.sp,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!compact && course.sectionCount >= 2 && course.teacher.isNotBlank()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = course.teacher,
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    color = fg.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
