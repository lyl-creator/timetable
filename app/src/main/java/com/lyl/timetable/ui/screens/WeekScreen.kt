package com.lyl.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.ui.components.ChoiceChip
import com.lyl.timetable.ui.components.GlassIconButton
import com.lyl.timetable.ui.components.LargeTitleHeader
import com.lyl.timetable.ui.components.TimetableGrid
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
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
    onEmptyAreaClick: (Int, Int) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.colors
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
            .padding(horizontal = AppDimens.ScreenPadding)
    ) {
        Spacer(Modifier.height(6.dp))

        LargeTitleHeader(
            title = "课程表",
            subtitle = weekSubtitle(settings, week, weekCourses.map { it.name }.distinct().size)
        ) {
            GlassIconButton(
                icon = Icons.Rounded.Upload,
                contentDescription = "导入课表",
                onClick = onImport
            )
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "添加课程",
                onClick = { onEmptyAreaClick(1, 1) },
                filled = true
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            WeekSelector(
                totalWeeks = settings.totalWeeks,
                current = week,
                highlightWeek = if (todayInThisWeek || settings.currentWeekOverride > 0) autoWeek else -1,
                onSelect = onWeekChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { onWeekChange(autoWeek) },
                enabled = week != autoWeek,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Text(text = "今天")
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            TimetableGrid(
                courses = weekCourses,
                days = days,
                settings = settings,
                sectionCount = sectionCount,
                today = if (todayInThisWeek) today else null,
                weekDates = dates,
                onCourseClick = onCourseClick,
                onEmptyAreaClick = { day, section -> onEmptyAreaClick(day, section) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (weekCourses.isEmpty()) {
                "本周没有课程，点击右上角添加，或导入教务导出表格"
            } else {
                "共 ${weekCourses.size} 个上课时段 · 点击空白格可快速添加"
            },
            style = AppType.Caption1,
            color = p.textTertiary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Spacer(Modifier.height(12.dp))
    }
}

/** 横向滚动的周次选择胶囊 */
@Composable
private fun WeekSelector(
    totalWeeks: Int,
    current: Int,
    highlightWeek: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        runCatching {
            listState.animateScrollToItem((current - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0)))
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier.height(40.dp),
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
    val p = AppTheme.colors
    Box(contentAlignment = Alignment.BottomCenter) {
        ChoiceChip(
            text = week.toString(),
            selected = selected,
            onClick = onClick
        )
        if (isCurrent && !selected) {
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .background(p.accent, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}
