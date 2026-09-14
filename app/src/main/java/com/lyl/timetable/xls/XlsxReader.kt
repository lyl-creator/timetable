package com.lyl.timetable.xls

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * OOXML（.xlsx）解析：ZIP 容器 + XML。
 * 仅依赖 JDK 自带的 java.util.zip 与 javax.xml，Android 与桌面 JVM 均可用。
 */
internal object XlsxReader {

    private const val NS_SPREADSHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun parse(bytes: ByteArray): Workbook {
        val entries = unzip(bytes)
        if (entries.isEmpty()) error("压缩包为空")

        val workbookXml = entries["xl/workbook.xml"]
            ?: entries.entries.firstOrNull { it.key.equals("xl/workbook.xml", true) }?.value
            ?: error("缺少 xl/workbook.xml")

        val relsXml = entries["xl/_rels/workbook.xml.rels"]

        // sheet 名称 → 目标路径
        val relTargets = HashMap<String, String>()
        if (relsXml != null) {
            val relRoot = parseXml(relsXml)
            val rels = relRoot.getElementsByTagName("Relationship")
            for (i in 0 until rels.length) {
                val e = rels.item(i) as? Element ?: continue
                val id = e.getAttribute("Id")
                val target = e.getAttribute("Target")
                if (id.isNotEmpty() && target.isNotEmpty()) relTargets[id] = target
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { readSharedStrings(it) } ?: emptyList()

        val root = parseXml(workbookXml)
        val sheetNodes = root.getElementsByTagName("sheet")
        val sheets = ArrayList<SheetTable>()

        for (i in 0 until sheetNodes.length) {
            val e = sheetNodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name").ifBlank { "Sheet${i + 1}" }
            val rid = attrByLocalName(e, "id")
            val target = relTargets[rid] ?: "worksheets/sheet${i + 1}.xml"
            val path = normalizeSheetPath(target)
            val xmlBytes = entries[path] ?: entries.entries.firstOrNull { it.key.endsWith(path.substringAfterLast('/'), true) }?.value
            if (xmlBytes == null) {
                sheets.add(SheetTable(name, emptyList()))
                continue
            }
            sheets.add(readSheet(name, xmlBytes, sharedStrings))
        }
        return Workbook(sheets)
    }

    private fun normalizeSheetPath(target: String): String {
        val t = target.removePrefix("/")
        return if (t.startsWith("xl/")) t else "xl/$t"
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            var guard = 0
            while (entry != null && guard++ < 4096) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    val bos = ByteArrayOutputStream(16 * 1024)
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = zis.read(buf)
                        if (n <= 0) break
                        bos.write(buf, 0, n)
                        if (bos.size() > 64 * 1024 * 1024) break
                    }
                    out[name] = bos.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun parseXml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isIgnoringElementContentWhitespace = true }
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        return doc.documentElement
    }

    private fun attrByLocalName(e: Element, local: String): String {
        val attrs = e.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            val n = a.nodeName
            if (n == local || n.endsWith(":$local")) return a.nodeValue ?: ""
        }
        return ""
    }

    /** 共享字符串表：<si> 下可能含多个 <r><t> 片段，需要拼接 */
    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val root = runCatching { parseXml(bytes) }.getOrNull() ?: return emptyList()
        val siNodes = root.getElementsByTagName("si")
        val out = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            val tNodes = si.getElementsByTagName("t")
            if (tNodes.length == 0) {
                out.add("")
                continue
            }
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) sb.append(tNodes.item(j).textContent ?: "")
            out.add(sb.toString().trim())
        }
        return out
    }

    private fun readSheet(name: String, bytes: ByteArray, sst: List<String>): SheetTable {
        val root = runCatching { parseXml(bytes) }.getOrNull() ?: return SheetTable(name, emptyList())
        val sheet = SparseSheet(name)
        val rows = root.getElementsByTagName("row")
        for (i in 0 until rows.length) {
            val rowEl = rows.item(i) as? Element ?: continue
            val rowIdx = rowEl.getAttribute("r").toIntOrNull()?.minus(1) ?: i
            var autoCol = 0
            val cells = rowEl.getElementsByTagName("c")
            for (j in 0 until cells.length) {
                val c = cells.item(j) as? Element ?: continue
                val ref = c.getAttribute("r")
                val colIdx = columnIndex(ref) ?: autoCol
                autoCol = colIdx + 1
                val text = cellText(c, sst) ?: continue
                sheet.put(rowIdx, colIdx, text)
            }
        }
        return sheet.toTable()
    }

    private fun cellText(c: Element, sst: List<String>): String? {
        val type = c.getAttribute("t")
        when (type) {
            "inlineStr" -> {
                val t = c.getElementsByTagName("t")
                if (t.length == 0) return null
                val sb = StringBuilder()
                for (i in 0 until t.length) sb.append(t.item(i).textContent ?: "")
                return sb.toString().trim().ifEmpty { null }
            }
            "s" -> {
                val v = firstChildText(c, "v") ?: return null
                val idx = v.trim().toDoubleOrNull()?.toInt() ?: return null
                return sst.getOrNull(idx)?.ifEmpty { null }
            }
            "str" -> return firstChildText(c, "v")?.trim()?.ifEmpty { null }
            "b" -> {
                val v = firstChildText(c, "v")?.trim() ?: return null
                return if (v == "1") "TRUE" else "FALSE"
            }
            "e" -> return null
            else -> {
                val v = firstChildText(c, "v") ?: return null
                val d = v.trim().toDoubleOrNull() ?: return v.trim().ifEmpty { null }
                return NumberFmt.format(d).ifEmpty { null }
            }
        }
    }

    private fun firstChildText(parent: Element, tag: String): String? {
        val list = parent.getElementsByTagName(tag)
        if (list.length == 0) return null
        val node: Node = list.item(0)
        return node.textContent
    }

    /** 单元格引用（如 "AB12"）转 0 基列号 */
    private fun columnIndex(ref: String): Int? {
        var col = 0
        var seen = false
        for (ch in ref) {
            if (ch.isLetter()) {
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
                seen = true
            } else break
        }
        return if (seen) col - 1 else null
    }
}
