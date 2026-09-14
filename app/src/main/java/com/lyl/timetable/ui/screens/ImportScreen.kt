package com.lyl.timetable.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.lyl.timetable.ui.components.CircleIconButton
import com.lyl.timetable.ui.components.EmptyState
import com.lyl.timetable.ui.components.GradientButton
import com.lyl.timetable.ui.components.OriginCard
import com.lyl.timetable.ui.components.SectionLabel
import com.lyl.timetable.ui.components.SoftButton
import com.lyl.timetable.ui.theme.OriginTheme
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
    val c = OriginTheme.colors
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

                    is SpreadsheetParser.Result.Success -> {
                        val outcome = TimetableImporter().analyze(parsed.workbook)
                        ImportUiState.Parsed(
                            fileName = fileName,
                            formatLabel = parsed.format.label,
                            outcome = outcome,
                            preview = parsed.workbook.first
                        )
                    }
                }
            }.getOrElse { ImportUiState.Failed("课表文件", "解析过程出错：${it.message ?: "未知错误"}") }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                size = 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("导入课表", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(
                    "读取本地表格文件，全程离线",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
        }

        Spacer(Modifier.height(18.dp))

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
    Column(Modifier.fillMaxSize()) {
        OriginCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                icon = Icons.Rounded.Upload,
                title = "选择课表文件",
                description = "支持教务系统导出的 .xls、.xlsx、csv 文件，" +
                        "也包括以网页表格形式导出的「伪 xls」。\n" +
                        "导入时会按表格中的周次信息，还原每一周不同的上课安排。",
                action = {
                    GradientButton(
                        text = "浏览本地文件",
                        icon = Icons.Rounded.InsertDriveFile,
                        onClick = onPick,
                        modifier = Modifier.width(200.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun ParsingContent() {
    val c = OriginTheme.colors
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = OriginTheme.accentStart)
        Spacer(Modifier.height(18.dp))
        Text("正在解析课表…", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text("较大文件可能需要数秒", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

@Composable
private fun FailedContent(fileName: String, message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OriginCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                icon = Icons.Rounded.ErrorOutline,
                title = "解析失败",
                description = "$fileName\n\n$message",
                action = {
                    SoftButton(text = "重新选择文件", onClick = onRetry, icon = Icons.Rounded.InsertDriveFile)
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
    val c = OriginTheme.colors
    val outcome = state.outcome
    val distinctCourseCount = outcome.courses.map { it.name }.distinct().size
    val multiSlotCourses = outcome.courses.groupBy { it.name }.filter { it.value.size > 1 }.keys

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                OriginCard(contentPadding = PaddingValues(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    if (outcome.success) OriginTheme.accentStart.copy(alpha = 0.14f)
                                    else c.danger.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (outcome.success) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (outcome.success) OriginTheme.accentStart else c.danger,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                color = c.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${state.formatLabel} · ${outcome.strategy.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill("课程门数", "$distinctCourseCount")
                        StatPill("上课时段", "${outcome.courses.size}")
                        if (outcome.skippedRows > 0) StatPill("跳过行", "${outcome.skippedRows}")
                    }
                }
            }

            if (multiSlotCourses.isNotEmpty()) {
                item {
                    OriginCard(
                        contentPadding = PaddingValues(16.dp),
                        color = c.cardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("已识别多时段课程", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "下列课程在不同周次的上课时间或地点不同，已按各自周次分别保存：\n" +
                                    multiSlotCourses.take(8).joinToString("、") +
                                    if (multiSlotCourses.size > 8) " 等 ${multiSlotCourses.size} 门" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }
            }

            if (outcome.warnings.isNotEmpty()) {
                item {
                    OriginCard(
                        contentPadding = PaddingValues(16.dp),
                        color = c.cardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("提示", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        outcome.warnings.forEach { w ->
                            Text(
                                "· $w",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (outcome.courses.isNotEmpty()) {
                item {
                    OriginCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("导入方式", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (replaceExisting) "清空原有课表后写入" else "保留原有课程，追加写入",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textTertiary
                                )
                            }
                            Switch(checked = replaceExisting, onCheckedChange = onReplaceChange)
                        }
                    }
                }

                item { SectionLabel("识别结果预览（前 20 条时段）") }

                items(outcome.courses.take(20)) { course ->
                    CoursePreviewRow(course)
                }

                if (outcome.courses.size > 20) {
                    item {
                        Text(
                            "其余 ${outcome.courses.size - 20} 条已省略",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            if (state.preview != null && state.preview.rowCount > 0) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    OriginCard(
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expanded = !expanded }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "查看原始表格内容",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (expanded) "收起" else "展开",
                                style = MaterialTheme.typography.bodySmall,
                                color = OriginTheme.accentStart
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            RawTablePreview(state.preview)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SoftButton(
                text = "重新选择",
                onClick = onRepick,
                icon = Icons.Rounded.InsertDriveFile
            )
            GradientButton(
                text = if (outcome.success) "确认导入 ${outcome.courses.size} 个时段" else "无法导入",
                onClick = onConfirm,
                enabled = outcome.success,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    val c = OriginTheme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardElevated)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoursePreviewRow(course: Course) {
    val c = OriginTheme.colors
    val palette = courseColor(course.colorIndex)
    OriginCard(contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.foreground(c.isLight))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    course.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RawTablePreview(table: SheetTable) {
    val c = OriginTheme.colors
    val maxRows = minOf(table.rowCount, 10)
    val maxCols = minOf(table.columnCount, 8)
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
    ) {
        for (r in 0 until maxRows) {
            Row {
                for (col in 0 until maxCols) {
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(34.dp)
                            .background(c.cardElevated)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = table.cell(r, col),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
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
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary
        )
    }
}
