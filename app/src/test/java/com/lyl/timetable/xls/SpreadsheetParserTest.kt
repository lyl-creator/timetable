package com.lyl.timetable.xls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器正确性验证。样本文件由脚本生成（xlwt 生成真实 BIFF8 二进制，openpyxl 生成 OOXML）。
 */
class SpreadsheetParserTest {

    private fun load(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("samples/$name")?.readBytes()
            ?: error("缺少测试样本：$name")

    private fun success(bytes: ByteArray, fileName: String): SpreadsheetParser.Result.Success {
        val result = SpreadsheetParser.parseBytes(bytes, fileName)
        assertTrue("解析应成功，实际为 $result", result is SpreadsheetParser.Result.Success)
        return result as SpreadsheetParser.Result.Success
    }

    @Test
    fun `xls 列表式课表应完整解析表头与数据行`() {
        val parsed = success(load("list_style.xls"), "list_style.xls")
        assertEquals(SpreadsheetParser.Format.XLS, parsed.format)

        val sheet = parsed.workbook.sheets.first()
        assertEquals("课表", sheet.name)
        assertEquals(11, sheet.rowCount)      // 1 行表头 + 10 行数据
        assertEquals(6, sheet.columnCount)

        assertEquals("课程名称", sheet.cell(0, 0))
        assertEquals("上课周次", sheet.cell(0, 2))
        assertEquals("高等数学A", sheet.cell(1, 0))
        assertEquals("张伟", sheet.cell(1, 1))
        assertEquals("1-16周", sheet.cell(1, 2))
        assertEquals("教三301", sheet.cell(1, 5))
        assertEquals("形势与政策", sheet.cell(10, 0))
    }

    @Test
    fun `xls 矩阵式课表应保留单元格内的多行文本`() {
        val parsed = success(load("matrix_style.xls"), "matrix_style.xls")
        val sheet = parsed.workbook.sheets.first()
        assertEquals("我的课表", sheet.name)
        assertEquals(9, sheet.rowCount)
        assertEquals(6, sheet.columnCount)

        assertEquals("节次", sheet.cell(0, 0))
        assertEquals("星期一", sheet.cell(0, 1))

        val firstCell = sheet.cell(1, 1)
        assertTrue("单元格应保留换行结构：$firstCell", firstCell.contains("\n"))
        assertTrue(firstCell.contains("高等数学"))
        assertTrue(firstCell.contains("张伟"))
        assertTrue(firstCell.contains("教三301"))
    }

    @Test
    fun `xls 紧凑写法应保留原始复合文本`() {
        val parsed = success(load("compact_style.xls"), "compact_style.xls")
        val sheet = parsed.workbook.sheets.first()
        val cell = sheet.cell(1, 1)
        assertTrue(cell.contains("高等数学"))
        assertTrue(cell.contains("1-16周"))
        assertTrue(cell.contains("张伟"))
    }

    @Test
    fun `HTML 伪 xls 应展开合并单元格`() {
        val parsed = success(load("html_style.xls"), "html_style.xls")
        assertEquals(SpreadsheetParser.Format.HTML, parsed.format)

        val sheet = parsed.workbook.sheets.first()
        assertTrue("应至少解析出表头与数据行", sheet.rowCount >= 5)

        // 第一行是 colspan=7 的标题
        assertTrue(sheet.cell(0, 0).contains("课程表"))
        // 星期表头行
        val headerRow = (0 until sheet.rowCount).first { sheet.cell(it, 1).contains("星期一") }
        assertEquals("节次", sheet.cell(headerRow, 0))

        // rowspan=2 的单元格应在两行内都可见
        val joined = (0 until sheet.rowCount).joinToString("|") { r -> sheet.cell(r, 1) }
        assertTrue("合并单元格内容应被展开：$joined", joined.contains("高等数学"))

        // HTML 实体应被还原
        val allText = (0 until sheet.rowCount).joinToString("|") { r ->
            (0 until sheet.columnCount).joinToString(",") { c -> sheet.cell(r, c) }
        }
        assertTrue("不应残留 &nbsp; 实体", !allText.contains("&nbsp"))
    }

    @Test
    fun `xlsx 应解析共享字符串与多工作表`() {
        val parsed = success(load("modern_style.xlsx"), "modern_style.xlsx")
        assertEquals(SpreadsheetParser.Format.XLSX, parsed.format)
        assertEquals(2, parsed.workbook.sheets.size)

        val listSheet = parsed.workbook.sheets[0]
        assertEquals("课程清单", listSheet.name)
        assertEquals("课程名称", listSheet.cell(0, 0))
        assertEquals("高等数学A", listSheet.cell(1, 0))
        assertEquals("教三301", listSheet.cell(1, 5))

        val matrixSheet = parsed.workbook.sheets[1]
        assertEquals("周课表", matrixSheet.name)
        assertEquals("星期一", matrixSheet.cell(0, 1))
        assertTrue(matrixSheet.cell(1, 1).contains("高等数学"))
    }

    @Test
    fun `大共享字符串表应正确跨越 CONTINUE 记录`() {
        val parsed = success(load("big_sst.xls"), "big_sst.xls")
        val sheet = parsed.workbook.sheets.first()
        assertEquals(261, sheet.rowCount)

        assertEquals("课程名称", sheet.cell(0, 0))
        // 抽样校验中间与末尾行，确认 SST 未错位
        for (index in listOf(1, 50, 137, 260)) {
            val expected = "专业选修课程名称编号%03d".format(index - 1)
            assertTrue(
                "第 $index 行课程名应为 $expected 开头，实际 ${sheet.cell(index, 0)}",
                sheet.cell(index, 0).startsWith(expected)
            )
            assertEquals(
                "教学楼第%d区%d室".format((index - 1) % 9 + 1, ((index - 1) * 7) % 500),
                sheet.cell(index, 4)
            )
        }
    }

    @Test
    fun `GBK 编码的 CSV 应正确解码中文`() {
        val parsed = success(load("gbk_style.csv"), "gbk_style.csv")
        val sheet = parsed.workbook.sheets.first()
        assertEquals(3, sheet.rowCount)      // 1 行表头 + 2 行数据
        assertEquals("课程名称", sheet.cell(0, 0))
        assertEquals("高等数学", sheet.cell(1, 0))
        assertEquals("教三301", sheet.cell(1, 4))
        assertEquals("文科楼A101", sheet.cell(2, 4))
    }

    @Test
    fun `空文件应返回失败而不是崩溃`() {
        val result = SpreadsheetParser.parseBytes(ByteArray(0), "empty.xls")
        assertTrue(result is SpreadsheetParser.Result.Failure)
        assertNotNull((result as SpreadsheetParser.Result.Failure).message)
    }

    @Test
    fun `损坏的 xls 应返回失败而不是崩溃`() {
        // 具备合法的 OLE2 文件头，但内部结构完全无效
        val broken = ByteArray(4096) { 0x41 }
        val magic = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
        )
        magic.copyInto(broken, 0)
        val result = SpreadsheetParser.parseBytes(broken, "broken.xls")
        assertTrue(
            "损坏文件应返回失败，实际为 $result",
            result is SpreadsheetParser.Result.Failure
        )
    }

    @Test
    fun `RK 数值解码应覆盖整数与缩放两种编码`() {
        // 整数型 RK：值 = rk >> 2
        assertEquals(42.0, Biff8Reader.decodeRk(42 shl 2 or 0x02), 1e-9)
        // 整数并除以 100
        assertEquals(4.2, Biff8Reader.decodeRk(420 shl 2 or 0x03), 1e-9)
    }
}
