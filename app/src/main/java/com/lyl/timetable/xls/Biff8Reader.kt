package com.lyl.timetable.xls

/**
 * BIFF8 记录流解析（.xls 的核心数据层）。
 *
 * 记录结构：type(u16) + length(u16) + payload。
 * 关键记录：BOF / BOUNDSHEET / SST(+CONTINUE) / LABELSST / LABEL / RSTRING /
 *          NUMBER / RK / MULRK / FORMULA(+STRING) / BOOLERR / BLANK / MULBLANK / EOF
 */
internal object Biff8Reader {

    // ---- 记录类型 ----
    private const val REC_FORMULA = 0x0006
    private const val REC_EOF = 0x000A
    private const val REC_BLANK = 0x0201
    private const val REC_NUMBER = 0x0203
    private const val REC_LABEL = 0x0204
    private const val REC_BOOLERR = 0x0205
    private const val REC_STRING = 0x0207
    private const val REC_RSTRING = 0x00D6
    private const val REC_BOUNDSHEET = 0x0085
    private const val REC_MULRK = 0x00BD
    private const val REC_MULBLANK = 0x00BE
    private const val REC_RK = 0x027E
    private const val REC_SST = 0x00FC
    private const val REC_LABELSST = 0x00FD
    private const val REC_CONTINUE = 0x003C
    private const val REC_BOF = 0x0809

    private data class SheetRef(val name: String, val offset: Int)

    fun parse(stream: ByteArray): Workbook {
        val sheets = ArrayList<SheetRef>()
        var sst: List<String> = emptyList()

        // ---------- 第一遍：读取全局记录（工作表清单 + 共享字符串表） ----------
        var pos = 0
        while (pos + 4 <= stream.size) {
            val type = stream.u16le(pos)
            val len = stream.u16le(pos + 2)
            val dataStart = pos + 4
            if (dataStart + len > stream.size) break

            when (type) {
                REC_BOUNDSHEET -> readBoundsheet(stream, dataStart, len)?.let { sheets.add(it) }

                REC_SST -> {
                    val chunks = ArrayList<ByteArray>(8)
                    chunks.add(stream.copyOfRange(dataStart, dataStart + len))
                    var p = dataStart + len
                    while (p + 4 <= stream.size && stream.u16le(p) == REC_CONTINUE) {
                        val cl = stream.u16le(p + 2)
                        if (p + 4 + cl > stream.size) break
                        chunks.add(stream.copyOfRange(p + 4, p + 4 + cl))
                        p += 4 + cl
                    }
                    sst = readSst(chunks)
                    pos = p
                    continue
                }

                REC_EOF -> break // 全局区结束，其后为各工作表子流
            }
            pos = dataStart + len
        }

        // ---------- 第二遍：逐工作表解析单元格 ----------
        val result = ArrayList<SheetTable>(sheets.size)
        for (ref in sheets) {
            val sheet = readSheet(stream, ref, sst)
            result.add(sheet.toTable())
        }
        if (result.isEmpty()) {
            // 极少数文件缺少 BOUNDSHEET，退化为把整个流当作一张表解析
            val sheet = SparseSheet("Sheet1")
            scanCells(stream, 0, sheet, sst)
            if (!sheet.isEmpty()) result.add(sheet.toTable())
        }
        return Workbook(result)
    }

    private fun readBoundsheet(stream: ByteArray, off: Int, len: Int): SheetRef? {
        if (len < 8) return null
        val position = stream.u32le(off)
        val cch = stream[off + 6].toInt() and 0xFF
        val grbit = stream[off + 7].toInt() and 0xFF
        val wide = (grbit and 0x01) != 0
        val nameBytes = if (wide) cch * 2 else cch
        if (8 + nameBytes > len) return null
        val name = if (wide) {
            String(stream, off + 8, cch * 2, Charsets.UTF_16LE)
        } else {
            String(stream, off + 8, cch, Charsets.ISO_8859_1)
        }
        return SheetRef(name.ifBlank { "Sheet" }, position)
    }

    private fun readSheet(stream: ByteArray, ref: SheetRef, sst: List<String>): SparseSheet {
        val sheet = SparseSheet(ref.name)
        var start = ref.offset
        if (start <= 0 || start + 4 > stream.size || stream.u16le(start) != REC_BOF) {
            // BOUNDSHEET 位置不可靠时，退化为全流扫描
            scanCells(stream, 0, sheet, sst)
            return sheet
        }
        scanCells(stream, start, sheet, sst)
        return sheet
    }

