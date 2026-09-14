package com.lyl.timetable.data

/** 单双周类型 */
enum class WeekParity(val label: String, val shortLabel: String) {
    ALL("每周", "全"),
    ODD("单周", "单"),
    EVEN("双周", "双")
}

/**
 * 一门课程（含上课时段与生效周次）。
 *
 * dayOfWeek：1 = 周一 … 7 = 周日
 * startSection / endSection：1 基节次，可跨多节（如 1-2 节连堂）
 * weekMask：周次位掩码，bit(n-1) 为 1 表示第 n 周上课（最多支持 31 周），
 *           可精确表达「1-16 周」「单周」「3,5,7 周」等任意组合
 */
data class Course(
    val id: Long = 0L,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weekMask: Int,
    val colorIndex: Int = 0,
    val note: String = ""
) {
    val sectionCount: Int get() = (endSection - startSection + 1).coerceAtLeast(1)

    fun isActiveInWeek(week: Int): Boolean {
        if (week !in 1..31) return false
        return (weekMask shr (week - 1)) and 1 == 1
    }

    /** 生效周次列表，升序 */
    val activeWeeks: List<Int>
        get() = (1..31).filter { isActiveInWeek(it) }

    /** 形如「1-16周」，若为单双周则追加标注 */
    val weekText: String
        get() {
            val weeks = activeWeeks
            if (weeks.isEmpty()) return "未设置周次"
            val parity = detectParity(weeks)
            val rangePart = if (isContiguous(weeks)) {
                "${weeks.first()}-${weeks.last()}周"
            } else {
                weeks.joinToString("、") + "周"
            }
            return when (parity) {
                WeekParity.ODD -> "$rangePart(单)"
                WeekParity.EVEN -> "$rangePart(双)"
                WeekParity.ALL -> rangePart
            }
        }

    /** 形如「周一 1-2节」 */
    val scheduleText: String
        get() = "${DAY_NAMES[dayOfWeek - 1]} ${startSection}-${endSection}节"

    companion object {
        val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val DAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")

        fun maskOf(startWeek: Int, endWeek: Int, parity: WeekParity): Int {
            var mask = 0
            val lo = startWeek.coerceIn(1, 31)
            val hi = endWeek.coerceIn(lo, 31)
            for (w in lo..hi) {
                val ok = when (parity) {
                    WeekParity.ALL -> true
                    WeekParity.ODD -> w % 2 == 1
                    WeekParity.EVEN -> w % 2 == 0
                }
                if (ok) mask = mask or (1 shl (w - 1))
            }
            return mask
        }

        fun detectParity(weeks: List<Int>): WeekParity {
            if (weeks.isEmpty()) return WeekParity.ALL
            val oddOnly = weeks.all { it % 2 == 1 }
            val evenOnly = weeks.all { it % 2 == 0 }
            return when {
                oddOnly && weeks.size > 1 -> WeekParity.ODD
                evenOnly && weeks.size > 1 -> WeekParity.EVEN
                else -> WeekParity.ALL
            }
        }

        fun isContiguous(weeks: List<Int>): Boolean {
            if (weeks.size <= 1) return true
            for (i in 1 until weeks.size) if (weeks[i] != weeks[i - 1] + 1) return false
            return true
        }
    }
}

/** 单个节次的上下课时间 */
data class TimeSlot(
    val section: Int,
    val startTime: String,
    val endTime: String
) {
    val label: String get() = "$startTime"

    companion object {
        /**
         * 默认作息：上午 4 节 + 下午 4 节 + 晚间 4 节，
         * 每节 45 分钟、课间 10 分钟（由 [ScheduleTemplate] 统一生成，保证与设置页一致）。
         */
        fun default(): List<TimeSlot> = ScheduleTemplate().toTimeSlots()
    }
}

/** 上午 / 下午 / 晚间 时段分组，用于课表视觉分区 */
enum class DayPart(val label: String) {
    MORNING("上午"),
    AFTERNOON("下午"),
    EVENING("晚间");

