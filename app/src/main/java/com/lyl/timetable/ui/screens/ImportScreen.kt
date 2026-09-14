package com.lyl.timetable.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.Course
import com.lyl.timetable.importer.TimetableImporter
import com.lyl.timetable.ui.components.AppSwitch
import com.lyl.timetable.ui.components.EmptyStateView
import com.lyl.timetable.ui.components.GlassCard
import com.lyl.timetable.ui.components.GlassIconButton
import com.lyl.timetable.ui.components.GroupSection
import com.lyl.timetable.ui.components.PillButton
import com.lyl.timetable.ui.components.PillStyle
import com.lyl.timetable.ui.components.RowSeparator
import com.lyl.timetable.ui.components.SectionHeader
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
import com.lyl.timetable.ui.theme.courseColor
import com.lyl.timetable.xls.SheetTable
import com.lyl.timetable.xls.SpreadsheetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Parsing : ImportUiState

    data class Parsed(
        val fileName: String,
        val formatLabel: String,
        val outcome: TimetableImporter.Outcome,
        val preview: SheetTable?
    ) : ImportUiState

    data class Failed(val fileName: String, val message: String) : ImportUiState
}

@Composable
fun ImportScreen(
    initialUri: String?,
    onBack: () -> Unit,
    onConfirmImport: (List<Course>, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.colors
    val context = LocalContext.current
    var state by remember { mutableStateOf<ImportUiState>(ImportUiState.Idle) }
    var replaceExisting by remember { mutableStateOf(true) }
    var pendingUri by remember { mutableStateOf<String?>(initialUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pendingUri = uri.toString()
        }
    }

    LaunchedEffect(pendingUri) {
        val uriText = pendingUri ?: return@LaunchedEffect
        state = ImportUiState.Parsing
        state = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriText)
                val fileName = queryDisplayName(context, uri) ?: "课表文件"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext ImportUiState.Failed(fileName, "无法读取该文件，请重新选择。")
                when (val parsed = SpreadsheetParser.parseBytes(bytes, fileName)) {
                    is SpreadsheetParser.Result.Failure ->
                        ImportUiState.Failed(fileName, parsed.message)

                    is SpreadsheetParser.Result.Success -> ImportUiState.Parsed(
                        fileName = fileName,
                        formatLabel = parsed.format.label,
                        outcome = TimetableImporter().analyze(parsed.workbook),
                        preview = parsed.workbook.first
                    )
                }
            }.getOrElse { ImportUiState.Failed("课表文件", "解析过程出错：${it.message ?: "未知错误"}") }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---------------- 顶部 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("导入课表", style = AppType.Title2, color = p.textPrimary)
                Text(
                    "读取本地表格，全程离线",
                    style = AppType.Footnote,
                    color = p.textSecondary
                )
            }
        }

        when (val s = state) {
            ImportUiState.Idle -> IdleContent(onPick = { picker.launch(arrayOf("*/*")) })

            ImportUiState.Parsing -> ParsingContent()

            is ImportUiState.Failed -> FailedContent(
                fileName = s.fileName,
                message = s.message,
                onRetry = { picker.launch(arrayOf("*/*")) }
            )

            is ImportUiState.Parsed -> ParsedContent(
                state = s,
                replaceExisting = replaceExisting,
                onReplaceChange = { replaceExisting = it },
                onRepick = { picker.launch(arrayOf("*/*")) },
                onConfirm = { onConfirmImport(s.outcome.courses, replaceExisting) }
            )
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.lastPathSegment
    return runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()
}

