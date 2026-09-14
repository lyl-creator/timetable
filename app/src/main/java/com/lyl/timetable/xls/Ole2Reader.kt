package com.lyl.timetable.xls

import java.io.ByteArrayOutputStream

/**
 * OLE2 / CFB（Compound File Binary）容器解析，用于从 .xls 中取出 Workbook 数据流。
 *
 * 结构参考 MS-CFB：
 *   文件头 512 字节 → FAT（扇区分配表）→ 目录项 → 各数据流（按扇区链串接）。
 *   小于 miniStreamCutoff（通常 4096 字节）的数据流存放在 mini stream 中，使用 64 字节的 mini 扇区。
 */
internal object Ole2Reader {

    private const val HEADER_SIZE = 512
    private const val DIR_ENTRY_SIZE = 128
    private const val FREE_SECT = -1L          // 0xFFFFFFFF
    private const val END_OF_CHAIN = -2L       // 0xFFFFFFFE

    private fun toSect(v: Int): Long {
        val u = v.toLong() and 0xFFFFFFFFL
        return when (u) {
            0xFFFFFFFFL -> FREE_SECT
            0xFFFFFFFEL -> END_OF_CHAIN
            else -> u
        }
    }

    /** 取出 Workbook（或旧版 Book）数据流内容 */
    fun readWorkbookStream(data: ByteArray): ByteArray {
        require(data.size >= HEADER_SIZE) { "文件长度不足，不是有效的 OLE2 文档" }
        if (!SpreadsheetParser.matches(data, 0, byteArrayOf(
                0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
                0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
            ))
        ) {
            error("OLE2 文件头标记不正确")
        }

        val sectorShift = data.u16le(0x1E)
        val miniSectorShift = data.u16le(0x20)
        if (sectorShift < 7 || sectorShift > 14) error("异常扇区大小（shift=$sectorShift）")
        val sectorSize = 1 shl sectorShift
        val miniSectorSize = 1 shl miniSectorShift.coerceIn(2, 12)
        val firstDirSector = data.u32le(0x30)
        val miniCutoff = data.u32le(0x38).let { if (it <= 0) 4096L else it.toLong() }
        val firstMiniFatSector = data.u32le(0x3C)
        val firstDifatSector = data.u32le(0x44)

        // ---- 1. 汇总 FAT 扇区号（前 109 项在文件头，其余沿 DIFAT 链） ----
        val fatSectors = ArrayList<Int>(128)
        for (i in 0 until 109) {
            val s = toSect(data.u32le(0x4C + i * 4))
            if (s == FREE_SECT || s == END_OF_CHAIN) break
            fatSectors.add(s.toInt())
        }
        var difat = toSect(firstDifatSector)
        var guard = 0
        val perDifat = sectorSize / 4 - 1
        while (difat >= 0 && guard++ < 8192) {
            val base = sectorOffset(difat, sectorSize) ?: break
            if (base + sectorSize > data.size) break
            for (i in 0 until perDifat) {
                val s = toSect(data.u32le(base + i * 4))
                if (s == FREE_SECT || s == END_OF_CHAIN) continue
                fatSectors.add(s.toInt())
            }
            difat = toSect(data.u32le(base + perDifat * 4))
        }
        require(fatSectors.isNotEmpty()) { "未找到 FAT 分配表" }

        // ---- 2. 载入 FAT 表内容 ----
        val fat = IntArray(fatSectors.size * (sectorSize / 4))
        var fatIdx = 0
        for (fs in fatSectors) {
            val base = sectorOffset(fs.toLong(), sectorSize) ?: continue
            if (base + sectorSize > data.size) continue
            for (i in 0 until sectorSize / 4) fat[fatIdx++] = data.u32le(base + i * 4)
        }

        // ---- 3. 读取目录流，定位 Workbook / Book 与根条目 ----
        val dirBytes = readChain(data, fat, toSect(firstDirSector), sectorSize, -1L)
        if (dirBytes.size < DIR_ENTRY_SIZE) error("目录流为空")

        var wbStart = -1L
        var wbSize = -1L
        var rootStart = -1L
        var found = false

        val entryCount = dirBytes.size / DIR_ENTRY_SIZE
        for (i in 0 until entryCount) {
            val off = i * DIR_ENTRY_SIZE
            val type = dirBytes[off + 0x42].toInt() and 0xFF
            if (type != 1 && type != 2 && type != 5) continue

            val nameLenBytes = dirBytes.u16le(off + 0x40)
            val charCount = ((nameLenBytes - 2).coerceAtLeast(0)) / 2
            val name = if (charCount in 1..31) {
                String(dirBytes, off, charCount * 2, Charsets.UTF_16LE)
            } else ""

            val start = toSect(dirBytes.u32le(off + 0x74))
            var size = dirBytes.u64le(off + 0x78)
            // 部分生成器把流长度只写在低 4 字节，高 4 字节残留脏数据
            if (size <= 0L || size > data.size.toLong()) {
                val low = dirBytes.u32le(off + 0x78).toLong() and 0xFFFFFFFFL
                if (low in 1..data.size.toLong()) size = low
            }

            when {
                type == 5 -> { rootStart = start }
                type == 2 && (name.equals("Workbook", true) || name.equals("Book", true)) -> {
                    wbStart = start
                    wbSize = size
                    found = true
                }
            }
        }
        if (!found || wbStart < 0) error("未找到 Workbook 数据流（可能不是 Excel 文件）")

        // ---- 4. 依据大小选择主 FAT 或 mini FAT 读取 ----
        val useMini = wbSize in 1 until miniCutoff
        if (!useMini) {
            return readChain(data, fat, wbStart, sectorSize, wbSize)
        }

        val miniFatBytes = readChain(data, fat, toSect(firstMiniFatSector), sectorSize, -1L)
        if (miniFatBytes.size < 4) {
            // mini FAT 缺失时退化为按主 FAT 读取
            return readChain(data, fat, wbStart, sectorSize, wbSize)
        }
        val miniFat = IntArray(miniFatBytes.size / 4) { miniFatBytes.u32le(it * 4) }
        val miniStream = readChain(data, fat, rootStart, sectorSize, -1L)
        return readChain(miniStream, miniFat, wbStart, miniSectorSize, wbSize)
    }

    private fun sectorOffset(sector: Long, sectorSize: Int): Int? {
        val off = HEADER_SIZE.toLong() + sector * sectorSize
        if (off < 0 || off + sectorSize > Int.MAX_VALUE) return null
        return off.toInt()
    }

    /**
     * 沿扇区链读取数据。
     * @param size 期望长度，<=0 表示按链长读取
     */
    private fun readChain(data: ByteArray, fat: IntArray, start: Long, sectorSize: Int, size: Long): ByteArray {
        if (start < 0) return ByteArray(0)
        val out = ByteArrayOutputStream(if (size in 1..Int.MAX_VALUE.toLong()) size.toInt() else 8192)
        var sector = start
        var steps = 0
        val maxSteps = data.size / sectorSize + 16
        while (sector >= 0 && steps++ <= maxSteps) {
            val base = sectorOffset(sector, sectorSize) ?: break
            if (base + sectorSize > data.size) break
            out.write(data, base, sectorSize)
            val si = sector.toInt()
            sector = if (si >= 0 && si < fat.size) toSect(fat[si]) else END_OF_CHAIN
        }
        val bytes = out.toByteArray()
        return if (size in 1..bytes.size.toLong()) bytes.copyOf(size.toInt()) else bytes
    }
}