    companion object {
        fun of(section: Int): DayPart = when {
            section <= 4 -> MORNING
            section <= 8 -> AFTERNOON
            else -> EVENING
        }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

/**
 * 作息模板：按「每天几节 + 每节时长 + 课间休息」批量生成时间表。
 *
 * 上午 / 下午 / 晚间各自指定起始时间与节数，节与节之间按 [breakMinutes] 顺延，
 * 三段的节次从 1 开始连续编号，跳过节数为 0 的时段。
 */
data class ScheduleTemplate(
    val morningStart: String = "08:00",
    val morningCount: Int = 4,
    val afternoonStart: String = "14:00",
    val afternoonCount: Int = 4,
    val eveningStart: String = "19:00",
    val eveningCount: Int = 4,
    val lessonMinutes: Int = 45,
    val breakMinutes: Int = 10,
    /** 第 n 节单独指定的时长（分钟）；缺省时使用 [lessonMinutes] */
    val lessonOverrides: Map<Int, Int> = emptyMap(),
    /** 第 n 节之后那个课间的时长（分钟）；缺省时使用 [breakMinutes] */
    val breakOverrides: Map<Int, Int> = emptyMap()
) {
    val totalCount: Int get() = morningCount + afternoonCount + eveningCount

    /** 第 [section] 节的时长 */
    fun lessonMinutesAt(section: Int): Int =
        (lessonOverrides[section] ?: lessonMinutes).coerceIn(1, 300)

    /** 第 [section] 节之后的课间时长 */
    fun breakMinutesAfter(section: Int): Int =
        (breakOverrides[section] ?: breakMinutes).coerceIn(0, 120)

    fun toTimeSlots(): List<TimeSlot> {
        val out = ArrayList<TimeSlot>(totalCount)
        var section = 1

        fun appendBlock(startText: String, count: Int) {
            val start = TimeText.parse(startText) ?: return
            var cursor = start
            repeat(count.coerceAtLeast(0)) {
                val current = section++
                val end = cursor.plusMinutes(lessonMinutesAt(current).toLong())
                out.add(TimeSlot(current, TimeText.format(cursor), TimeText.format(end)))
                cursor = end.plusMinutes(breakMinutesAfter(current).toLong())
            }
        }

        appendBlock(morningStart, morningCount)
        appendBlock(afternoonStart, afternoonCount)
        appendBlock(eveningStart, eveningCount)
        return out
    }

    /** 是否存在逐节 / 逐课间的自定义设置 */
    val hasOverrides: Boolean get() = lessonOverrides.isNotEmpty() || breakOverrides.isNotEmpty()

    /** 每个时段第一节的固定开始时间（下午 / 晚间不会因上午变化而被推移） */
    fun segmentStartSections(): Map<Int, String> {
        val map = LinkedHashMap<Int, String>(3)
        var section = 1
        if (morningCount > 0) {
            map[section] = morningStart
            section += morningCount
        }
        if (afternoonCount > 0) {
            map[section] = afternoonStart
            section += afternoonCount
        }
        if (eveningCount > 0) map[section] = eveningStart
        return map
    }

    /**
     * 从第 [startSection] 节起按当前模板参数重排时间，用于「改了某节时长或某个课间后，
     * 其后的节次自动顺延」。遇到时段边界（下午 / 晚间第一节）时使用该段的固定开始时间。
     */
    fun reflow(slots: List<TimeSlot>, startSection: Int): List<TimeSlot> {
        if (slots.isEmpty()) return slots
        val boundaries = segmentStartSections()
        val result = ArrayList<TimeSlot>(slots.size)
        var cursor: String? = null

        for (slot in slots) {
            val affected = slot.section >= startSection
            val start = if (affected) {
                boundaries[slot.section] ?: cursor ?: slot.startTime
            } else {
                slot.startTime
            }
            val end = if (affected) {
                TimeText.plus(start, lessonMinutesAt(slot.section)) ?: slot.endTime
            } else {
                slot.endTime
            }
            result.add(if (affected) slot.copy(startTime = start, endTime = end) else slot)
            cursor = TimeText.plus(end, breakMinutesAfter(slot.section))
        }
        return result
    }
}

/** "HH:mm" 文本与 LocalTime 互转 */
object TimeText {
    private val PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")

    fun parse(text: String): java.time.LocalTime? {
        val m = PATTERN.find(text.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return java.time.LocalTime.of(h, min)
    }

    fun format(time: java.time.LocalTime): String =
        "%02d:%02d".format(time.hour, time.minute)

    fun isValid(text: String): Boolean = parse(text) != null

    /** 两个时间点之间的分钟数（跨零点时按次日计算） */
    fun minutesBetween(start: String, end: String): Int? {
        val s = parse(start) ?: return null
        val e = parse(end) ?: return null
        var diff = java.time.Duration.between(s, e).toMinutes().toInt()
        if (diff < 0) diff += 24 * 60
        return diff
    }

    /** 在给定时间上增加分钟数 */
    fun plus(start: String, minutes: Int): String? =
        parse(start)?.plusMinutes(minutes.toLong())?.let { format(it) }
}

/** 应用级设置 */
data class AppSettings(
    val termStartDate: String = "",          // ISO-8601，学期第一周的周一
    val totalWeeks: Int = 20,
    val sectionCount: Int = 12,
    val timeSlots: List<TimeSlot> = TimeSlot.default(),
    val scheduleTemplate: ScheduleTemplate = ScheduleTemplate(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val showWeekend: Boolean = true,
    val currentWeekOverride: Int = 0,        // 0 表示按开学日期自动计算
    val studentName: String = ""
) {
    fun timeSlotOf(section: Int): TimeSlot? = timeSlots.firstOrNull { it.section == section }

    fun timeRangeText(startSection: Int, endSection: Int): String {
        val s = timeSlotOf(startSection)?.startTime ?: return ""
        val e = timeSlotOf(endSection)?.endTime ?: return s
        return "$s - $e"
    }

    companion object {
        val ACCENTS = listOf("星海蓝", "晨曦紫", "青竹绿", "落日橙", "樱粉", "午夜灰")
    }
}
