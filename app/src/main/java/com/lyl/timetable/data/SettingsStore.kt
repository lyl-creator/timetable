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
        return AppSettings(
            termStartDate = prefs.getString(KEY_TERM_START, "").orEmpty(),
            totalWeeks = prefs.getInt(KEY_TOTAL_WEEKS, 20).coerceIn(1, 31),
            sectionCount = sectionCount.coerceIn(4, 20),
            timeSlots = if (slots.isEmpty()) TimeSlot.default() else slots,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
            }.getOrDefault(ThemeMode.SYSTEM),
            accentIndex = prefs.getInt(KEY_ACCENT, 0),
            showWeekend = prefs.getBoolean(KEY_WEEKEND, true),
            currentWeekOverride = prefs.getInt(KEY_WEEK_OVERRIDE, 0),
            studentName = prefs.getString(KEY_STUDENT, "").orEmpty()
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_TERM_START, settings.termStartDate)
            .putInt(KEY_TOTAL_WEEKS, settings.totalWeeks)
            .putInt(KEY_SECTION_COUNT, settings.sectionCount)
            .putString(KEY_SLOTS, encodeSlots(settings.timeSlots))
            .putString(KEY_THEME, settings.themeMode.name)
            .putInt(KEY_ACCENT, settings.accentIndex)
            .putBoolean(KEY_WEEKEND, settings.showWeekend)
            .putInt(KEY_WEEK_OVERRIDE, settings.currentWeekOverride)
            .putString(KEY_STUDENT, settings.studentName)
            .apply()
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
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent_index"
        const val KEY_WEEKEND = "show_weekend"
        const val KEY_WEEK_OVERRIDE = "week_override"
        const val KEY_STUDENT = "student_name"
    }
}
