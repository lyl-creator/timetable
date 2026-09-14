package com.lyl.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作息模板验证：按「每天节数 + 每节时长 + 课间休息」生成时间表。
 */
class ScheduleTemplateTest {

    @Test
    fun `按节数时长与课间生成连续作息`() {
        val template = ScheduleTemplate(
            morningStart = "08:00", morningCount = 4,
            afternoonStart = "14:00", afternoonCount = 4,
            eveningStart = "19:00", eveningCount = 0,
            lessonMinutes = 45, breakMinutes = 10
        )
        val slots = template.toTimeSlots()

        assertEquals(8, slots.size)
        assertEquals(TimeSlot(1, "08:00", "08:45"), slots[0])
        assertEquals(TimeSlot(2, "08:55", "09:40"), slots[1])
        assertEquals(TimeSlot(3, "09:50", "10:35"), slots[2])
        assertEquals(TimeSlot(4, "10:45", "11:30"), slots[3])
        assertEquals(TimeSlot(5, "14:00", "14:45"), slots[4])
        assertEquals(TimeSlot(8, "16:45", "17:30"), slots[7])

        // 节次编号应当连续
        assertEquals((1..8).toList(), slots.map { it.section })
    }

    @Test
    fun `节数为零的时段被跳过且编号保持连续`() {
        val template = ScheduleTemplate(
            morningCount = 2,
            afternoonCount = 0,
            eveningCount = 3
        )
        val slots = template.toTimeSlots()
        assertEquals(5, slots.size)
        assertEquals((1..5).toList(), slots.map { it.section })
        // 晚间从 19:00 起，紧接在上午之后编号
        assertEquals("19:00", slots[2].startTime)
    }

    @Test
    fun `每节时长与课间变化会反映到时间表`() {
        val template = ScheduleTemplate(
            morningStart = "09:00", morningCount = 3,
            afternoonCount = 0, eveningCount = 0,
            lessonMinutes = 50, breakMinutes = 5
        )
        val slots = template.toTimeSlots()
        assertEquals(3, slots.size)
        assertEquals("09:00", slots[0].startTime)
        assertEquals("09:50", slots[0].endTime)
        assertEquals("09:55", slots[1].startTime)
        assertEquals("10:45", slots[1].endTime)
        assertEquals("10:50", slots[2].startTime)
    }

    @Test
    fun `totalCount 汇总三段节数`() {
        assertEquals(12, ScheduleTemplate().totalCount)
        assertEquals(6, ScheduleTemplate(morningCount = 2, afternoonCount = 2, eveningCount = 2).totalCount)
        assertEquals(0, ScheduleTemplate(morningCount = 0, afternoonCount = 0, eveningCount = 0).totalCount)
    }

    @Test
    fun `默认作息由模板生成且为十二节`() {
        val slots = TimeSlot.default()
        assertEquals(12, slots.size)
        assertEquals("08:00", slots.first().startTime)
        assertEquals("22:30", slots.last().endTime)
        // 与模板默认值保持一致
        assertEquals(ScheduleTemplate().toTimeSlots(), slots)
    }

    @Test
    fun `时间文本解析与格式化`() {
        assertEquals(java.time.LocalTime.of(8, 5), TimeText.parse("08:05"))
        assertEquals(java.time.LocalTime.of(8, 5), TimeText.parse("8:05"))
        assertEquals(java.time.LocalTime.of(0, 0), TimeText.parse("00:00"))
        assertEquals(java.time.LocalTime.of(23, 59), TimeText.parse("23:59"))

        assertNull(TimeText.parse("24:00"))
        assertNull(TimeText.parse("08:60"))
        assertNull(TimeText.parse(""))
        assertNull(TimeText.parse("abc"))

        assertEquals("08:05", TimeText.format(java.time.LocalTime.of(8, 5)))
        assertTrue(TimeText.isValid("07:30"))
        assertTrue(!TimeText.isValid("7:3"))
    }

    @Test
    fun `时间加减与间隔计算`() {
        assertEquals(45, TimeText.minutesBetween("08:00", "08:45"))
        assertEquals(95, TimeText.minutesBetween("08:00", "09:35"))
        assertEquals("09:35", TimeText.plus("08:00", 95))
        assertEquals("00:10", TimeText.plus("23:40", 30))
        // 跨零点按次日计算
        assertEquals(30, TimeText.minutesBetween("23:50", "00:20"))
    }
}