    /** 从 start 处开始按记录遍历，收集单元格 */
    private fun scanCells(stream: ByteArray, start: Int, sheet: SparseSheet, sst: List<String>) {
        var pos = start
        var pendingStringRow = -1
        var pendingStringCol = -1
        var first = true

        while (pos + 4 <= stream.size) {
            val type = stream.u16le(pos)
            val len = stream.u16le(pos + 2)
            val d = pos + 4
            if (d + len > stream.size) break
            if (!first && type == REC_EOF) break
            first = false

            when (type) {
                REC_LABELSST -> {
                    if (len >= 10) {
                        val row = stream.u16le(d)
                        val col = stream.u16le(d + 2)
                        val idx = stream.u32le(d + 6)
                        if (idx >= 0 && idx < sst.size) sheet.put(row, col, sst[idx])
                    }
                }

                REC_LABEL, REC_RSTRING -> {
                    if (len >= 8) {
                        val row = stream.u16le(d)
                        val col = stream.u16le(d + 2)
                        val text = readUnicodeString(stream, d + 6, d + len)
                        if (text.isNotEmpty()) sheet.put(row, col, text)
                    }
                }

                REC_NUMBER -> {
                    if (len >= 14) {
                        val row = stream.u16le(d)
                        val col = stream.u16le(d + 2)
                        sheet.put(row, col, NumberFmt.format(doubleLe(stream, d + 6)))
                    }
                }

                REC_RK -> {
                    if (len >= 10) {
                        val row = stream.u16le(d)
                        val col = stream.u16le(d + 2)
                        sheet.put(row, col, NumberFmt.format(decodeRk(stream.u32le(d + 6))))
                    }
                }

                REC_MULRK -> {
                    if (len >= 6) {
                        val row = stream.u16le(d)
                        val count = (len - 6) / 6
                        for (k in 0 until count) {
                            val col = stream.u16le(d + 2) + k
                            val rkOff = d + 4 + k * 6 + 2
                            if (rkOff + 4 > d + len) break
                            sheet.put(row, col, NumberFmt.format(decodeRk(stream.u32le(rkOff))))
                        }
                    }
                }

                REC_FORMULA -> {
                    if (len >= 14) {
                        val row = stream.u16le(d)
                        val col = stream.u16le(d + 2)
                        val b6 = stream[d + 12].toInt() and 0xFF
                        val b7 = stream[d + 13].toInt() and 0xFF
                        if (b6 == 0xFF && b7 == 0xFF) {
                            pendingStringRow = row
                            pendingStringCol = col
                        } else if (b6 in 0..3 && b7 == 0xFF) {
                            // 布尔 / 错误 / 空串缓存值，课程表场景无需展示
                            if (b6 == 1) {
                                val boolVal = if ((stream[d + 8].toInt() and 0xFF) != 0) "TRUE" else "FALSE"
                                sheet.put(row, col, boolVal)
                            }
                        } else {
                            val v = doubleLe(stream, d + 6)
                            if (!v.isNaN() && !v.isInfinite()) {
                                sheet.put(row, col, NumberFmt.format(v))
                            }
                        }
                    }
                }

                REC_STRING -> {
                    if (pendingStringRow >= 0 && len >= 3) {
                        val text = readUnicodeString(stream, d, d + len)
                        if (text.isNotEmpty()) sheet.put(pendingStringRow, pendingStringCol, text)
                    }
                    pendingStringRow = -1
                    pendingStringCol = -1
                }

                REC_BOOLERR -> {
                    if (len >= 8) {
                        val isError = (stream[d + 7].toInt() and 0xFF) != 0
                        if (!isError) {
                            val row = stream.u16le(d)
                            val col = stream.u16le(d + 2)
                            val v = stream[d + 6].toInt() and 0xFF
                            sheet.put(row, col, if (v != 0) "TRUE" else "FALSE")
                        }
                    }
                }

                REC_MULBLANK, REC_BLANK -> { /* 显式空单元格，无需处理 */ }
            }
            pos = d + len
        }
    }

    /** 读取记录内嵌的 XLUnicodeString（LABEL / RSTRING / STRING 共用） */
    private fun readUnicodeString(stream: ByteArray, off: Int, recordEnd: Int): String {
        if (off + 3 > recordEnd) return ""
        val cch = stream.u16le(off)
        val grbit = stream[off + 2].toInt() and 0xFF
        var p = off + 3
        val wide = (grbit and 0x01) != 0
        val rich = (grbit and 0x08) != 0
        if (rich) p += 2
        val phonetic = (grbit and 0x04) != 0
        if (phonetic) p += 4
        val available = recordEnd - p
        val need = if (wide) cch * 2 else cch
        if (need <= 0 || need > available) {
            // 截断读取，尽量取到可用部分
            val usable = if (wide) available / 2 else available
            return if (usable <= 0) "" else decode(stream, p, usable, wide)
        }
        return decode(stream, p, cch, wide)
    }

    private fun decode(src: ByteArray, off: Int, count: Int, wide: Boolean): String {
        val sb = StringBuilder(count)
        if (wide) {
            for (i in 0 until count) {
                val lo = src[off + i * 2].toInt() and 0xFF
                val hi = src[off + i * 2 + 1].toInt() and 0xFF
                sb.append(((hi shl 8) or lo).toChar())
            }
        } else {
            for (i in 0 until count) sb.append((src[off + i].toInt() and 0xFF).toChar())
        }
        return sb.toString().trim()
    }

