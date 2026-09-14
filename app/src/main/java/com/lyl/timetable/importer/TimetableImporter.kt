package com.lyl.timetable.importer

import com.lyl.timetable.data.Course
import com.lyl.timetable.data.WeekParity
import com.lyl.timetable.xls.SheetTable
import com.lyl.timetable.xls.Workbook
import java.util.Locale

/**
 * 课表导入映射引擎。
 *
 * 教务系统导出的表格结构差异极大，这里用两级策略：
 *   1. 列表式（一行一门课）—— 通过表头关键词定位「课程 / 教师 / 周次 / 星期 / 节次 / 教室」各列；
 *   2. 矩阵式（星期 × 节次网格）—— 通过表头行识别星期列、通过首列识别节次行，
 *      再对每个单元格做「名称 / 教师 / 教室 / 周次」的启发式拆解。
 *
 * 两种策略都会各自评估产出，取课程数更多的一方。
 */
class TimetableImporter {

    enum class Strategy(val label: String) {
        LIST("按行课程清单"),
        MATRIX("按周课表网格"),
        UNKNOWN("未能识别")
    }

    data class Outcome(
        val courses: List<Course>,
        val strategy: Strategy,
        val sheetName: String,
        val warnings: List<String>,
        val skippedRows: Int,
        val detectedColumns: Map<Field, Int> = emptyMap()
    ) {
        val success: Boolean get() = courses.isNotEmpty()
    }

    enum class Field(val label: String) {
        NAME("课程名称"),
        TEACHER("任课教师"),
        WEEKS("上课周次"),
        DAY("星期"),
        SECTIONS("节次"),
        LOCATION("上课地点")
    }

    private val headerKeywords: Map<Field, List<String>> = mapOf(
        Field.NAME to listOf("课程名称", "课程名", "课程", "科目", "教学班", "名称", "course"),
        Field.TEACHER to listOf("教师", "老师", "任课", "授课", "teacher"),
        Field.WEEKS to listOf("周次", "上课周", "周数", "week"),
        Field.DAY to listOf("星期", "周几", "上课日", "weekday", "day"),
        Field.SECTIONS to listOf("节次", "节数", "上课节", "时间", "section", "period"),
        Field.LOCATION to listOf("地点", "教室", "上课地", "场所", "room", "location")
    )

    fun analyze(workbook: Workbook, overrideSheet: Int? = null): Outcome {
        val candidates = if (overrideSheet != null && overrideSheet in workbook.sheets.indices) {
            listOf(workbook.sheets[overrideSheet])
        } else {
            workbook.sheets
        }

        var best: Outcome? = null
        for (sheet in candidates) {
            if (sheet.rowCount == 0) continue
            val listOutcome = runCatching { parseListStyle(sheet) }.getOrNull()
            val matrixOutcome = runCatching { parseMatrixStyle(sheet) }.getOrNull()
            val pick = listOfNotNull(listOutcome, matrixOutcome).maxByOrNull { it.courses.size }
            if (pick != null && (best == null || pick.courses.size > best!!.courses.size)) best = pick
        }

        return best ?: Outcome(
            courses = emptyList(),
            strategy = Strategy.UNKNOWN,
            sheetName = workbook.sheets.firstOrNull()?.name.orEmpty(),
            warnings = listOf(
                "未能在表格中识别出课表结构。",
                "可支持的两种典型排布：① 一行一门课的清单（含「课程名称 / 教师 / 周次 / 星期 / 节次 / 教室」等表头）；" +
                        "② 星期为列、节次为行的周课表网格。",
                "若表格表头是自定义写法，可先用 Excel 或 WPS 把表头改为上述关键词后再导入。"
            ),
            skippedRows = 0
        )
    }

    // ============================================================
    //  策略一：列表式（一行一门课）
    // ============================================================

