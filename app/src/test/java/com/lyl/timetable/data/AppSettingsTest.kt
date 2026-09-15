package com.lyl.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课表显示天数的组合验证。
 *
 * 周六与周日各自独立开关，且结果必须保持升序 —— 课表网格按这个列表的顺序取星期与日期
 * （见 `WeekScreen` 中「按星期取对应日期」的映射），顺序错了会让日期与星期错位。
 */
class AppSettingsTest {

    private fun days(saturday: Boolean, sunday: Boolean): List<Int> =
        AppSettings(showSaturday = saturday, showSunday = sunday).visibleDays

    @Test
    fun `默认显示周一到周日`() {
        assertEquals((1..7).toList(), days(saturday = true, sunday = true))
    }

    @Test
    fun `周一至周五在任意组合下都始终显示且保持升序`() {
        for (saturday in listOf(true, false)) {
            for (sunday in listOf(true, false)) {
                val visible = days(saturday, sunday)
                assertEquals("周一至周五应始终存在", listOf(1, 2, 3, 4, 5), visible.take(5))
                assertEquals("结果应为升序", visible.sorted(), visible)
            }
        }
    }

    @Test
    fun `可只隐藏周六`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 7), days(saturday = false, sunday = true))
    }

    @Test
    fun `可只隐藏周日`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), days(saturday = true, sunday = false))
    }

    @Test
    fun `两个都关闭时只剩工作日`() {
        assertEquals(listOf(1, 2, 3, 4, 5), days(saturday = false, sunday = false))
    }
}
