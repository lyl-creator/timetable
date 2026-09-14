package com.lyl.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.ui.components.LargeTitle
import com.lyl.timetable.ui.components.TimetableGrid
import com.lyl.timetable.ui.theme.OriginTheme
import com.lyl.timetable.ui.weekDatesOf
import com.lyl.timetable.ui.weekSubtitle
import java.time.LocalDate

@Composable
fun WeekScreen(
    courses: List<Course>,
    settings: AppSettings,
    week: Int,
    autoWeek: Int,
    onWeekChange: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    onEmptyAreaClick: (dayOfWeek: Int, section: Int) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OriginTheme.colors
    val days = remember(settings.showWeekend) {
        if (settings.showWeekend) (1..7).toList() else (1..5).toList()
    }
    val weekCourses = remember(courses, week, days) {
        courses.filter { it.isActiveInWeek(week) && it.dayOfWeek in days }
    }
    val maxSection = remember(courses) { courses.maxOfOrNull { it.endSection } ?: 0 }
    val sectionCount = maxOf(settings.sectionCount, maxSection).coerceAtLeast(8)
    val dates = remember(settings.termStartDate, week) { weekDatesOf(settings, week) }
    val today = remember { LocalDate.now() }
    val todayInThisWeek = dates.any { it == today }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        LargeTitle(
            title = "课程表",
            subtitle = weekSubtitle(settings, week, weekCourses.map { it.name }.distinct().size)
        ) {
            GradientIconButton(
                icon = Icons.Rounded.Upload,
                contentDescription = "导入课表",
                onClick = onImport,
                background = c.card,
                tint = c.textPrimary
            )
            Spacer(Modifier.width(10.dp))
            GradientIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "添加课程",
                onClick = { onEmptyAreaClick(1, 1) }
            )
        }

        Spacer(Modifier.height(16.dp))

        WeekSelector(
            totalWeeks = settings.totalWeeks,
            current = week,
            highlightWeek = if (todayInThisWeek || settings.currentWeekOverride > 0) autoWeek else -1,
            onSelect = onWeekChange
        )

        Spacer(Modifier.height(10.dp))

        TimetableGrid(
            courses = weekCourses,
            days = days,
            settings = settings,
            sectionCount = sectionCount,
            today = if (todayInThisWeek) today else null,
            weekDates = dates,
            onCourseClick = onCourseClick,
            onEmptyAreaClick = onEmptyAreaClick,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.height(96.dp))
    }
}

/** 横向滚动的周次选择胶囊 */
@Composable
private fun WeekSelector(
    totalWeeks: Int,
    current: Int,
    highlightWeek: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        runCatching {
            listState.animateScrollToItem((current - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0)))
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items((1..totalWeeks).toList()) { w ->
            WeekChip(
                week = w,
                selected = w == current,
                isCurrent = w == highlightWeek,
                onClick = { onSelect(w) }
            )
        }
    }
}

@Composable
private fun WeekChip(week: Int, selected: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val c = OriginTheme.colors
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected -> Brush.linearGradient(
                        listOf(OriginTheme.accentStart, OriginTheme.accentEnd)
                    )
                    else -> Brush.linearGradient(listOf(c.card, c.card))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$week",
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) c.onAccent else c.textPrimary
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) c.onAccent else OriginTheme.accentStart)
                )
            }
        }
    }
}

@Composable
private fun GradientIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    background: androidx.compose.ui.graphics.Color? = null,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    val c = OriginTheme.colors
    val fg = tint ?: c.onAccent
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                background?.let { Brush.linearGradient(listOf(it, it)) }
                    ?: Brush.linearGradient(listOf(OriginTheme.accentStart, OriginTheme.accentEnd))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(20.dp))
    }
}
