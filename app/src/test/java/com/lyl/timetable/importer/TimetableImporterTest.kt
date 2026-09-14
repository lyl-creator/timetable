package com.lyl.timetable.importer

import com.lyl.timetable.data.Course
import com.lyl.timetable.data.WeekParity
import com.lyl.timetable.xls.SheetTable
import com.lyl.timetable.xls.SpreadsheetParser
import com.lyl.timetable.xls.Workbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入映射验证：把解析出的表格转换成课程记录。
 * 重点验证「每周课程可不同」——同一门课不同周次可对应不同时间/地点。
 */
class TimetableImporterTest {

    private fun parse(fileName: String): SpreadsheetParser.Result.Success {
        val bytes = javaClass.classLoader!!.getResourceAsStream("samples/$fileName")?.readBytes()
            ?: error("缺少测试样本：$fileName")
        val result = SpreadsheetParser.parseBytes(bytes, fileName)
        assertTrue("解析失败：$result", result is SpreadsheetParser.Result.Success)
        return result as SpreadsheetParser.Result.Success
    }

    private fun analyze(fileName: String) = TimetableImporter().analyze(parse(fileName).workbook)

    // ---------------- 列表式 ----------------

    @Test
    fun `列表式课表应识别为按行清单并解析出全部时段`() {
        val outcome = analyze("list_style.xls")
        assertTrue(outcome.success)
        assertEquals(TimetableImporter.Strategy.LIST, outcome.strategy)

        // 9 门不同课程、10 个上课时段（大学物理分前后半学期两个时段）
        assertEquals(9, outcome.courses.map { it.name }.distinct().size)
        assertEquals(10, outcome.courses.size)

        val math = outcome.courses.first { it.name == "高等数学A" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
        assertEquals("张伟", math.teacher)
        assertEquals("教三301", math.location)
        assertTrue("1-16 周应全部生效", math.isActiveInWeek(1) && math.isActiveInWeek(16))
        assertTrue("第 17 周不应生效", !math.isActiveInWeek(17))
    }

    @Test
    fun `单双周应被正确解析为掩码`() {
        val outcome = analyze("list_style.xls")

        val english = outcome.courses.first { it.name.startsWith("大学英语") }
        assertTrue("单周课程第 1 周生效", english.isActiveInWeek(1))
        assertTrue("单周课程第 2 周不生效", !english.isActiveInWeek(2))
        assertTrue("单周课程第 15 周生效", english.isActiveInWeek(15))
        assertEquals(WeekParity.ODD, Course.detectParity(english.activeWeeks))

        val dataStructure = outcome.courses.first { it.name == "数据结构" }
        assertTrue("双周课程第 10 周生效", dataStructure.isActiveInWeek(10))
        assertTrue("双周课程第 9 周不生效", !dataStructure.isActiveInWeek(9))
        assertEquals(WeekParity.EVEN, Course.detectParity(dataStructure.activeWeeks))
    }

    @Test
    fun `同一门课不同周次不同教室应保留为两个时段`() {
        val outcome = analyze("list_style.xls")
        val physics = outcome.courses.filter { it.name == "大学物理" }

        assertEquals("大学物理应有 2 个时段", 2, physics.size)

        val firstHalf = physics.first { it.location == "理科楼B203" }
        val secondHalf = physics.first { it.location == "理科楼B205" }

        assertTrue("前 8 周在 B203", firstHalf.isActiveInWeek(3) && !firstHalf.isActiveInWeek(12))
        assertTrue("后 8 周在 B205", secondHalf.isActiveInWeek(12) && !secondHalf.isActiveInWeek(3))

        // 任意一周只应有 1 个时段生效，两个时段不会互相冲突
        assertEquals(1, physics.count { it.isActiveInWeek(3) })
        assertEquals(1, physics.count { it.isActiveInWeek(12) })
    }

    @Test
    fun `离散周次应被完整保留而不是近似成区间`() {
        val outcome = analyze("list_style.xls")
        val policy = outcome.courses.first { it.name == "形势与政策" }
        assertEquals(listOf(1, 3, 5, 7, 9), policy.activeWeeks)
        assertTrue("不应在第 11 周生效", !policy.isActiveInWeek(11))
        assertTrue("不应在第 2 周生效", !policy.isActiveInWeek(2))
    }

    // ---------------- 矩阵式 ----------------

    @Test
    fun `矩阵式课表应识别星期列与节次行`() {
        val outcome = analyze("matrix_style.xls")
        assertTrue(outcome.success)
        assertEquals(TimetableImporter.Strategy.MATRIX, outcome.strategy)

        val math = outcome.courses.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals("第 1-2 节应合并为一门跨节课程", 1, math.startSection)
        assertEquals(2, math.endSection)
        assertEquals("教三301", math.location)
        assertEquals("张伟", math.teacher)

        val pe = outcome.courses.first { it.name.contains("体育") }
        assertEquals(4, pe.dayOfWeek)
        assertEquals(7, pe.startSection)
        assertEquals(8, pe.endSection)

        assertTrue("矩阵中应识别出多门课程", outcome.courses.map { it.name }.distinct().size >= 6)
    }

    @Test
    fun `紧凑写法应能拆出课程名教师与教室`() {
        val outcome = analyze("compact_style.xls")
        assertTrue(outcome.success)

        val math = outcome.courses.firstOrNull { it.name.startsWith("高等数学") }
        assertNotNull("应识别出高等数学，实际：${outcome.courses.map { it.name }}", math)
        requireNotNull(math)
        assertTrue("周次应被识别并剥离", math.isActiveInWeek(5) && !math.isActiveInWeek(20))
        assertEquals("教三301", math.location)
        assertEquals("张伟", math.teacher)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
    }

    @Test
    fun `HTML 伪 xls 应能识别出课程`() {
        val outcome = analyze("html_style.xls")
        assertTrue(outcome.success)
        val names = outcome.courses.map { it.name }
        assertTrue("应识别出高等数学：$names", names.any { it.contains("高等数学") })
        assertTrue("应识别出数据结构：$names", names.any { it.contains("数据结构") })
        assertTrue("应识别出体育：$names", names.any { it.contains("体育") })
    }

    @Test
    fun `xlsx 与 csv 同样应被支持`() {
        val xlsx = analyze("modern_style.xlsx")
        assertTrue(xlsx.success)
        assertTrue("xlsx 应识别出至少 8 个时段", xlsx.courses.size >= 8)

        val csv = analyze("gbk_style.csv")
        assertTrue(csv.success)
        assertEquals(2, csv.courses.size)
        assertTrue(csv.courses.any { it.name == "高等数学" })
    }

    // ---------------- 字段解析 ----------------

    @Test
    fun `解析星期与节次的各种写法`() {
        val importer = TimetableImporter()
        assertEquals(1, importer.parseDay("周一"))
        assertEquals(3, importer.parseDay("星期三"))
        assertEquals(7, importer.parseDay("周日"))
        assertEquals(7, importer.parseDay("星期天"))
        assertEquals(5, importer.parseDay("周5"))
        assertEquals(2, importer.parseDay("Tuesday"))
        assertEquals(2, importer.parseDay("周二 1-2节"))

        assertEquals(1, importer.parseSections("1-2节")?.first)
        assertEquals(2, importer.parseSections("1-2节")?.last)
        assertEquals(3, importer.parseSections("第3节")?.first)
        assertEquals(3, importer.parseSections("3")?.first)
        assertEquals(3, importer.parseSections("03,04")?.first)
        assertEquals(4, importer.parseSections("03,04")?.last)
        // 0102 表示第 1-2 节
        assertEquals(1, importer.parseSections("0102")?.first)
        assertEquals(2, importer.parseSections("0102")?.last)
        assertEquals(6, importer.parseSections("5-6")?.last)
    }

    @Test
    fun `周次文本的各种写法`() {
        val range = TimetableImporter.WeekPatterns.find("高等数学(1-16周)张伟")
        assertNotNull(range)
        requireNotNull(range)
        assertEquals(Course.maskOf(1, 16, WeekParity.ALL), range.mask)
        assertTrue("剩余文本应保留课程名", range.remaining.contains("高等数学"))
        assertTrue("剩余文本应保留教师", range.remaining.contains("张伟"))

        val odd = TimetableImporter.WeekPatterns.find("1-16周(单)")
        assertNotNull(odd)
        assertEquals(Course.maskOf(1, 16, WeekParity.ODD), odd!!.mask)

        val list = TimetableImporter.WeekPatterns.find("1,3,5,7周")
        assertNotNull(list)
        assertNotNull(list)
        assertEquals(listOf(1, 3, 5, 7), weeksOf(list!!.mask))

        val parityOnly = TimetableImporter.WeekPatterns.find("单周 高等数学")
        assertNotNull(parityOnly)
        assertEquals(WeekParity.ODD, Course.detectParity(weeksOf(parityOnly!!.mask)))

        val none = TimetableImporter.WeekPatterns.find("高等数学 张伟")
        assertEquals(null, none)
    }

    private fun weeksOf(mask: Int): List<Int> = (1..31).filter { (mask shr (it - 1)) and 1 == 1 }

    @Test
    fun `无法识别的表格应给出可读的失败提示`() {
        val sheet = SheetTable(
            "随意",
            listOf(
                listOf("甲", "乙", "丙"),
                listOf("1", "2", "3"),
                listOf("4", "5", "6")
            )
        )
        val outcome = TimetableImporter().analyze(Workbook(listOf(sheet)))
        assertTrue("不应识别为课表", !outcome.success)
        assertEquals(TimetableImporter.Strategy.UNKNOWN, outcome.strategy)
        assertTrue(outcome.warnings.isNotEmpty())
    }

    // ---------------- 真实教务导出格式 ----------------

    @Test
    fun `方括号周次与字母楼栋可被识别`() {
        val weeks = TimetableImporter.WeekPatterns.findAll("王鑫[4，5，7-18]周N楼-411")
        assertNotNull(weeks)
        requireNotNull(weeks)
        assertEquals(listOf(4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18), weeksOf(weeks.mask))
        assertTrue("剩余文本应保留教师名：${weeks.remaining}", weeks.remaining.contains("王鑫"))
        assertTrue("剩余文本应保留地点：${weeks.remaining}", weeks.remaining.contains("N楼-411"))
    }

    @Test
    fun `多段周次应合并为并集`() {
        val weeks = TimetableImporter.WeekPatterns.findAll("周文康[10-16]周，[4，5，7-9]周")
        assertNotNull(weeks)
        requireNotNull(weeks)
        assertEquals(
            listOf(4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            weeksOf(weeks.mask)
        )
    }

    @Test
    fun `真实教务矩阵课表应能完整识别`() {
        val sheet = SheetTable(
            "学生课表",
            listOf(
                listOf("2026秋季学期(2026211627)李跃龙课表", "", "", "", "", "", "", "", ""),
                listOf("", "", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"),
                listOf(
                    "上午", "1-2",
                    "通用英语B(2026版大一上)\n王鑫[4，5，7-18]周N楼-411", "",
                    "习近平新时代中国特色社会主义思想概论\n周文康[10-16]周，[4，5，7-9]周\nG楼-101",
                    "体育（1）(定向运动)\n张晓秋[1-5，7-17]周",
                    "习近平新时代中国特色社会主义思想概论\n周文康[10-16]周，[4，5，7-9]周\nG楼-101",
                    "通用英语B(2026版大一上)\n王鑫[15]周N楼-411", ""
                ),
                listOf(
                    "上午", "3-4", "", "",
                    "通用英语B(2026版大一上)\n王鑫[4，5，7-18]周N楼-411", "",
                    "代数与几何X\n王卫卫（0602019）[15-18]周M楼-102",
                    "通用英语B(2026版大一上)\n王鑫[16]周N楼-411", ""
                ),
                listOf(
                    "下午", "5-6", "",
                    "计算思维与人工智能\n吕晓倩[4，5，7-16]周M楼-201", "",
                    "计算思维与人工智能\n吕晓倩[4，5，7-16]周M楼-201", "", "", ""
                ),
                listOf(
                    "下午", "7-8",
                    "代数与几何X\n王卫卫（0602019）[4，5，7-18]周M楼-104",
                    "数学分析（1）\n于战华[18]周，[4，5，7-17]周\nM楼-405",
                    "代数与几何X\n王卫卫（0602019）[4，5，7-18]周M楼-104",
                    "数学分析（1）\n于战华[4，5，7-17]周M楼-405",
                    "数学分析（1）\n于战华[4，5，7-17]周M楼-405", "", ""
                ),
                listOf(
                    "晚上", "9-10", "", "",
                    "超声：无损检测前沿探索与实践\n赵扬[4，5，7-12]周H楼-429",
                    "职业生涯规划及就业指导\n王莹[10-17]周N楼-112", "", "", ""
                ),
                listOf("其它课程：四史专题◇网络◇3-16◇", "", "", "", "", "", "", "", "")
            )
        )
        val outcome = TimetableImporter().analyze(Workbook(listOf(sheet)))

        assertTrue("应识别成功：${outcome.warnings}", outcome.success)
        assertEquals(TimetableImporter.Strategy.MATRIX, outcome.strategy)

        val english = outcome.courses.first { it.name.startsWith("通用英语B") && it.dayOfWeek == 1 }
        assertEquals("王鑫", english.teacher)
        assertEquals("N楼-411", english.location)
        assertEquals(1, english.startSection)
        assertEquals(2, english.endSection)
        assertEquals(
            listOf(4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18),
            english.activeWeeks
        )

        val pe = outcome.courses.first { it.name.contains("体育") }
        assertEquals("张晓秋", pe.teacher)
        assertEquals(4, pe.dayOfWeek)
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17), pe.activeWeeks)

        val geometry = outcome.courses.first { it.name == "代数与几何X" && it.location == "M楼-102" }
        assertEquals("王卫卫", geometry.teacher)
        assertEquals(listOf(15, 16, 17, 18), geometry.activeWeeks)

        val ultrasonic = outcome.courses.first { it.name.contains("超声") }
        assertEquals(9, ultrasonic.startSection)
        assertEquals(10, ultrasonic.endSection)
        assertEquals("H楼-429", ultrasonic.location)

        val analysis = outcome.courses.first { it.name == "数学分析（1）" }
        assertEquals("于战华", analysis.teacher)
        assertEquals("M楼-405", analysis.location)

        // 同一门课出现在两个星期时，应生成两个时段
        assertTrue(
            "习近平新时代中国特色社会主义思想概论应有两个时段",
            outcome.courses.count { it.name.contains("习近平") } >= 2
        )
    }
}