    private fun parseListStyle(sheet: SheetTable): Outcome? {
        val headerRow = findHeaderRow(sheet) ?: return null
        val columns = mapColumns(sheet, headerRow)
        if (columns[Field.NAME] == null) return null

        val courses = ArrayList<Course>()
        val warnings = ArrayList<String>()
        var skipped = 0
        var colorSeq = 0

        for (r in headerRow + 1 until sheet.rowCount) {
            val row = sheet.rows[r]
            val name = pick(row, columns[Field.NAME]).cleanName()
            if (name.isBlank() || isHeaderLike(name) || name.length > 60) {
                skipped++
                continue
            }

            val day = parseDay(pick(row, columns[Field.DAY]))
            val sections = parseSections(pick(row, columns[Field.SECTIONS]))
            val weekInfo = parseWeeks(pick(row, columns[Field.WEEKS]))

            if (day == null || sections == null) {
                skipped++
                continue
            }

            courses.add(
                Course(
                    name = name,
                    teacher = pick(row, columns[Field.TEACHER]).cleanTeacher(),
                    location = pick(row, columns[Field.LOCATION]).cleanLocation(),
                    dayOfWeek = day,
                    startSection = sections.first,
                    endSection = sections.last,
                    weekMask = weekInfo.mask,
                    colorIndex = colorSeq++ % 12,
                    note = weekInfo.text.takeIf { it.isNotBlank() } ?: pick(row, columns[Field.WEEKS]).trim()
                )
            )
        }

        if (courses.isEmpty()) return null
        if (skipped > 0) warnings.add("已跳过 $skipped 行无法识别或不完整的数据（缺少星期或节次）。")
        return Outcome(
            courses = mergeDuplicates(courses),
            strategy = Strategy.LIST,
            sheetName = sheet.name,
            warnings = warnings,
            skippedRows = skipped,
            detectedColumns = columns.filterValues { it != null }.mapValues { it.value!! }
        )
    }

    private fun findHeaderRow(sheet: SheetTable): Int? {
        val limit = minOf(sheet.rowCount, 30)
        var bestRow: Int? = null
        var bestHits = 0
        for (r in 0 until limit) {
            val row = sheet.rows[r]
            var hits = 0
            for (field in Field.values()) {
                if (row.any { cell -> matchesKeyword(cell, headerKeywords[field]!!) }) hits++
            }
            if (hits > bestHits) {
                bestHits = hits
                bestRow = r
            }
        }
        return if (bestHits >= 2) bestRow else null
    }

    private fun mapColumns(sheet: SheetTable, headerRow: Int): Map<Field, Int?> {
        val row = sheet.rows[headerRow]
        val result = HashMap<Field, Int?>()
        val taken = HashSet<Int>()
        for (field in Field.values()) {
            val idx = row.indices.firstOrNull { c ->
                c !in taken && matchesKeyword(row[c], headerKeywords[field]!!)
            }
            result[field] = idx
            if (idx != null) taken.add(idx)
        }
        return result
    }

    private fun matchesKeyword(cell: String, keywords: List<String>): Boolean {
        val c = cell.trim().lowercase(Locale.ROOT).replace(" ", "").replace("\n", "")
        if (c.isEmpty()) return false
        return keywords.any { c == it || c.contains(it) }
    }

    private fun pick(row: List<String>, index: Int?): String {
        if (index == null || index !in row.indices) return ""
        return row[index].trim()
    }

    private fun isHeaderLike(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return headerKeywords.values.flatten().any { t == it }
    }

    // ============================================================
    //  策略二：矩阵式（星期 × 节次）
    // ============================================================

