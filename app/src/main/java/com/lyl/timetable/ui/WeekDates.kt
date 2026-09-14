package com.lyl.timetable.ui

import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.TimetableRepository
import java.time.LocalDate

/** 指定教学周对应的 7 个日期（周一为首日）；未设置开学日期时返回空位 */
fun weekDatesOf(settings: AppSettings, week: Int): List<LocalDate?> {
    val start = TimetableRepository.parseDate(settings.termStartDate) ?: return List(7) { null }
    val monday = TimetableRepository.mondayOf(start).plusWeeks((week - 1).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

/** 显示用：9月14日 */
fun formatChineseDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

/** 学期第几周的说明文本 */
fun weekSubtitle(settings: AppSettings, week: Int, courseCount: Int): String {
    val parts = ArrayList<String>(3)
    parts.add("第 $week 周")
    val start = TimetableRepository.parseDate(settings.termStartDate)
    if (start != null) {
        val dates = weekDatesOf(settings, week)
        val a = dates.firstOrNull()
        val b = dates.lastOrNull()
        if (a != null && b != null) parts.add("${a.monthValue}.${a.dayOfMonth} - ${b.monthValue}.${b.dayOfMonth}")
    } else {
        parts.add("未设置开学日期")
    }
    parts.add("共 $courseCount 门课")
    return parts.joinToString("  ·  ")
}
