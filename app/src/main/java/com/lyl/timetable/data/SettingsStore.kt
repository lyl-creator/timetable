package com.lyl.timetable.data

import android.content.Context
import android.content.SharedPreferences

/** 设置持久化（SharedPreferences），节次时间以紧凑字符串形式存放 */
internal class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("timetable_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val slots = decodeSlots(prefs.getString(KEY_SLOTS, null))
        val sectionCount = prefs.getInt(KEY_SECTION_COUNT, slots.size.coerceAtLeast(12))
        // 1.8.0 及更早版本只有一个「显示周末」总开关；升级后用它作为两个新开关的初始值，
        // 这样此前关掉周末的用户不会在升级后突然多出两天
        val legacyShowWeekend = prefs.getBoolean(KEY_WEEKEND, true)
        return AppSettings(
            termStartDate = prefs.getString(KEY_TERM_START, "").orEmpty(),
            totalWeeks = prefs.getInt(KEY_TOTAL_WEEKS, 20).coerceIn(1, 31),
            sectionCount = sectionCount.coerceIn(4, 20),
            timeSlots = if (slots.isEmpty()) TimeSlot.default() else slots,
            scheduleTemplate = decodeTemplate(prefs.getString(KEY_TEMPLATE, null)),
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
            }.getOrDefault(ThemeMode.SYSTEM),
            accentIndex = prefs.getInt(KEY_ACCENT, 0),
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            showSaturday = prefs.getBoolean(KEY_SATURDAY, legacyShowWeekend),
            showSunday = prefs.getBoolean(KEY_SUNDAY, legacyShowWeekend),
            currentWeekOverride = prefs.getInt(KEY_WEEK_OVERRIDE, 0),
            studentName = prefs.getString(KEY_STUDENT, "").orEmpty(),
            reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false),
            reminderLeadMinutes = prefs.getInt(
                KEY_REMINDER_LEAD,
                AppSettings.DEFAULT_REMINDER_LEAD
            ),
            keepAliveEnabled = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_TERM_START, settings.termStartDate)
            .putInt(KEY_TOTAL_WEEKS, settings.totalWeeks)
            .putInt(KEY_SECTION_COUNT, settings.sectionCount)
            .putString(KEY_SLOTS, encodeSlots(settings.timeSlots))
            .putString(KEY_TEMPLATE, encodeTemplate(settings.scheduleTemplate))
            .putString(KEY_THEME, settings.themeMode.name)
            .putInt(KEY_ACCENT, settings.accentIndex)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.useDynamicColor)
            .putBoolean(KEY_SATURDAY, settings.showSaturday)
            .putBoolean(KEY_SUNDAY, settings.showSunday)
            .putInt(KEY_WEEK_OVERRIDE, settings.currentWeekOverride)
            .putString(KEY_STUDENT, settings.studentName)
            .putBoolean(KEY_REMINDER_ENABLED, settings.reminderEnabled)
            .putInt(KEY_REMINDER_LEAD, settings.effectiveReminderLead)
            .putBoolean(KEY_KEEP_ALIVE, settings.keepAliveEnabled)
            .apply()
    }

    private fun encodeTemplate(t: ScheduleTemplate): String = listOf(
        t.morningStart, t.morningCount.toString(),
        t.afternoonStart, t.afternoonCount.toString(),
        t.eveningStart, t.eveningCount.toString(),
        t.lessonMinutes.toString(), t.breakMinutes.toString(),
        encodeOverrides(t.lessonOverrides), encodeOverrides(t.breakOverrides)
    ).joinToString("|")

    private fun encodeOverrides(map: Map<Int, Int>): String =
        map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    private fun decodeOverrides(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<Int, Int>()
        for (part in raw.split(';')) {
            val f = part.split(':')
            if (f.size != 2) continue
            val section = f[0].trim().toIntOrNull() ?: continue
            val minutes = f[1].trim().toIntOrNull() ?: continue
            if (section in 1..31 && minutes in 0..300) out[section] = minutes
        }
        return out
    }

    private fun decodeTemplate(raw: String?): ScheduleTemplate {
        if (raw.isNullOrBlank()) return ScheduleTemplate()
        val f = raw.split("|")
        if (f.size < 8) return ScheduleTemplate()
        val fallback = ScheduleTemplate()
        return ScheduleTemplate(
            morningStart = f[0].takeIf { TimeText.isValid(it) } ?: fallback.morningStart,
            morningCount = f[1].toIntOrNull()?.coerceIn(0, 12) ?: fallback.morningCount,
            afternoonStart = f[2].takeIf { TimeText.isValid(it) } ?: fallback.afternoonStart,
            afternoonCount = f[3].toIntOrNull()?.coerceIn(0, 12) ?: fallback.afternoonCount,
            eveningStart = f[4].takeIf { TimeText.isValid(it) } ?: fallback.eveningStart,
            eveningCount = f[5].toIntOrNull()?.coerceIn(0, 12) ?: fallback.eveningCount,
            lessonMinutes = f[6].toIntOrNull()?.coerceIn(20, 180) ?: fallback.lessonMinutes,
            breakMinutes = f[7].toIntOrNull()?.coerceIn(0, 60) ?: fallback.breakMinutes,
            lessonOverrides = decodeOverrides(f.getOrNull(8)),
            breakOverrides = decodeOverrides(f.getOrNull(9))
        )
    }

    private fun encodeSlots(slots: List<TimeSlot>): String =
        slots.joinToString(";") { "${it.section}|${it.startTime}|${it.endTime}" }

    private fun decodeSlots(raw: String?): List<TimeSlot> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<TimeSlot>()
        for (part in raw.split(';')) {
            val f = part.split('|')
            if (f.size < 3) continue
            val section = f[0].toIntOrNull() ?: continue
            out.add(TimeSlot(section, f[1], f[2]))
        }
        return out.sortedBy { it.section }
    }

    private companion object {
        const val KEY_TERM_START = "term_start"
        const val KEY_TOTAL_WEEKS = "total_weeks"
        const val KEY_SECTION_COUNT = "section_count"
        const val KEY_SLOTS = "time_slots"
        const val KEY_TEMPLATE = "schedule_template"
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent_index"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        /** 旧版总开关，仅作为新开关的默认值来源，不再写入 */
        const val KEY_WEEKEND = "show_weekend"
        const val KEY_SATURDAY = "show_saturday"
        const val KEY_SUNDAY = "show_sunday"
        const val KEY_WEEK_OVERRIDE = "week_override"
        const val KEY_STUDENT = "student_name"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_LEAD = "reminder_lead_minutes"
        const val KEY_KEEP_ALIVE = "keep_alive_enabled"
    }
}
