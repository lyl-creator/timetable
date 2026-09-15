package com.lyl.timetable.reminder

import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.WeekParity
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上课提醒的计划逻辑验证。
 *
 * 学期基准：2026-03-02（周一）为第 1 周；默认作息下各节次时间为
 * 1 节 08:00-08:45、2 节 08:55-09:40、3 节 09:50-10:35、4 节 10:45-11:30。
 */
class ReminderPlannerTest {

    private val termStart = "2026-03-02"
    private val monday = LocalDate.of(2026, 3, 2)

    private fun settings(
        lead: Int = 10,
        override: Int = 0,
        enabled: Boolean = true,
        start: String = termStart
    ) = AppSettings(
        termStartDate = start,
        totalWeeks = 20,
        reminderEnabled = enabled,
        reminderLeadMinutes = lead,
        currentWeekOverride = override
    )

    private fun course(
        name: String,
        day: Int = 1,
        startSection: Int,
        endSection: Int,
        startWeek: Int = 1,
        endWeek: Int = 20,
        parity: WeekParity = WeekParity.ALL,
        location: String = ""
    ) = Course(
        name = name,
        dayOfWeek = day,
        startSection = startSection,
        endSection = endSection,
        weekMask = Course.maskOf(startWeek, endWeek, parity),
        location = location
    )

    // ------------------------------------------------------------------
    //  相邻同一门课的合并
    // ------------------------------------------------------------------

