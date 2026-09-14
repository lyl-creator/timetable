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
        /** 国内高校常见作息：12 节（上午 4 + 下午 4 + 晚间 4） */
        fun default(): List<TimeSlot> = listOf(
            TimeSlot(1, "08:00", "08:45"),
            TimeSlot(2, "08:55", "09:40"),
            TimeSlot(3, "10:00", "10:45"),
            TimeSlot(4, "10:55", "11:40"),
            TimeSlot(5, "14:00", "14:45"),
            TimeSlot(6, "14:55", "15:40"),
            TimeSlot(7, "16:00", "16:45"),
            TimeSlot(8, "16:55", "17:40"),
            TimeSlot(9, "19:00", "19:45"),
            TimeSlot(10, "19:55", "20:40"),
            TimeSlot(11, "20:50", "21:35"),
            TimeSlot(12, "21:45", "22:30")
        )
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

/** 应用级设置 */
data class AppSettings(
    val termStartDate: String = "",          // ISO-8601，学期第一周的周一
    val totalWeeks: Int = 20,
    val sectionCount: Int = 12,
    val timeSlots: List<TimeSlot> = TimeSlot.default(),
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
