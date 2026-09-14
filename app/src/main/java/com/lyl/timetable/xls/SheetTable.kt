package com.lyl.timetable.xls

/**
 * 解析结果中的一张工作表。
 *
 * rows 为矩形文本矩阵：每行长度相同，均等于 columnCount。
 * 所有单元格值均已去除首尾空白，数值型单元格已做归一化（整数不带 .0 后缀）。
 */
data class SheetTable(
    val name: String,
    val rows: List<List<String>>
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    fun cell(row: Int, col: Int): String =
        rows.getOrNull(row)?.getOrNull(col)?.trim().orEmpty()

    /** 该行是否所有单元格为空 */
    fun isBlankRow(row: Int): Boolean = rows.getOrNull(row)?.all { it.isBlank() } ?: true

    override fun toString(): String = "SheetTable($name, ${rowCount}x$columnCount)"
}

/** 整个表格文件（可能含多张工作表） */
data class Workbook(val sheets: List<SheetTable>) {
    val first: SheetTable? get() = sheets.firstOrNull { it.rowCount > 0 }
    override fun toString(): String = "Workbook(${sheets.size} sheets)"
}

/** 稀疏工作表构建器：允许乱序写入，最后补齐为矩形 */
internal class SparseSheet(private val name: String) {
    private val cells = HashMap<Int, HashMap<Int, String>>()
    private var maxRow = -1
    private var maxCol = -1

    fun put(row: Int, col: Int, value: String) {
        if (row < 0 || col < 0 || col > 16383) return
        val v = value.trim()
        if (v.isEmpty()) return
        cells.getOrPut(row) { HashMap() }[col] = v
        if (row > maxRow) maxRow = row
        if (col > maxCol) maxCol = col
    }

    fun isEmpty(): Boolean = maxRow < 0 || maxCol < 0

    fun toTable(): SheetTable {
        if (isEmpty()) return SheetTable(name, emptyList())
        val rows = ArrayList<List<String>>(maxRow + 1)
        for (r in 0..maxRow) {
            val line = cells[r]
            if (line == null) {
                rows.add(List(maxCol + 1) { "" })
            } else {
                rows.add(List(maxCol + 1) { c -> line[c] ?: "" })
            }
        }
        return SheetTable(name, trimTrailingBlankRows(rows))
    }

    companion object {
        fun trimTrailingBlankRows(rows: List<List<String>>): List<List<String>> {
            var end = rows.size
            while (end > 0 && rows[end - 1].all { it.isBlank() }) end--
            return if (end == rows.size) rows else rows.subList(0, end).toList()
        }
    }
}

/** 数值归一化：去尾零、整数去掉 .0 */
internal object NumberFmt {
    fun format(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return ""
        if (d == Math.floor(d) && Math.abs(d) < 1e15) {
            return d.toLong().toString()
        }
        val s = d.toString()
        return if (s.contains('.') && !s.contains('E') && !s.contains('e')) {
            s.trimEnd('0').trimEnd('.')
        } else {
            s
        }
    }
}