    @Test
    fun `相邻的同一门课合并为一次提醒并覆盖整段节次`() {
        val courses = listOf(
            course("高等数学", startSection = 1, endSection = 2),
            course("高等数学", startSection = 3, endSection = 4)
        )
        val merged = ReminderPlanner.mergeAdjacentRuns(courses)

        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startSection)
        assertEquals(4, merged[0].endSection)
    }

    @Test
    fun `相邻但不同名的课程不会被合并`() {
        val courses = listOf(
            course("高等数学", startSection = 1, endSection = 2),
            course("大学物理", startSection = 3, endSection = 4)
        )
        assertEquals(2, ReminderPlanner.mergeAdjacentRuns(courses).size)
    }

    @Test
    fun `同一天里节次不相接的同一门课不会被合并`() {
        val courses = listOf(
            course("高等数学", startSection = 1, endSection = 2),
            course("高等数学", startSection = 4, endSection = 5)
        )
        assertEquals(2, ReminderPlanner.mergeAdjacentRuns(courses).size)
    }

    @Test
    fun `乱序输入也能正确合并连续节次`() {
        val courses = listOf(
            course("高等数学", startSection = 5, endSection = 6),
            course("高等数学", startSection = 1, endSection = 2),
            course("高等数学", startSection = 3, endSection = 4)
        )
        val merged = ReminderPlanner.mergeAdjacentRuns(courses)

        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startSection)
        assertEquals(6, merged[0].endSection)
    }

    @Test
    fun `合并时保留首个时段的教室并补全缺失信息`() {
        val courses = listOf(
            course("高等数学", startSection = 1, endSection = 2, location = "N楼-411"),
            course("高等数学", startSection = 3, endSection = 4, location = "M楼-102")
        )
        val merged = ReminderPlanner.mergeAdjacentRuns(courses)

        assertEquals("N楼-411", merged[0].location)
    }

    // ------------------------------------------------------------------
    //  触发时刻
    // ------------------------------------------------------------------

    @Test
    fun `连续四节同一门课只产生一条提醒且用整段结束时间`() {
        val courses = listOf(
            course("高等数学", startSection = 1, endSection = 2),
            course("高等数学", startSection = 3, endSection = 4)
        )
        val events = ReminderPlanner.eventsForDate(
            courses, settings(lead = 10), monday, 10, WeekResolver(settings(), monday)
        )

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("08:00", event.startText)
        assertEquals("11:30", event.endText)
        assertEquals(1, event.startSection)
        assertEquals(4, event.endSection)
        assertEquals(LocalDateTime.of(2026, 3, 2, 7, 50), event.triggerAt)
        assertEquals(210, event.durationMinutes)
    }

    @Test
    fun `提前时间按设置换算为触发时刻`() {
        val courses = listOf(course("大学英语", startSection = 3, endSection = 4))
        val resolver = WeekResolver(settings(), monday)

        for (lead in AppSettings.REMINDER_LEADS) {
            val event = ReminderPlanner.eventsForDate(courses, settings(lead), monday, lead, resolver).single()
            assertEquals(
                LocalDateTime.of(2026, 3, 2, 9, 50).minusMinutes(lead.toLong()),
                event.triggerAt
            )
            assertEquals(lead, event.leadMinutes)
        }
    }

    @Test
    fun `缺少对应节次时间时不产生提醒`() {
        val courses = listOf(course("体育", startSection = 13, endSection = 13))
        val events = ReminderPlanner.eventsForDate(
            courses, settings(), monday, 10, WeekResolver(settings(), monday)
        )
        assertTrue(events.isEmpty())
    }

    // ------------------------------------------------------------------
    //  周次过滤
    // ------------------------------------------------------------------

    @Test
    fun `单双周课程只在其生效的周次产生提醒`() {
        val odd = listOf(
            course("形势与政策", startSection = 1, endSection = 2, startWeek = 1, endWeek = 5, parity = WeekParity.ODD)
        )
        val resolver = WeekResolver(settings(), monday)

        // 第 1 周（单周）有提醒，第 2 周（双周）没有
        assertEquals(1, ReminderPlanner.eventsForDate(odd, settings(), monday, 10, resolver).size)
        assertEquals(
            0,
            ReminderPlanner.eventsForDate(odd, settings(), monday.plusWeeks(1), 10, resolver).size
        )
    }

    @Test
    fun `超出学期范围的日期不产生提醒`() {
        val courses = listOf(course("高等数学", startSection = 1, endSection = 2))
        val resolver = WeekResolver(settings(), monday)

        // 开学之前
        assertTrue(ReminderPlanner.eventsForDate(courses, settings(), monday.minusWeeks(1), 10, resolver).isEmpty())
        // 总周数之后
        assertTrue(ReminderPlanner.eventsForDate(courses, settings(), monday.plusWeeks(25), 10, resolver).isEmpty())
    }

    @Test
    fun `未设置开学日期时不排布任何提醒`() {
        val courses = listOf(course("高等数学", startSection = 1, endSection = 2))
        val plan = ReminderPlanner.plan(
            courses,
            settings(start = ""),
            monday,
            LocalDateTime.of(2026, 3, 2, 7, 0)
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `提醒开关关闭时不排布任何提醒`() {
        val courses = listOf(course("高等数学", startSection = 1, endSection = 2))
        val plan = ReminderPlanner.plan(
            courses,
            settings(enabled = false),
            monday,
            LocalDateTime.of(2026, 3, 2, 7, 0)
        )
        assertTrue(plan.isEmpty())
    }

    // ------------------------------------------------------------------
    //  整体排布
    // ------------------------------------------------------------------

    @Test
    fun `排布覆盖未来两周内的全部课程且按时间升序`() {
        val courses = listOf(
            course("高等数学", day = 1, startSection = 1, endSection = 2),
            course("大学物理", day = 3, startSection = 5, endSection = 6),
            course("程序设计", day = 5, startSection = 3, endSection = 4)
        )
        val plan = ReminderPlanner.plan(
            courses,
            settings(lead = 15),
            monday,
            LocalDateTime.of(2026, 3, 2, 6, 0),
            daysAhead = 14
        )

        assertEquals(6, plan.size)
        assertEquals(plan.sortedBy { it.triggerAt }, plan)
        // 第一周的三门课都在窗口内，最早的是周一上午的高等数学
        assertEquals("高等数学", plan.first().courseName)
        assertEquals(LocalDateTime.of(2026, 3, 2, 7, 45), plan.first().triggerAt)
    }

    @Test
    fun `已经过去的触发时刻不会被重复排入`() {
        val courses = listOf(
            course("高等数学", day = 1, startSection = 1, endSection = 2),
            course("大学物理", day = 1, startSection = 5, endSection = 6)
        )
        // 周一 09:00：上午第一节已过，下午第一节课还没到
        val plan = ReminderPlanner.plan(
            courses,
            settings(lead = 10),
            monday,
            LocalDateTime.of(2026, 3, 2, 9, 0),
            daysAhead = 1
        )

        assertEquals(1, plan.size)
        assertEquals("大学物理", plan[0].courseName)
    }

    @Test
    fun `同一天上午与下午的同一门课分别提醒`() {
        val courses = listOf(
            course("高等数学", day = 1, startSection = 1, endSection = 2),
            course("高等数学", day = 1, startSection = 5, endSection = 6)
        )
        val plan = ReminderPlanner.plan(
            courses,
            settings(),
            monday,
            LocalDateTime.of(2026, 3, 2, 6, 0),
            daysAhead = 1
        )

        assertEquals(2, plan.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 7, 50), plan[0].triggerAt)
        assertEquals(LocalDateTime.of(2026, 3, 2, 13, 50), plan[1].triggerAt)
    }

    // ------------------------------------------------------------------
    //  手动指定当前周时的平移
    // ------------------------------------------------------------------

    @Test
    fun `手动指定当前周时提醒整体平移到该周`() {
        val today = LocalDate.of(2026, 3, 16)          // 自动推算为第 3 周
        val manual = settings(override = 5)            // 用户改为第 5 周
        val resolver = WeekResolver(manual, today)
        assertEquals(5, resolver.weekOf(today))

        // 只在第 5 周生效的课程，应当落在今天
        val onlyWeek5 = listOf(
            course("专题讲座", day = 1, startSection = 1, endSection = 2, startWeek = 5, endWeek = 5)
        )
        val plan = ReminderPlanner.plan(
            onlyWeek5, manual, today, LocalDateTime.of(2026, 3, 16, 6, 0), daysAhead = 1
        )
        assertEquals(1, plan.size)
        assertEquals(LocalDateTime.of(2026, 3, 16, 7, 50), plan[0].triggerAt)
    }

    @Test
    fun `未手动指定周次时按开学日期自动推算`() {
        val resolver = WeekResolver(settings(), LocalDate.of(2026, 3, 16))
        assertEquals(3, resolver.weekOf(LocalDate.of(2026, 3, 16)))
        assertEquals(4, resolver.weekOf(LocalDate.of(2026, 3, 23)))
        assertEquals(0, resolver.weekOf(LocalDate.of(2026, 2, 23)))
    }

    // ------------------------------------------------------------------
    //  通知文案
    // ------------------------------------------------------------------

    @Test
    fun `通知文案包含提前时间时间段与地点`() {
        val courses = listOf(
            course("高等数学", startSection = 1, endSection = 2, location = "N楼-411")
        )
        val event = ReminderPlanner.eventsForDate(
            courses, settings(lead = 15), monday, 15, WeekResolver(settings(), monday)
        ).single()

        assertEquals("高等数学", event.title)
        // 1-2 节：08:00 开始，第 2 节 09:40 结束
        assertEquals("15 分钟后  ·  08:00-09:40  ·  N楼-411", event.detail)
    }

    @Test
    fun `提前时间为零时文案显示现在`() {
        val courses = listOf(course("高等数学", startSection = 1, endSection = 2))
        val event = ReminderPlanner.eventsForDate(
            courses, settings(lead = 0), monday, 0, WeekResolver(settings(), monday)
        ).single()

        assertTrue(event.detail.startsWith("现在"))
    }

    @Test
    fun `提前时间取值收敛到预设档位`() {
        assertEquals(10, settings(lead = 10).effectiveReminderLead)
        assertEquals(30, settings(lead = 30).effectiveReminderLead)
        // 异常数据回落到最接近的一档
        assertEquals(30, settings(lead = 999).effectiveReminderLead)
        assertEquals(5, settings(lead = -3).effectiveReminderLead)
    }

    @Test
    fun `未设置开学日期时不具备排布条件`() {
        assertFalse(settings(start = "").canScheduleReminder)
        assertTrue(settings().canScheduleReminder)
    }
}
