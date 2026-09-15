package com.lyl.timetable.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 学期日期换算。纯 Kotlin 实现（不引用任何 Android API），
 * 以便课表界面与提醒计划器共用同一套规则，并可被单元测试直接覆盖。
 */
object TermDates {

    val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** 解析 ISO 日期文本，非法或为空时返回 null */
    fun parse(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        if (!ISO_DATE.matches(text)) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    /** 把任意日期归一到所在周的周一（ISO 周，周一为一周之始） */
    fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /**
     * 相对于学期第一周周一，[date] 落在第几周（1 基）。
     * 早于开学日期时返回 0。
     */
    fun weekIndex(termStartMonday: LocalDate, date: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(termStartMonday, date)
        if (days < 0) return 0
        return (days / 7).toInt() + 1
    }
}
