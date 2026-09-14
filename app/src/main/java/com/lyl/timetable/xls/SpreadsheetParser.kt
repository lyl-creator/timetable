package com.lyl.timetable.xls

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * 表格文件解析入口。
 *
 * 支持格式（按字节特征自动识别，与文件扩展名无关）：
 *  1. .xls  —— 旧版 Excel 二进制格式（OLE2 复合文档 + BIFF8 记录流）
 *  2. .xlsx —— OOXML（ZIP + XML）
 *  3. 伪 .xls —— 部分教务系统以 HTML 表格冒充 xls 导出
 *  4. .csv / .tsv / .txt —— 定界文本（自动识别 UTF-8 / GBK / UTF-16）
 *
 * 本类及其依赖全部为纯 JVM 实现，不引用任何 android.* 类型，可直接单元测试。
 */
object SpreadsheetParser {

    private const val MAX_BYTES = 64 * 1024 * 1024

    sealed class Result {
        data class Success(val workbook: Workbook, val format: Format) : Result()
        data class Failure(val message: String) : Result()
    }

    enum class Format(val label: String) {
        XLS("Excel 97-2003 (.xls)"),
        XLSX("Excel 工作簿 (.xlsx)"),
        HTML("网页表格伪 xls"),
        DELIMITED("定界文本 (csv/tsv)")
    }

    fun parse(file: File): Result = try {
        parseBytes(file.readBytes(), file.name)
    } catch (t: Throwable) {
        Result.Failure("读取文件失败：${t.message ?: t.javaClass.simpleName}")
    }

    fun parse(stream: InputStream, fileName: String? = null): Result = try {
        parseBytes(stream.readBytes(), fileName)
    } catch (t: Throwable) {
        Result.Failure("读取数据失败：${t.message ?: t.javaClass.simpleName}")
    }

    fun parseBytes(bytes: ByteArray, fileName: String? = null): Result {
        if (bytes.isEmpty()) return Result.Failure("文件内容为空")
        if (bytes.size > MAX_BYTES) return Result.Failure("文件过大（超过 64 MB）")

        // 1) OLE2 复合文档 —— .xls
        if (bytes.size >= 8 && matches(bytes, 0, OLE2_MAGIC)) {
            return runCatching { Ole2Reader.readWorkbookStream(bytes) }
                .fold(
                    onSuccess = { stream ->
                        val wb = Biff8Reader.parse(stream)
                        if (wb.sheets.isEmpty()) Result.Failure("未在 .xls 中找到任何工作表")
                        else Result.Success(wb, Format.XLS)
                    },
                    onFailure = { Result.Failure("解析 .xls 失败：${it.message ?: it.javaClass.simpleName}") }
                )
        }

        // 2) ZIP 容器 —— .xlsx / .xlsm
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return runCatching { XlsxReader.parse(bytes) }.fold(
                onSuccess = { wb ->
                    if (wb.sheets.isEmpty()) Result.Failure("未在 .xlsx 中找到任何工作表")
                    else Result.Success(wb, Format.XLSX)
                },
                onFailure = { Result.Failure("解析 .xlsx 失败：${it.message ?: it.javaClass.simpleName}") }
            )
        }

        // 3) HTML 伪 xls —— 教务系统常见
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1).lowercase()
        val looksHtml = head.contains("<html") || head.contains("<table") ||
                head.contains("<!doctype") || head.contains("<meta") && head.contains("charset")
        if (looksHtml) {
            val decoded = TextDecode.decode(bytes)
            return runCatching { HtmlTableReader.parse(decoded) }.fold(
                onSuccess = { sheets ->
                    val wb = Workbook(sheets.map { it.toTable() }.filter { it.rowCount > 0 })
                    if (wb.sheets.isEmpty()) Result.Failure("网页表格中未解析到有效行")
                    else Result.Success(wb, Format.HTML)
                },
                onFailure = { Result.Failure("解析网页表格失败：${it.message ?: it.javaClass.simpleName}") }
            )
        }

        // 4) 定界文本
        return runCatching {
            val text = TextDecode.decode(bytes)
            val sheets = DelimitedTextReader.parse(text, fileName ?: "")
            val wb = Workbook(sheets.filter { it.rowCount > 0 })
            if (wb.sheets.isEmpty()) Result.Failure("文本内容为空或无法识别行列结构") else Result.Success(wb, Format.DELIMITED)
        }.fold(
            onSuccess = { it },
            onFailure = { Result.Failure("解析文本失败：${it.message ?: it.javaClass.simpleName}") }
        )
    }

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )

    internal fun matches(data: ByteArray, offset: Int, pattern: ByteArray): Boolean {
        if (offset + pattern.size > data.size) return false
        for (i in pattern.indices) if (data[offset + i] != pattern[i]) return false
        return true
    }
}

internal object TextDecode {
    private val CHARSET_DECL = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)", RegexOption.IGNORE_CASE)

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // HTML / XML 中声明的字符集优先级最高
        val asciiHead = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val declared = CHARSET_DECL.find(asciiHead)?.groupValues?.get(1)
        if (!declared.isNullOrBlank()) {
            val normalized = when (declared.lowercase()) {
                "gb2312", "gbk", "gb18030" -> "GBK"
                "utf8" -> "UTF-8"
                else -> declared
            }
            runCatching { return String(bytes, charset(normalized)) }
        }
        // 严格 UTF-8 解码，失败则回退 GBK（中文教务系统导出常见）
        val utf8 = Charsets.UTF_8.newDecoder()
        return runCatching { utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { runCatching { String(bytes, charset("GBK")) }.getOrDefault(String(bytes, Charsets.ISO_8859_1)) }
    }

    /** 在源码中可能混用 HTML 字符集声明，此处按声明优先 */
    fun decodeWithCharset(bytes: ByteArray, charsetName: String?): String {
        if (charsetName.isNullOrBlank()) return decode(bytes)
        return runCatching { String(bytes, charset(charsetName)) }.getOrElse { decode(bytes) }
    }
}

internal fun ByteArray.u16le(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.u32le(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.u64le(offset: Int): Long {
    var v = 0L
    for (i in 7 downTo 0) v = (v shl 8) or (this[offset + i].toLong() and 0xFF)
    return v
}

internal fun ByteArrayOutputStream.writeU8(v: Int) = write(v and 0xFF)