    private fun parseMatrixStyle(sheet: SheetTable): Outcome? {
        val header = findWeekHeaderRow(sheet) ?: return null
        val headerRow = header.first
        val dayColumns = header.second
        if (dayColumns.size < 2) return null

        val sectionColumn = findSectionColumn(sheet, headerRow, dayColumns.values.min())
        val courses = ArrayList<Course>()
        val warnings = ArrayList<String>()
        var skipped = 0

        var fallbackSection = 1
        for (r in headerRow + 1 until sheet.rowCount) {
            val row = sheet.rows[r]
            val sectionText = if (sectionColumn != null) pick(row, sectionColumn) else ""
            val sections = parseSections(sectionText) ?: run {
                val guess = fallbackSection
                fallbackSection += 1
                IntRange(guess, guess)
            }
            if (sectionColumn != null && sections.first > 1) fallbackSection = sections.last + 1

            for ((day, col) in dayColumns) {
                val text = pick(row, col)
                if (text.isBlank()) continue
                val parsed = parseCellContent(text)
                if (parsed.name.isBlank()) {
                    skipped++
                    continue
                }
                courses.add(
                    Course(
                        name = parsed.name,
                        teacher = parsed.teacher,
                        location = parsed.location,
                        dayOfWeek = day,
                        startSection = sections.first,
                        endSection = sections.last,
                        weekMask = parsed.mask,
                        colorIndex = -1,
                        note = parsed.weekNote
                    )
                )
            }
        }

        if (courses.isEmpty()) return null
        if (skipped > 0) warnings.add("有 $skipped 个单元格内容无法解析，已忽略。")
        return Outcome(
            courses = assignColors(mergeAdjacentSections(mergeDuplicates(courses))),
            strategy = Strategy.MATRIX,
            sheetName = sheet.name,
            warnings = warnings,
            skippedRows = skipped
        )
    }

    /** 找到星期表头行，返回行号与「星期 → 列号」映射 */
    private fun findWeekHeaderRow(sheet: SheetTable): Pair<Int, Map<Int, Int>>? {
        val limit = minOf(sheet.rowCount, 15)
        var bestRow = -1
        var bestMap: Map<Int, Int> = emptyMap()
        for (r in 0 until limit) {
            val row = sheet.rows[r]
            val map = LinkedHashMap<Int, Int>()
            for (c in row.indices) {
                val cell = row[c].trim()
                if (cell.length > 8) continue
                val day = parseDay(cell) ?: continue
                if (!map.containsKey(day)) map[day] = c
            }
            if (map.size >= 2 && map.size > bestMap.size) {
                bestRow = r
                bestMap = map
            }
        }
        return if (bestRow >= 0) bestRow to bestMap else null
    }

    /** 节次列：星期表头左侧、含节次标识最多的一列 */
    private fun findSectionColumn(sheet: SheetTable, headerRow: Int, firstDayCol: Int): Int? {
        var bestCol: Int? = null
        var bestHits = 0
        for (c in 0 until firstDayCol.coerceAtLeast(1)) {
            var hits = 0
            val last = (headerRow + 40).coerceAtMost(sheet.rowCount - 1)
            for (r in headerRow..last) {
                if (parseSections(sheet.cell(r, c)) != null) hits++
            }
            if (hits > bestHits) {
                bestHits = hits
                bestCol = c
            }
        }
        // 至少命中 3 行才认为是节次列，否则交给默认序号
        return if (bestHits >= 3) bestCol else null
    }

    /** 拆解矩阵单元格内的复合文本 */
    private data class CellContent(
        val name: String,
        val teacher: String,
        val location: String,
        val mask: Int,
        val weekNote: String
    )

