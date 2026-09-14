package com.lyl.timetable.xls

/**
 * HTML 表格解析。
 *
 * 国内大量教务系统的"导出 Excel"实际输出的是 HTML 表格（扩展名仍为 .xls），
 * 本解析器按标签流扫描，支持 colspan / rowspan 合并单元格的展开。
 */
internal object HtmlTableReader {

    fun parse(html: String): List<SparseSheet> {
        val sheets = ArrayList<SparseSheet>()
        var builder: TableBuilder? = null
        var tableDepth = 0
        var i = 0
        val n = html.length

        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0) {
                builder?.appendText(html.substring(i))
                break
            }
            if (lt > i) builder?.appendText(html.substring(i, lt))

            val gt = html.indexOf('>', lt + 1)
            if (gt < 0) break
            val rawTag = html.substring(lt + 1, gt).trim()
            i = gt + 1
            if (rawTag.isEmpty()) continue
            if (rawTag.startsWith("!--")) {
                // 跳过注释
                val end = html.indexOf("-->", lt)
                if (end >= 0) i = end + 3
                continue
            }

            val closing = rawTag.startsWith("/")
            val selfClose = rawTag.endsWith("/")
            val tagBody = rawTag.removePrefix("/").removeSuffix("/").trim()
            val spaceIdx = tagBody.indexOfFirst { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
            val tagName = (if (spaceIdx >= 0) tagBody.substring(0, spaceIdx) else tagBody).lowercase()
            val attrs = if (spaceIdx >= 0) tagBody.substring(spaceIdx) else ""

            when (tagName) {
                "table" -> {
                    if (!closing) {
                        tableDepth++
                        if (builder == null) builder = TableBuilder()
                    } else {
                        tableDepth--
                        if (tableDepth <= 0) {
                            builder?.let { b -> val t = b.build(); if (t != null) sheets.add(t) }
                            builder = null
                            tableDepth = 0
                        }
                    }
                }
                "tr" -> builder?.onRowEnd(closing)
                "td", "th" -> builder?.onCell(closing, attrs)
                "br" -> if (!closing) builder?.appendLineBreak()
                "p", "div" -> if (closing) builder?.appendText(" ")
            }
            if (selfClose && tagName in setOf("br", "td", "th")) {
                if (tagName != "br") builder?.onCell(true, attrs)
            }
        }
        builder?.let { b -> val t = b.build(); if (t != null) sheets.add(t) }
        return sheets
    }

    private class TableBuilder {
        private val cells = HashMap<Int, HashMap<Int, String>>()
        private val occupied = HashMap<Int, HashSet<Int>>()
        private var row = -1
        private var col = 0
        private var curStartCol = 0
        private var curColSpan = 1
        private var curRowSpan = 1
        private var inCell = false
        private val text = StringBuilder()

        /**
         * 行切换。
         *
         * 只在 `<tr>` 开始时递增行号：部分教务系统导出的 HTML 省略 `</tr>`，
         * 若两端都递增会导致行号跳变、rowspan 占位错乱。
         */
        fun onRowEnd(closing: Boolean) {
            if (inCell) finishCell()
            if (!closing) {
                row++
                col = 0
            }
        }

        fun onCell(closing: Boolean, attrs: String) {
            if (!closing) {
                if (inCell) finishCell()
                curStartCol = nextFreeCol(row, col)
                curColSpan = attrInt(attrs, "colspan")
                curRowSpan = attrInt(attrs, "rowspan")
                text.setLength(0)
                inCell = true
            } else {
                if (inCell) finishCell()
            }
        }

        private fun nextFreeCol(r: Int, from: Int): Int {
            var c = from
            val set = occupied[r]
            if (set != null) {
                while (set.contains(c)) c++
            }
            return c
        }

        private fun finishCell() {
            inCell = false
            val value = HtmlTableReader.unescapeHtml(text.toString())
                .replace('\u0000', ' ')
                .replace('\u00A0', ' ')
                .trim()
            if (row < 0) return
            val r0 = row
            val c0 = curStartCol
            for (r in r0 until r0 + curRowSpan.coerceIn(1, 500)) {
                for (c in c0 until c0 + curColSpan.coerceIn(1, 200)) {
                    if (value.isNotEmpty()) {
                        cells.getOrPut(r) { HashMap() }[c] = value
                    }
                    if (r != r0) occupied.getOrPut(r) { HashSet() }.add(c)
                }
            }
            col = c0 + curColSpan.coerceIn(1, 200)
        }

        fun appendText(s: String) {
            if (!inCell) return
            text.append(s)
        }

        fun appendLineBreak() {
            if (inCell && text.isNotEmpty() && text.last() != '\n') text.append('\n')
        }

        fun build(): SparseSheet? {
            if (inCell) finishCell()
            if (cells.isEmpty()) return null
            val sheet = SparseSheet("表格")
            for ((r, line) in cells) for ((c, v) in line) sheet.put(r, c, v)
            return sheet
        }

        private fun attrInt(attrs: String, name: String): Int {
            val idx = attrs.lowercase().indexOf(name)
            if (idx < 0) return 1
            val eq = attrs.indexOf('=', idx)
            if (eq < 0) return 1
            var p = eq + 1
            while (p < attrs.length && attrs[p].isWhitespace()) p++
            val quote = if (p < attrs.length && (attrs[p] == '"' || attrs[p] == '\'')) attrs[p] else null
            if (quote != null) p++
            val sb = StringBuilder()
            while (p < attrs.length && (quote == null && !attrs[p].isWhitespace() || quote != null && attrs[p] != quote)) {
                if (attrs[p].isDigit()) sb.append(attrs[p])
                p++
            }
            return sb.toString().toIntOrNull()?.coerceIn(1, 500) ?: 1
        }
    }

    /** 解析后立即展开 HTML 实体 */
    internal fun unescapeHtml(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val semi = s.indexOf(';', i)
            if (semi < 0 || semi - i > 12) {
                sb.append(c)
                i++
                continue
            }
            val entity = s.substring(i + 1, semi)
            val decoded = when {
                entity.startsWith("#x", true) -> entity.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                entity.equals("nbsp", true) -> " "
                entity.equals("amp", true) -> "&"
                entity.equals("lt", true) -> "<"
                entity.equals("gt", true) -> ">"
                entity.equals("quot", true) -> "\""
                entity.equals("apos", true) -> "'"
                entity.equals("ensp", true) || entity.equals("emsp", true) -> " "
                else -> null
            }
            if (decoded == null) {
                sb.append(c)
                i++
            } else {
                sb.append(decoded)
                i = semi + 1
            }
        }
        return sb.toString()
    }
}

/** 标签剥离工具：供 HTML 视图使用（导出器/预览也可复用） */
internal object HtmlText {
    fun stripTagsAndUnescape(raw: String): String {
        val noTag = raw.replace(Regex("<[^>]*>"), " ")
        return HtmlTableReader.unescapeHtml(noTag).replace('\u00A0', ' ').trim()
    }
}