    // ---------------- SST 解析（支持跨 CONTINUE 的字符串） ----------------

    private fun readSst(chunks: List<ByteArray>): List<String> {
        val cursor = ChunkCursor(chunks)
        if (cursor.remainingTotal() < 8) return emptyList()
        cursor.skip(4)                       // cstTotal：含重复项的总数
        val unique = cursor.readU32Value()   // cstUnique
        if (unique <= 0 || unique > 500_000) return emptyList()

        val out = ArrayList<String>(minOf(unique, 65536))
        for (i in 0 until unique) {
            if (!cursor.ensure()) break
            val s = cursor.readString() ?: break
            out.add(s)
        }
        return out
    }

    /**
     * 支持跨 CONTINUE 边界的游标。
     *
     * MS-XLS 规定：当一个 Unicode 字符串的字符数据被拆分到 CONTINUE 记录时，
     * CONTINUE 载荷的第 1 个字节是新片段的编码标志（8 位或 16 位），不含在字符计数内。
     */
    private class ChunkCursor(private val chunks: List<ByteArray>) {
        private var ci = 0
        private var off = 0

        fun ensure(): Boolean {
            while (ci < chunks.size && off >= chunks[ci].size) {
                ci++
                off = 0
            }
            return ci < chunks.size
        }

        fun remainingTotal(): Int {
            var total = 0
            for (i in ci until chunks.size) {
                total += if (i == ci) (chunks[i].size - off).coerceAtLeast(0) else chunks[i].size
            }
            return total
        }

        private fun atBoundary(): Boolean {
            val cur = chunks.getOrNull(ci) ?: return true
            return off >= cur.size
        }

        fun skip(n: Int) {
            var left = n
            while (left > 0 && ensure()) {
                val avail = chunks[ci].size - off
                val take = minOf(left, avail)
                off += take
                left -= take
            }
        }

        private fun readByte(): Int {
            if (!ensure()) return -1
            return chunks[ci][off++].toInt() and 0xFF
        }

        private fun readU16(): Int {
            val a = readByte()
            val b = readByte()
            if (a < 0 || b < 0) return -1
            return a or (b shl 8)
        }

        fun readU32Value(): Int {
            val a = readByte(); val b = readByte(); val c = readByte(); val d = readByte()
            if (a < 0 || b < 0 || c < 0 || d < 0) return -1
            return a or (b shl 8) or (c shl 16) or (d shl 24)
        }

        fun readString(): String? {
            if (!ensure()) return null
            val cch = readU16()
            if (cch < 0) return null
            val grbit = readByte()
            if (grbit < 0) return null

            var wide = (grbit and 0x01) != 0
            val rich = (grbit and 0x08) != 0
            val phonetic = (grbit and 0x04) != 0
            var cRun = 0
            var cbExt = 0
            if (rich) {
                cRun = readU16()
                if (cRun < 0) return null
            }
            if (phonetic) {
                cbExt = readU32Value()
                if (cbExt < 0) return null
            }

            val sb = StringBuilder(cch)
            var i = 0
            while (i < cch) {
                // 字符数据恰好用尽当前片段时，续片段的第 1 字节是新的编码标志
                if (atBoundary()) {
                    if (!ensure()) break
                    val flag = readByte()
                    if (flag < 0) break
                    wide = (flag and 0x01) != 0
                }
                if (wide) {
                    val lo = readByte()
                    val hi = readByte()
                    if (lo < 0 || hi < 0) break
                    sb.append(((hi shl 8) or lo).toChar())
                } else {
                    val b = readByte()
                    if (b < 0) break
                    sb.append(b.toChar())
                }
                i++
            }

            if (rich && cRun > 0) skip(cRun * 4)
            if (phonetic && cbExt > 0) skip(cbExt)
            return sb.toString()
        }
    }

    // ---------------- 数值解码 ----------------

    /** RK 值：低 2 位为标志（是否 100 倍缩放 / 是否为整数） */
    internal fun decodeRk(rkRaw: Int): Double {
        val isInt = (rkRaw and 0x02) != 0
        val isScaled = (rkRaw and 0x01) != 0
        var v: Double = if (isInt) {
            (rkRaw shr 2).toDouble()
        } else {
            val bits = (rkRaw.toLong() and 0xFFFFFFFCL) shl 32
            java.lang.Double.longBitsToDouble(bits)
        }
        if (isScaled) v /= 100.0
        return v
    }

    private fun doubleLe(src: ByteArray, off: Int): Double {
        var bits = 0L
        for (i in 7 downTo 0) bits = (bits shl 8) or (src[off + i].toLong() and 0xFF)
        return java.lang.Double.longBitsToDouble(bits)
    }
}