    /**
     * 拆分规则（按顺序）：
     *   1. 先剥离周次与节次片段（并保留一个空格作为分隔）；
     *   2. 从中挑出符合地点特征的片段作为教室；
     *   3. 剩下的第一个片段作为课程名；
     *   4. 其余片段中符合姓名特征的作为教师，多教师合并。
     *
     * 顺序很关键：课程名优先于教师，避免「高等数学」这类四字课程名被误判成教师姓名。
     */
    private fun parseCellContent(raw: String): CellContent {
        var work = raw.replace('\u00A0', ' ').trim()
        var mask = 0
        var weekNote = ""

        val weekMatch = WeekPatterns.findAll(work)
        if (weekMatch != null) {
            mask = weekMatch.mask
            weekNote = weekMatch.text
            work = weekMatch.remaining
        }
        val sectionMatch = SectionPatterns.find(work)
        if (sectionMatch != null) work = sectionMatch.remaining

        // 去掉剥离后残留的空括号
        work = work.replace(Regex("[（(]\\s*[)）]"), " ")

        val parts = work
            .split(Regex("[\\n\\r]+|[|｜;；,，/、]+|\\s+"))
            .map { it.trim(' ', '.', '·', '-', '—') }
            .filter { it.isNotEmpty() }

        var location = ""
        val candidates = ArrayList<String>(parts.size)
        for (part in parts) {
            val cleaned = part.trim()
            if (cleaned.isEmpty()) continue
            // 仅当整段被括号包裹时才剥离括号（如「(张伟)」），保留「体育(篮球)」这类名称本身
            val inner = Regex("^[（(](.+)[)）]$").find(cleaned)?.groupValues?.get(1)?.trim() ?: cleaned
            if (inner.isEmpty()) continue
            if (location.isEmpty() && looksLikeLocation(inner)) {
                location = inner
                continue
            }
            candidates.add(cleaned)
        }

        // 括号内的内容（如「高等数学(张伟)」中的教师）仅作为兜底来源，
        // 因为课程名本身也可能带括号说明，例如「体育（1）(定向运动)」。
        val parens = Regex("[（(]([^（）()]{1,20})[)）]").findAll(work)
            .map { it.groupValues[1].trim() }
            .toList()

        val ordered = LinkedHashSet(candidates).toMutableList()
        var name = if (ordered.isNotEmpty()) ordered.removeAt(0) else ""

        // 先用换行 / 空白 / 标点分隔出的片段，语义更明确
        var teacher = ""
        for (p in ordered) {
            when {
                teacher.isEmpty() && looksLikeTeacher(p) -> teacher = p
                location.isEmpty() && looksLikeLocation(p) -> location = p
                teacher.isEmpty() -> teacher = p
                looksLikeTeacher(p) -> teacher = "$teacher、$p"
            }
        }

        // 仍有空缺时才用括号内容补齐，并把补上的部分从课程名中剥离
        val consumed = ArrayList<String>(2)
        for (p in parens) {
            if (p.isEmpty()) continue
            when {
                location.isEmpty() && looksLikeLocation(p) -> {
                    location = p
                    consumed.add(p)
                }

                teacher.isEmpty() && looksLikeTeacher(p) -> {
                    teacher = p
                    consumed.add(p)
                }
            }
        }
        for (p in consumed) {
            name = name.replace("($p)", " ").replace("（$p）", " ")
        }
        name = name.replace(Regex("\\s{2,}"), " ").trim()

        name = name.replace(Regex("^[\\d\\s．.、]+"), "").trim()
        // 教师名后常带工号括号，如「王卫卫（0602019）」
        teacher = teacher.replace(Regex("[（(]\\s*\\d{4,}\\s*[)）]"), "").trim()
        return CellContent(name, teacher, location, mask, weekNote)
    }

