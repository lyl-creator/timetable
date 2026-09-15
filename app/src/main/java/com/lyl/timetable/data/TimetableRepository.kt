package com.lyl.timetable.data

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 课程与设置的统一仓库。数据全部落在设备本地，应用不申请任何网络权限。
 */
class TimetableRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = CourseDao(appContext)
    private val settingsStore = SettingsStore(appContext)

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _courses.value = dao.all()
    }

    // ---------------- 课程 ----------------

    fun addCourse(course: Course): Long {
        val id = dao.insert(course)
        reload()
        return id
    }

    fun updateCourse(course: Course): Boolean {
        val ok = dao.update(course)
        reload()
        return ok
    }

    fun deleteCourse(id: Long): Boolean {
        val ok = dao.delete(id)
        reload()
        return ok
    }

    fun clearCourses(): Int {
        val n = dao.deleteAll()
        reload()
        return n
    }

    /**
     * 批量导入。
     * @param replaceExisting true 时先清空原有课表
     * @return 实际写入条数
     */
    fun importCourses(courses: List<Course>, replaceExisting: Boolean): Int {
        if (replaceExisting) dao.deleteAll()
        val n = dao.insertAll(courses)
        reload()
        return n
    }

    fun nextColorIndex(): Int {
        val used = _courses.value.groupingBy { it.colorIndex }.eachCount()
        val max = _courses.value.maxOfOrNull { it.colorIndex } ?: -1
        return (max + 1) % 12
    }

    // ---------------- 设置 ----------------

    fun updateSettings(settings: AppSettings) {
        settingsStore.save(settings)
        _settings.value = settings
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        updateSettings(block(_settings.value))
    }

    // ---------------- 派生数据 ----------------

    /** 当前教学周（依据开学日期推算；未设置或超出范围时回退到 1） */
    fun currentWeek(today: LocalDate = LocalDate.now()): Int {
        val settings = _settings.value
        if (settings.currentWeekOverride > 0) return settings.currentWeekOverride
        val start = parseDate(settings.termStartDate) ?: return 1
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return 1
        val week = (days / 7).toInt() + 1
        return week.coerceIn(1, settings.totalWeeks)
    }

    fun coursesAt(week: Int): List<Course> = _courses.value.filter { it.isActiveInWeek(week) }

    fun coursesAt(week: Int, dayOfWeek: Int): List<Course> =
        coursesAt(week).filter { it.dayOfWeek == dayOfWeek }

    /** 统计实际用到的最大节次，用于压缩课表行数 */
    fun maxUsedSection(): Int = _courses.value.maxOfOrNull { it.endSection } ?: 0

    companion object {
        val ISO_DATE = TermDates.ISO_DATE

        /** 解析 ISO 日期文本，非法或为空时返回 null */
        fun parseDate(text: String?): LocalDate? = TermDates.parse(text)

        /** 把任意日期归一到所在周的周一（ISO 周，周一为一周之始） */
        fun mondayOf(date: LocalDate): LocalDate = TermDates.mondayOf(date)
    }
}