@Composable
private fun IdleContent(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPadding)
    ) {
        GlassCard(contentPadding = PaddingValues(0.dp)) {
            EmptyStateView(
                icon = Icons.Rounded.Upload,
                title = "选择课表文件",
                description = "支持教务导出的 .xls、.xlsx、csv，\n也包括以网页表格形式导出的「伪 xls」。\n" +
                        "导入时会按表格中的周次信息，还原每周不同的安排。",
                action = {
                    PillButton(
                        text = "浏览本地文件",
                        icon = Icons.Rounded.Upload,
                        onClick = onPick,
                        modifier = Modifier.width(210.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun ParsingContent() {
    val p = AppTheme.colors
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = p.accent)
        Spacer(Modifier.height(18.dp))
        Text("正在解析课表…", style = AppType.Headline, color = p.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text("较大文件可能需要数秒", style = AppType.Footnote, color = p.textSecondary)
    }
}

@Composable
private fun FailedContent(fileName: String, message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPadding)
    ) {
        GlassCard(contentPadding = PaddingValues(0.dp)) {
            EmptyStateView(
                icon = Icons.Rounded.ErrorOutline,
                title = "解析失败",
                description = "$fileName\n\n$message",
                action = {
                    PillButton(text = "重新选择文件", onClick = onRetry, style = PillStyle.Glass)
                }
            )
        }
    }
}

@Composable
private fun ParsedContent(
    state: ImportUiState.Parsed,
    replaceExisting: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    onRepick: () -> Unit,
    onConfirm: () -> Unit
) {
    val p = AppTheme.colors
    val outcome = state.outcome
    val distinctCourseCount = outcome.courses.map { it.name }.distinct().size
    val multiSlotCourses = outcome.courses.groupBy { it.name }.filter { it.value.size > 1 }.keys

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPadding)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.GroupGap),
            contentPadding = PaddingValues(top = 14.dp, bottom = 16.dp)
        ) {
            item {
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (outcome.success) p.accentSoft(0.14f)
                                    else p.danger.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (outcome.success) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (outcome.success) p.accent else p.danger,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.fileName,
                                style = AppType.Headline,
                                color = p.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${state.formatLabel} · ${outcome.strategy.label}",
                                style = AppType.Footnote,
                                color = p.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill("课程门数", distinctCourseCount.toString())
                        StatPill("上课时段", outcome.courses.size.toString())
                        if (outcome.skippedRows > 0) StatPill("跳过行", outcome.skippedRows.toString())
                    }
                }
            }

            if (multiSlotCourses.isNotEmpty()) {
                item {
                    GlassCard(
                        contentPadding = PaddingValues(16.dp),
                        strong = true
                    ) {
                        Text(
                            "已识别多时段课程",
                            style = AppType.Footnote,
                            color = p.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "下列课程在不同周次的上课时间或地点不同，已按各自周次分别保存：\n" +
                                    multiSlotCourses.take(8).joinToString("、") +
                                    if (multiSlotCourses.size > 8) " 等 ${multiSlotCourses.size} 门" else "",
                            style = AppType.Footnote,
                            color = p.textSecondary
                        )
                    }
                }
            }

            if (outcome.warnings.isNotEmpty()) {
                item {
                    GlassCard(contentPadding = PaddingValues(16.dp), strong = true) {
                        Text(
                            "提示",
                            style = AppType.Footnote,
                            color = p.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        outcome.warnings.forEach { w ->
                            Text(
                                "· $w",
                                style = AppType.Footnote,
                                color = p.textSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (outcome.courses.isNotEmpty()) {
                item {
                    GroupSection("导入方式") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (replaceExisting) "清空原有课表后写入" else "保留原有课程，追加写入",
                                    style = AppType.Body,
                                    color = p.textPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "共 ${outcome.courses.size} 个时段待写入",
                                    style = AppType.Footnote,
                                    color = p.textSecondary
                                )
                            }
                            AppSwitch(checked = replaceExisting, onCheckedChange = onReplaceChange)
                        }
                    }
                }

                item { SectionHeader("识别结果预览（前 20 条）") }

                item {
                    GroupSection {
                        outcome.courses.take(20).forEachIndexed { index, course ->
                            CoursePreviewRow(course)
                            if (index != outcome.courses.take(20).lastIndex) {
                                RowSeparator(inset = 16.dp)
                            }
                        }
                    }
                }

                if (outcome.courses.size > 20) {
                    item {
                        Text(
                            "其余 ${outcome.courses.size - 20} 条已省略",
                            style = AppType.Footnote,
                            color = p.textTertiary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            if (state.preview != null && state.preview.rowCount > 0) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    GlassCard(
                        contentPadding = PaddingValues(0.dp),
                        onClick = { expanded = !expanded }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "查看原始表格内容",
                                style = AppType.Body,
                                color = p.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (expanded) "收起" else "展开",
                                style = AppType.Footnote,
                                color = p.accent
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            RawTablePreview(state.preview)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PillButton(
                text = "重新选择",
                onClick = onRepick,
                style = PillStyle.Glass
            )
            PillButton(
                text = if (outcome.success) "导入 ${outcome.courses.size} 个时段" else "无法导入",
                onClick = onConfirm,
                enabled = outcome.success,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    val p = AppTheme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(AppDimens.InnerRadius))
            .background(p.fill)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, style = AppType.Caption1, color = p.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = AppType.Headline,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoursePreviewRow(course: Course) {
    val p = AppTheme.colors
    val palette = courseColor(course.colorIndex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(palette.accent(p.isLight))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                course.name,
                style = AppType.Headline,
                color = p.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    course.scheduleText,
                    course.weekText,
                    course.location.takeIf { it.isNotBlank() },
                    course.teacher.takeIf { it.isNotBlank() }
                ).joinToString("  ·  "),
                style = AppType.Footnote,
                color = p.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RawTablePreview(table: SheetTable) {
    val p = AppTheme.colors
    val maxRows = minOf(table.rowCount, 10)
    val maxCols = minOf(table.columnCount, 8)
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        for (r in 0 until maxRows) {
            Row {
                for (col in 0 until maxCols) {
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(34.dp)
                            .background(p.fill)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = table.cell(r, col),
                            style = AppType.Caption1,
                            color = p.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(1.dp))
                }
            }
            Spacer(Modifier.height(1.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "共 ${table.rowCount} 行 × ${table.columnCount} 列（仅显示前 $maxRows 行 / $maxCols 列）",
            style = AppType.Caption2,
            color = p.textTertiary
        )
    }
}