    /** 教室特征：A101 / B-203、计算机楼501、教三301、体育馆 等 */
    private fun looksLikeLocation(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 2..20) return false
        if (t.any { it.isWhitespace() }) return false
        // 纯字母数字房号
        if (Regex("^[A-Za-z]{1,3}[-－]?\\d{2,4}$").matches(t)) return true
        if (Regex("^[A-Za-z]{1,2}\\d{1,3}[-－]\\d{1,4}$").matches(t)) return true
        // 「XX楼XXX」「X楼-101」「XX区X301」等（允许字母开头，如 N楼-411）
        if (Regex("^[A-Za-z\\u4e00-\\u9fa5]{1,6}(楼|区|馆|室|场)[A-Za-z0-9\\-－区栋号]{0,10}$").matches(t)) return true
        // 以场馆名称结尾的短词
        if (Regex("^[\\u4e00-\\u9fa5]{1,6}(楼|馆|场|室|区|园|院|中心|教室)$").matches(t)) return true
        // 「教三301」「三教301」这类中文前缀 + 数字
        if (Regex("^[\\u4e00-\\u9fa5]{1,4}[-－]?[A-Za-z]?\\d{1,4}(室|教室|号)?$").matches(t)) return true
        return false
    }

    /** 姓名特征：2-4 个汉字，或由顿号/逗号分隔的多个姓名，或以「老师」结尾 */
    private fun looksLikeTeacher(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 14) return false
        if (t.any { it.isWhitespace() }) return false
        if (looksLikeLocation(t)) return false
        val segments = t.split(Regex("[、,，]+")).filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false
        return segments.all { seg ->
            seg.endsWith("老师") || (seg.length in 2..4 && seg.all { it.code in 0x4E00..0x9FFF })
        }
    }

    // ============================================================
    //  字段解析：星期 / 节次 / 周次
    // ============================================================

    private val dayMap: Map<String, Int> = buildMap {
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        val english = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        names.forEachIndexed { i, cn ->
            val day = i + 1
            put("周$cn", day)
            put("星期$cn", day)
            put("礼拜$cn", day)
            put("周$day", day)
            put("星期$day", day)
            put("0$day", day)
            put(english[i], day)
        }
        put("周日", 7)
        put("周天", 7)
        put("星期天", 7)
        put("sun", 7)
        put("sunday", 7)
    }

    internal fun parseDay(raw: String): Int? {
        val t = raw.trim().lowercase(Locale.ROOT).replace(" ", "")
        if (t.isEmpty()) return null
        dayMap[t]?.let { return it }
        // 兼容「星期一 第1-2节」这类复合文本
        for (key in listOf(
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日", "星期天",
            "周一", "周二", "周三", "周四", "周五", "周六", "周日", "周天"
        )) {
            if (t.startsWith(key) || t == key) return dayMap[key]
        }
        val num = Regex("^(?:周|星期|礼拜)\\s*([1-7])").find(t)?.groupValues?.get(1)
        if (num != null) return num.toInt()
        // 英文完整写法（Monday / Tuesday …）按前三个字母匹配
        if (t.length > 3 && t.all { it in 'a'..'z' }) {
            dayMap[t.substring(0, 3)]?.let { return it }
        }
        return null
    }

    /** 解析节次，如「1-2」「第3节」「0102」「03,04」 */
    internal fun parseSections(raw: String): IntRange? {
        val t = raw.trim().replace('\u00A0', ' ')
        if (t.isEmpty()) return null

        // 4 位数字：0102 表示第 1-2 节，0304 表示第 3-4 节
        if (Regex("^\\d{4}$").matches(t)) {
            val a = t.substring(0, 2).toIntOrNull()
            val b = t.substring(2, 4).toIntOrNull()
            if (a != null && b != null && a in 1..20 && b in 1..20) {
                return IntRange(minOf(a, b), maxOf(a, b))
            }
        }
        // 形如 1-2 节 / 第1-2节 / 1~2
        Regex("(\\d{1,2})\\s*[-~－—至]\\s*(\\d{1,2})").find(t)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && a in 1..20 && b in 1..20) {
                return IntRange(minOf(a, b), maxOf(a, b))
            }
        }
        // 形如 3,4 / 03、04
        Regex("^0?(\\d{1,2})\\s*[、,，]\\s*0?(\\d{1,2})$").find(t)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && a in 1..20 && b in 1..20) {
                return IntRange(minOf(a, b), maxOf(a, b))
            }
        }
        // 形如 第3节 / 3节
        Regex("第?\\s*(\\d{1,2})\\s*节").find(t)?.groupValues?.get(1)?.toIntOrNull()?.let {
            if (it in 1..20) return IntRange(it, it)
        }
        // 纯数字
        val pure = Regex("^0?(\\d{1,2})$").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (pure != null && pure in 1..20) return IntRange(pure, pure)
        return null
    }

    internal data class WeekResult(val mask: Int, val text: String, val remaining: String)

    /** 解析周次文本。命中片段会从 remaining 中移除并补一个空格，避免课程名与教师被粘连。 */
    internal object WeekPatterns {

        private fun consume(input: String, range: IntRange): String =
            input.replaceRange(range, " ").replace(Regex("\\s{2,}"), " ")

        /** 解析单段周次文本，支持「4、5、7-18」这类数字与范围混排 */
        private fun parseWeekList(segment: String): Int {
            var mask = 0
            for (piece in segment.split(Regex("[,，、;；\\s]+"))) {
                val p = piece.trim()
                if (p.isEmpty()) continue
                val range = Regex("^(\\d{1,2})\\s*[-~－—至]\\s*(\\d{1,2})$").find(p)
                if (range != null) {
                    val a = range.groupValues[1].toIntOrNull() ?: continue
                    val b = range.groupValues[2].toIntOrNull() ?: continue
                    if (a in 1..31 && b in 1..31) {
                        for (w in minOf(a, b)..maxOf(a, b)) mask = mask or (1 shl (w - 1))
                    }
                    continue
                }
                val single = Regex("^(\\d{1,2})$").find(p)?.groupValues?.get(1)?.toIntOrNull()
                if (single != null && single in 1..31) mask = mask or (1 shl (single - 1))
            }
            return mask
        }

        private const val ODD_MASK = 0x55555555
        private const val EVEN_MASK = 0xAAAAAAAA.toInt()

        /**
         * 扫描输入中的**全部**周次片段并合并。
         *
         * 教务系统常见的「教师[周次]周地点」写法会出现多段周次，
         * 例如「周文康[10-16]周，[4，5，7-9]周」，需要逐段识别后取并集。
         */
        fun findAll(input: String): WeekResult? {
            data class Hit(val range: IntRange, val mask: Int, val text: String)

            val hits = ArrayList<Hit>(4)
            val taken = BooleanArray(input.length)
            fun free(range: IntRange): Boolean =
                range.first >= 0 && range.last < input.length && range.all { !taken[it] }

            fun record(range: IntRange, mask: Int, text: String) {
                if (mask == 0 || !free(range)) return
                hits.add(Hit(range, mask, text))
                for (i in range) taken[i] = true
            }

            // 1) [4，5，7-18]周 —— 方括号包裹的周次列表
            Regex("\\[\\s*([0-9\\s，,、;；\\-~－—至]+?)\\s*\\]\\s*周").findAll(input).forEach { m ->
                record(m.range, parseWeekList(m.groupValues[1]), m.value.trim())
            }

            // 2) 1-16周 / 第1-16周 / 1-16周(单)
            Regex("(?:第)?\\s*(\\d{1,2})\\s*[-~－—至]\\s*(\\d{1,2})\\s*周\\s*(?:[（(]\\s*(单|双)\\s*[)）])?")
                .findAll(input).forEach { m ->
                    val a = m.groupValues[1].toIntOrNull() ?: return@forEach
                    val b = m.groupValues[2].toIntOrNull() ?: return@forEach
                    if (a !in 1..31 || b !in 1..31) return@forEach
                    val parity = when (m.groupValues[3]) {
                        "单" -> WeekParity.ODD
                        "双" -> WeekParity.EVEN
                        else -> WeekParity.ALL
                    }
                    record(m.range, Course.maskOf(a, b, parity), m.value.trim())
                }

            // 3) 1,3,5周 这类纯列表（可带单双周标注）
            Regex("((?:\\d{1,2}\\s*[,，、]\\s*){1,}\\d{1,2})\\s*周\\s*(?:[（(]\\s*(单|双)\\s*[)）])?")
                .findAll(input).forEach { m ->
                    var mask = parseWeekList(m.groupValues[1])
                    if (mask == 0) return@forEach
                    when (m.groupValues[2]) {
                        "单" -> mask = mask and ODD_MASK
                        "双" -> mask = mask and EVEN_MASK
                    }
                    record(m.range, mask, m.value.trim())
                }

            // 4) 单独出现的「单周 / 双周」，未给范围时按 1-20 周
            Regex("[（(]?\\s*(单周|双周)\\s*[)）]?").findAll(input).forEach { m ->
                val parity = if (m.groupValues[1] == "单周") WeekParity.ODD else WeekParity.EVEN
                record(m.range, Course.maskOf(1, 20, parity), m.value.trim())
            }

            // 5) 周次：1-16 这类带前缀的写法
            Regex("周次\\s*[:：]?\\s*([\\d\\-~，,、]+)").findAll(input).forEach { m ->
                record(m.range, parseWeekList(m.groupValues[1]), m.value.trim())
            }

            if (hits.isEmpty()) return null

            var mask = 0
            for (h in hits) mask = mask or h.mask

            // 从后往前移除命中片段，保留一个空格避免课程名与教师粘连
            val sb = StringBuilder(input)
            for (h in hits.sortedByDescending { it.range.first }) {
                sb.replace(h.range.first, h.range.last + 1, " ")
            }
            val remaining = sb.toString().replace(Regex("\\s{2,}"), " ").trim()
            return WeekResult(mask, hits.joinToString("，") { it.text }, remaining)
        }

        fun find(input: String): WeekResult? {
            val t = input

            val rangeWithUnit = Regex("(?:第)?\\s*(\\d{1,2})\\s*[-~－—至]\\s*(\\d{1,2})\\s*周\\s*(?:[（(]\\s*(单|双)\\s*[)）])?")
            rangeWithUnit.find(t)?.let { m ->
                val a = m.groupValues[1].toIntOrNull()
                val b = m.groupValues[2].toIntOrNull()
                if (a != null && b != null && a in 1..31 && b in a..31) {
                    val parity = when (m.groupValues[3]) {
                        "单" -> WeekParity.ODD
                        "双" -> WeekParity.EVEN
                        else -> WeekParity.ALL
                    }
                    return WeekResult(Course.maskOf(a, b, parity), m.value.trim(), consume(t, m.range))
                }
            }

            val listWithUnit = Regex("((?:\\d{1,2}\\s*[,，、]\\s*){1,}\\d{1,2})\\s*周\\s*(?:[（(]\\s*(单|双)\\s*[)）])?")
            listWithUnit.find(t)?.let { m ->
                val weeks = m.groupValues[1].split(Regex("[,，、]"))
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..31 }
                if (weeks.isNotEmpty()) {
                    var mask = 0
                    weeks.forEach { mask = mask or (1 shl (it - 1)) }
                    return WeekResult(mask, m.value.trim(), consume(t, m.range))
                }
            }

            val parityOnly = Regex("[（(]?\\s*(单周|双周)\\s*[)）]?")
            parityOnly.find(t)?.let { m ->
                val parity = if (m.groupValues[1] == "单周") WeekParity.ODD else WeekParity.EVEN
                return WeekResult(Course.maskOf(1, 20, parity), m.value.trim(), consume(t, m.range))
            }

            val explicit = Regex("周次\\s*[:：]?\\s*([\\d\\-~，,、]+)")
            explicit.find(t)?.let { m ->
                val seg = m.groupValues[1]
                var mask = 0
                for (piece in seg.split(Regex("[,，、]"))) {
                    val p = piece.trim()
                    if (p.contains('-') || p.contains('~')) {
                        val f = p.split(Regex("[-~]"))
                        val a = f.getOrNull(0)?.trim()?.toIntOrNull()
                        val b = f.getOrNull(1)?.trim()?.toIntOrNull()
                        if (a != null && b != null) {
                            for (w in minOf(a, b)..maxOf(a, b)) {
                                if (w in 1..31) mask = mask or (1 shl (w - 1))
                            }
                        }
                    } else {
                        p.toIntOrNull()?.let { if (it in 1..31) mask = mask or (1 shl (it - 1)) }
                    }
                }
                if (mask != 0) return WeekResult(mask, m.value.trim(), consume(t, m.range))
            }
            return null
        }

        /** 未命中任何周次写法时，默认 1-20 周全周上课 */
        fun parse(raw: String): WeekResult {
            val r = find(raw)
            return r ?: WeekResult(
                Course.maskOf(1, 20, WeekParity.ALL),
                "",
                raw
            )
        }
    }

    private object SectionPatterns {
        fun find(input: String): WeekResult? {
            val m = Regex("(?:第)?\\s*\\d{1,2}\\s*[-~－—]\\s*\\d{1,2}\\s*节").find(input) ?: return null
            return WeekResult(0, m.value.trim(), input.replaceRange(m.range, " ").replace(Regex("\\s{2,}"), " "))
        }
    }

    private fun parseWeeks(raw: String): WeekResult = WeekPatterns.parse(raw)

    // ============================================================
    //  收尾处理
    // ============================================================

    /** 合并同一门课在同一时段的重复记录（合并单元格展开后会产生重复） */
    private fun mergeDuplicates(courses: List<Course>): List<Course> {
        val grouped = LinkedHashMap<String, Course>()
        for (c in courses) {
            val key = "${c.name}|${c.dayOfWeek}|${c.startSection}|${c.endSection}|${c.teacher}|${c.location}"
            val exist = grouped[key]
            grouped[key] = if (exist == null) c else exist.copy(weekMask = exist.weekMask or c.weekMask)
        }
        return grouped.values.sortedWith(
            compareBy({ it.dayOfWeek }, { it.startSection }, { it.name })
        )
    }

    /**
     * 合并相邻节次的同一条课程。
     *
     * 矩阵式课表中「第1节」「第2节」通常是两行，同一门课会占两格；
     * 合并后可还原为一门跨节课程（1-2 节），与课表原始语义一致。
     */
    private fun mergeAdjacentSections(courses: List<Course>): List<Course> {
        val sorted = courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
        val result = ArrayList<Course>(sorted.size)
        for (c in sorted) {
            val last = result.lastOrNull()
            val mergeable = last != null &&
                    last.dayOfWeek == c.dayOfWeek &&
                    last.name == c.name &&
                    last.teacher == c.teacher &&
                    last.location == c.location &&
                    last.weekMask == c.weekMask &&
                    c.startSection == last.endSection + 1
            if (mergeable) {
                result[result.size - 1] = last!!.copy(endSection = c.endSection)
            } else {
                result.add(c)
            }
        }
        return result
    }

    /** 同一门课统一配色，视觉上便于区分 */
    private fun assignColors(courses: List<Course>): List<Course> {
        val palette = HashMap<String, Int>()
        var next = 0
        return courses.map { c ->
            val idx = palette.getOrPut(c.name) { next++ % 12 }
            c.copy(colorIndex = idx)
        }
    }

    // ---------------- 文本清理 ----------------

    private fun String.cleanName(): String = this
        .replace(Regex("[\\s\\u00A0]+"), " ")
        .trim(' ', '-', '—', '\n')

    private fun String.cleanTeacher(): String = this
        .replace(Regex("[\\s\\u00A0]+"), "")
        .trim(' ', ',', '，', '/')

    private fun String.cleanLocation(): String = this
        .replace(Regex("[\\s\\u00A0]+"), "")
        .trim(' ', ',', '，')
}
