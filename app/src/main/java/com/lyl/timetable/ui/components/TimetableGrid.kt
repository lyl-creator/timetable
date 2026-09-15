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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.courseColor
import java.time.LocalDate

/** 左侧固定的节次列宽度（需要放得下「08:00–08:45」） */
private val TimeColumnWidth = 60.dp
private val RowHeight = 56.dp
private val HeaderHeight = 56.dp
private val MinDayColumnWidth = 58.dp
private val ChipRadius = 12.dp

/**
 * 周课表网格。
 *
 * 布局要点：**左侧节次与时间列固定**，只有右侧的表头与网格可以横向滚动；
 * 纵向滚动时两者一起滚动，因此节次与课程始终一一对应。
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
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val grouped = remember(courses) {
        courses.groupBy { Triple(it.dayOfWeek, it.startSection, it.endSection) }
    }

    BoxWithConstraints(modifier = modifier) {
        val available = maxWidth - TimeColumnWidth
        val dayColWidth = maxOf(
            MinDayColumnWidth,
            available / days.size.coerceAtLeast(1)
        )
        val gridWidth = dayColWidth * days.size
        val bodyHeight = RowHeight * sectionCount
        val todayIndex = if (today != null) weekDates.indexOf(today) else -1
        val p = AppTheme.colors

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
        ) {
            // ---------------- 左侧固定列 ----------------
            Column(
                modifier = Modifier
                    .width(TimeColumnWidth)
                    .background(p.card)
            ) {
                // 与表头对齐的占位
                Spacer(Modifier.height(HeaderHeight))
                for (section in 1..sectionCount) {
                    val slot = settings.timeSlotOf(section)
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
                                text = "${slot.startTime}–${slot.endTime}",
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                                color = p.textTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ---------------- 右侧：表头 + 网格（共用横向滚动） ----------------
            Column(
                modifier = Modifier
                    .width(available)
                    .horizontalScroll(hScroll)
            ) {
                Row(
                    modifier = Modifier
                        .height(HeaderHeight)
                        .width(gridWidth),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                Box(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(bodyHeight)
                ) {
                    GridBackground(
                        days = days.size,
                        sectionCount = sectionCount,
                        dayColWidth = dayColWidth,
                        todayIndex = todayIndex
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
                color = p.accent.copy(alpha = if (p.isLight) 0.06f else 0.12f),
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
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(AppDimens.InnerRadius))
                .then(
                    if (isToday) {
                        Modifier.background(scheme.primaryContainer)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = Course.DAY_SHORT[dayOfWeek - 1],
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isToday) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            )
            if (date != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) scheme.onPrimaryContainer else scheme.outline
                )
            }
        }
    }
}

/** 单个课程块：左侧色条 + tonal 底色 + 深色文字 */
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
                .width(4.dp)
                .fillMaxSize()
                .background(bar)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 5.dp)
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
