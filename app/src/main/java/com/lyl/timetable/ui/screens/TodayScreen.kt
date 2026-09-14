package com.lyl.timetable.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.ui.components.AccentSoftCard
import com.lyl.timetable.ui.components.EmptyState
import com.lyl.timetable.ui.components.LargeTitle
import com.lyl.timetable.ui.components.OriginCard
import com.lyl.timetable.ui.formatChineseDate
import com.lyl.timetable.ui.theme.OriginTheme
import com.lyl.timetable.ui.theme.courseColor
import java.time.LocalDate
import java.time.LocalTime

private data class TodayEntry(
    val course: Course,
    val startTime: String,
    val endTime: String,
    val state: CourseState
)

private enum class CourseState { FINISHED, ONGOING, UPCOMING }

@Composable
fun TodayScreen(
    courses: List<Course>,
    settings: AppSettings,
    week: Int,
    onCourseClick: (Course) -> Unit,
    onAddCourse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OriginTheme.colors
    val today = remember { LocalDate.now() }
    val now = remember { LocalTime.now() }
    val dayOfWeek = today.dayOfWeek.value

    val entries = remember(courses, settings, week) {
        courses
            .filter { it.isActiveInWeek(week) && it.dayOfWeek == dayOfWeek }
            .sortedBy { it.startSection }
            .map { course ->
                val startText = settings.timeSlotOf(course.startSection)?.startTime ?: ""
                val endText = settings.timeSlotOf(course.endSection)?.endTime ?: ""
                TodayEntry(course, startText, endText, CourseState.UPCOMING)
            }
    }

    val decorated = entries.map { e ->
        val start = parseTime(e.startTime)
        val end = parseTime(e.endTime)
        val state = when {
            start == null || end == null -> CourseState.UPCOMING
            !now.isBefore(start) && now.isBefore(end) -> CourseState.ONGOING
            !now.isBefore(end) -> CourseState.FINISHED
            else -> CourseState.UPCOMING
        }
        e.copy(state = state)
    }

    val hasTermStart = remember(settings.termStartDate) { settings.termStartDate.isNotBlank() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LargeTitle(
                title = "今日",
                subtitle = buildString {
                    append(formatChineseDate(today))
                    append("  ")
                    append(Course.DAY_NAMES[dayOfWeek - 1])
                    if (hasTermStart) append("  ·  第 $week 周")
                }
            )
            Spacer(Modifier.height(4.dp))
        }

        if (decorated.isEmpty()) {
            item {
                OriginCard(contentPadding = PaddingValues(0.dp)) {
                    EmptyState(
                        icon = Icons.Rounded.CalendarMonth,
                        title = if (hasTermStart) "今天没有课程" else "尚未设置学期",
                        description = if (hasTermStart) {
                            "好好休息，或前往课后安排自学内容。"
                        } else {
                            "设置开学日期后即可自动定位当前教学周。"
                        }
                    )
                }
            }
        } else {
            val ongoing = decorated.firstOrNull { it.state == CourseState.ONGOING }
            val next = decorated.firstOrNull { it.state == CourseState.UPCOMING }
            val highlight = ongoing ?: next

            if (highlight != null) {
                item {
                    AccentSoftCard(contentPadding = PaddingValues(18.dp)) {
                        Text(
                            text = if (highlight.state == CourseState.ONGOING) "正在上课" else "即将开始",
                            style = MaterialTheme.typography.labelMedium,
                            color = OriginTheme.accentStart
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = highlight.course.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = listOfNotNull(
                                highlight.startTime.takeIf { it.isNotBlank() }?.let {
                                    "$it - ${highlight.endTime}"
                                },
                                highlight.course.location.takeIf { it.isNotBlank() },
                                highlight.course.teacher.takeIf { it.isNotBlank() }
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                    }
                }
            }

            item {
                Text(
                    text = "共 ${decorated.size} 节",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            items(decorated, key = { it.course.id.toString() + it.course.startSection }) { entry ->
                TodayCourseRow(
                    entry = entry,
                    settings = settings,
                    onClick = { onCourseClick(entry.course) }
                )
            }
        }
    }
}

@Composable
private fun TodayCourseRow(
    entry: TodayEntry,
    settings: AppSettings,
    onClick: () -> Unit
) {
    val c = OriginTheme.colors
    val palette = courseColor(entry.course.colorIndex)
    val finished = entry.state == CourseState.FINISHED
    val alpha = if (finished) 0.45f else 1f

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.width(46.dp).padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = entry.startTime.ifBlank { "--:--" },
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.state == CourseState.ONGOING) OriginTheme.accentStart else c.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.endTime.ifBlank { "" },
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }

        Spacer(Modifier.width(10.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.foreground(c.isLight).copy(alpha = alpha * 0.9f))
            )
            Spacer(Modifier.width(10.dp))
            OriginCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                onClick = onClick
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.course.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = listOfNotNull(
                                entry.course.location.takeIf { it.isNotBlank() },
                                entry.course.teacher.takeIf { it.isNotBlank() }
                            ).joinToString("  ·  ").ifBlank { "地点待定" },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.state == CourseState.ONGOING) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = c.accent.copy(alpha = if (c.isLight) 0.12f else 0.22f)
                        ) {
                            Text(
                                text = "进行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = OriginTheme.accentStart,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (entry.state == CourseState.FINISHED) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(c.textTertiary.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}

private fun parseTime(text: String): LocalTime? {
    if (text.isBlank()) return null
    val parts = text.split(':')
    if (parts.size < 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return LocalTime.of(h, m)
}
