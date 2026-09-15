package com.lyl.timetable.reminder

import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.TermDates
import com.lyl.timetable.data.TimeText
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 一次「该去上课了」的提醒。
 *
 * 纯数据、纯计算，不引用任何 Android API，因此调度逻辑可以被单元测试完整覆盖，
 * 也便于在闹钟触发时通过 Intent 附加信息直接投递通知（无需再读数据库）。
 */
data class ReminderEvent(
    val courseName: String,
    val teacher: String,
    val location: String,
    val colorIndex: Int,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    /** 合并后的连续区间开始时间，形如 08:00 */
    val startText: String,
    /** 合并后的连续区间结束时间，形如 09:35 */
    val endText: String,
    val triggerAt: LocalDateTime,
    /** 触发时刻距离开始时刻的分钟数 */
    val leadMinutes: Int
) {
    /** 形如「1-4 节」 */
    val sectionText: String
        get() = if (startSection == endSection) "$startSection 节" else "$startSection-$endSection 节"

    /** 通知的标题行 */
    val title: String
        get() = courseName.ifBlank { "上课提醒" }

    /** 通知的正文行，例如「10 分钟后 · 08:00-09:35 · N楼-411」 */
    val detail: String
        get() {
            val parts = ArrayList<String>(3)
            parts.add(if (leadMinutes > 0) "$leadMinutes 分钟后" else "现在")
            parts.add("$startText-$endText")
            if (location.isNotBlank()) parts.add(location)
            return parts.joinToString("  ·  ")
        }

    /** 合并后的课时长（分钟），用于展示 */
    val durationMinutes: Int?
        get() = TimeText.minutesBetween(startText, endText)
}

/**
 * 把日期换算成教学周的解析器。
 *
 * 与课表页保持一致：当用户手动指定了「当前周」时，说明开学日期自动推算的结果与实际情况有偏差，
 * 此时提醒也整体平移，保证提醒发生在用户看到的课表所属的那一周。
 */
class WeekResolver(settings: AppSettings, today: LocalDate) {

    private val totalWeeks = settings.totalWeeks
    private val termStartMonday = TermDates.parse(settings.termStartDate)

    /** 手动指定当前周时相对自动推算的偏移量 */
    private val shift: Int = run {
        val start = termStartMonday ?: return@run 0
        if (settings.currentWeekOverride <= 0) return@run 0
        val auto = TermDates.weekIndex(start, today)
        if (auto <= 0) 0 else settings.currentWeekOverride - auto
    }

    /** 是否具备换算条件（必须先设置开学日期） */
    val usable: Boolean get() = termStartMonday != null

    /** [date] 对应的教学周；0 表示不在学期范围内 */
    fun weekOf(date: LocalDate): Int {
        val start = termStartMonday ?: return 0
        val auto = TermDates.weekIndex(start, date)
        if (auto <= 0) return 0
        val week = auto + shift
        return if (week in 1..totalWeeks) week else 0
    }
}

/**
 * 提醒计划器。
 *
 * 三条规则：
 * 1. 只提醒当周实际生效的课程（周次掩码、单双周、节假日空周都自然被排除）；
 * 2. **相邻的同一门课合并为一次提醒**——例如周一 1-2 节与 3-4 节都是「高等数学」，
 *    只在上课前提醒一次，并以整段（1-4 节）的结束时间作为课程结束时间；
 * 3. 触发时刻 = 该段开始时刻 − 提前分钟数，早于「现在」的不再排入。
 */
object ReminderPlanner {

    /** 预排天数：即使两周内没打开过应用，闹钟也已经安排好了 */
    const val DAYS_AHEAD = 14

    /**
     * 合并同一门课里首尾相接的时段（节次连续）。
     * 输入无需排序；仅合并 name 相同且 `下一段起始节次 == 上一段结束节次 + 1` 的相邻项。
     */
    fun mergeAdjacentRuns(courses: List<Course>): List<Course> {
        if (courses.size <= 1) return courses
        val sorted = courses.sortedWith(compareBy({ it.startSection }, { it.endSection }))
        val out = ArrayList<Course>(sorted.size)
        for (course in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.name == course.name && course.startSection == last.endSection + 1) {
                out[out.size - 1] = last.copy(
                    endSection = maxOf(last.endSection, course.endSection),
                    location = last.location.ifBlank { course.location },
                    teacher = last.teacher.ifBlank { course.teacher }
                )
            } else {
                out.add(course)
            }
        }
        return out
    }

    /** 某一天（已合并相邻时段）应提醒的事件，按开始时刻升序 */
    fun eventsForDate(
        courses: List<Course>,
        settings: AppSettings,
        date: LocalDate,
        leadMinutes: Int,
        resolver: WeekResolver
    ): List<ReminderEvent> {
        val week = resolver.weekOf(date)
        if (week == 0) return emptyList()
        val dayOfWeek = date.dayOfWeek.value
        val todays = courses.filter { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }
        if (todays.isEmpty()) return emptyList()

        val lead = leadMinutes.coerceIn(0, 180)
        return mergeAdjacentRuns(todays).mapNotNull { course ->
            val startText = settings.timeSlotOf(course.startSection)?.startTime ?: return@mapNotNull null
            val endText = settings.timeSlotOf(course.endSection)?.endTime ?: startText
            val startTime = TimeText.parse(startText) ?: return@mapNotNull null
            ReminderEvent(
                courseName = course.name,
                teacher = course.teacher,
                location = course.location,
                colorIndex = course.colorIndex,
                dayOfWeek = course.dayOfWeek,
                startSection = course.startSection,
                endSection = course.endSection,
                startText = startText,
                endText = endText,
                triggerAt = LocalDateTime.of(date, startTime).minusMinutes(lead.toLong()),
                leadMinutes = lead
            )
        }.sortedBy { it.triggerAt }
    }

    /**
     * 从 [today] 起 [daysAhead] 天内的全部提醒，按时间升序。
     * 触发时刻早于 [now] 的会被丢弃（重排时不会补发已经过去的提醒）。
     */
    fun plan(
        courses: List<Course>,
        settings: AppSettings,
        today: LocalDate,
        now: LocalDateTime,
        daysAhead: Int = DAYS_AHEAD
    ): List<ReminderEvent> {
        if (!settings.canScheduleReminder) return emptyList()
        if (courses.isEmpty()) return emptyList()
        val resolver = WeekResolver(settings, today)
        if (!resolver.usable) return emptyList()

        val lead = settings.effectiveReminderLead
        val out = ArrayList<ReminderEvent>()
        for (offset in 0 until daysAhead.coerceAtLeast(1)) {
            val date = today.plusDays(offset.toLong())
            out += eventsForDate(courses, settings, date, lead, resolver)
        }
        return out.filter { it.triggerAt.isAfter(now) }.sortedBy { it.triggerAt }
    }

    /** 面向设置页的预览：今天与明天的提醒概览 */
    fun previewForToday(
        courses: List<Course>,
        settings: AppSettings,
        today: LocalDate
    ): List<ReminderEvent> {
        if (!settings.canScheduleReminder) return emptyList()
        val resolver = WeekResolver(settings, today)
        if (!resolver.usable) return emptyList()
        return eventsForDate(courses, settings, today, settings.effectiveReminderLead, resolver)
    }
}
