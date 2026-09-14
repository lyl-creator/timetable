package com.lyl.timetable.xls

/**
 * 定界文本解析（CSV / TSV / 分号分隔），遵循 RFC 4180 的引号转义规则，
 * 并按首部若干行自动推断分隔符。
 */
internal object DelimitedTextReader {

    fun parse(text: String, fileName: String = ""): List<SheetTable> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(lines)
        val rows = ArrayList<List<String>>(lines.size)
        val pending = StringBuilder()
        var inQuotes = false
        val current = ArrayList<String>()
        var field = StringBuilder()

        fun flushField() {
            current.add(HtmlTableReader.unescapeHtml(field.toString()).trim())
            field = StringBuilder()
        }

        fun flushRow() {
            flushField()
            rows.add(current.toList())
            current.clear()
        }

        for (raw in lines) {
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (inQuotes) {
                    if (ch == '"') {
                        if (i + 1 < raw.length && raw[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(ch)
                    }
                } else {
                    when (ch) {
                        '"' -> inQuotes = true
                        delimiter -> flushField()
                        else -> field.append(ch)
                    }
                }
                i++
            }
            pending.setLength(0)
            flushRow()
        }

        if (rows.isEmpty()) return emptyList()
        val cols = rows.maxOf { it.size }
        if (cols == 0) return emptyList()
        val padded = rows.map { r -> if (r.size == cols) r else r + List(cols - r.size) { "" } }
        val name = fileName.substringBeforeLast('.').ifBlank { "文本数据" }
        return listOf(SheetTable(name, SparseSheet.trimTrailingBlankRows(padded)))
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(20).filter { it.isNotBlank() }
        if (sample.isEmpty()) return ','
        val candidates = listOf('\t', ',', ';', '|')
        var best = ','
        var bestScore = -1
        for (c in candidates) {
            val counts = sample.map { line -> line.count { it == c } }
            val nonZero = counts.count { it > 0 }
            if (nonZero == 0) continue
            // 稳定性优先：出现次数的一致性越高越可能是真正的分隔符
            val avg = counts.sum().toDouble() / counts.size
            val variance = counts.map { (it - avg) * (it - avg) }.sum() / counts.size
            val score = nonZero * 100 - variance.toInt() - (if (c == '\t') 0 else 1)
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }
}
