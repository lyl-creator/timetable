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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.ui.components.AccentGlassCard
import com.lyl.timetable.ui.components.EmptyStateView
import com.lyl.timetable.ui.components.GroupSection
import com.lyl.timetable.ui.components.LargeTitleHeader
import com.lyl.timetable.ui.components.RowSeparator
import com.lyl.timetable.ui.formatChineseDate
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
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
    val p = AppTheme.colors
    val today = remember { LocalDate.now() }
    val now = remember { LocalTime.now() }
    val dayOfWeek = today.dayOfWeek.value
    val hasTermStart = remember(settings.termStartDate) { settings.termStartDate.isNotBlank() }

    val decorated = remember(courses, settings, week) {
        courses
            .filter { it.isActiveInWeek(week) && it.dayOfWeek == dayOfWeek }
            .sortedBy { it.startSection }
            .map { course ->
                val startText = settings.timeSlotOf(course.startSection)?.startTime ?: ""
                val endText = settings.timeSlotOf(course.endSection)?.endTime ?: ""
                val start = parseTime(startText)
                val end = parseTime(endText)
                val state = when {
                    start == null || end == null -> CourseState.UPCOMING
                    !now.isBefore(start) && now.isBefore(end) -> CourseState.ONGOING
                    !now.isBefore(end) -> CourseState.FINISHED
                    else -> CourseState.UPCOMING
                }
                TodayEntry(course, startText, endText, state)
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenPadding),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.GroupGap)
    ) {
        item {
            LargeTitleHeader(
                title = "今日",
                subtitle = buildString {
                    append(formatChineseDate(today))
                    append(" · ")
                    append(Course.DAY_NAMES[dayOfWeek - 1])
                    if (hasTermStart) append(" · 第 $week 周")
                }
            )
        }

        if (decorated.isEmpty()) {
            item {
                GroupSection {
                    EmptyStateView(
                        icon = Icons.Rounded.CalendarMonth,
                        title = if (hasTermStart) "今天没有课程" else "尚未设置学期",
                        description = if (hasTermStart) {
                            "可以好好休息，或安排自学内容。"
                        } else {
                            "在设置中填写开学日期后，即可自动定位当前教学周。"
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
                    AccentGlassCard {
                        Text(
                            text = if (highlight.state == CourseState.ONGOING) "正在上课" else "即将开始",
                            style = AppType.Footnote,
                            color = p.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = highlight.course.name,
                            style = AppType.Title2,
                            color = p.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = listOfNotNull(
                                highlight.startTime.takeIf { it.isNotBlank() }?.let {
                                    "$it – ${highlight.endTime}"
                                },
                                highlight.course.location.takeIf { it.isNotBlank() },
                                highlight.course.teacher.takeIf { it.isNotBlank() }
                            ).joinToString("  ·  "),
                            style = AppType.Subheadline,
                            color = p.textSecondary
                        )
                    }
                }
            }

            item {
                GroupSection(
                    title = "今日课程",
                    footer = "共 ${decorated.size} 个时段"
                ) {
                    decorated.forEachIndexed { index, entry ->
                        TodayRow(
                            entry = entry,
                            onClick = { onCourseClick(entry.course) }
                        )
                        if (index != decorated.lastIndex) RowSeparator(inset = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRow(entry: TodayEntry, onClick: () -> Unit) {
    val p = AppTheme.colors
    val palette = courseColor(entry.course.colorIndex)
    val finished = entry.state == CourseState.FINISHED
    val alpha = if (finished) 0.45f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(50.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = entry.startTime.ifBlank { "--:--" },
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.state == CourseState.ONGOING) p.accent else p.textPrimary.copy(alpha = alpha)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.endTime,
                style = AppType.Caption1,
                color = p.textTertiary
            )
        }

        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(palette.accent(p.isLight).copy(alpha = alpha))
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.course.name,
                style = AppType.Headline,
                color = p.textPrimary.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    entry.course.location.takeIf { it.isNotBlank() },
                    entry.course.teacher.takeIf { it.isNotBlank() }
                ).joinToString("  ·  ").ifBlank { "地点待定" },
                style = AppType.Footnote,
                color = p.textSecondary.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (entry.state == CourseState.ONGOING) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(AppDimens.Capsule))
                    .background(p.accentSoft(if (p.isLight) 0.14f else 0.24f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "进行中",
                    style = AppType.Caption2,
                    color = p.accent,
                    fontWeight = FontWeight.SemiBold
                )
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
