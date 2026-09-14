package com.lyl.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.ThemeMode
import com.lyl.timetable.data.TimeSlot
import com.lyl.timetable.data.TimetableRepository
import com.lyl.timetable.ui.SettingsGroup
import com.lyl.timetable.ui.components.LargeTitle
import com.lyl.timetable.ui.components.SettingRow
import com.lyl.timetable.ui.theme.AccentPalette
import com.lyl.timetable.ui.theme.OriginTheme
import com.lyl.timetable.ui.theme.courseColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    courses: List<Course>,
    onSettingsChange: (AppSettings) -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
    onEditCourse: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OriginTheme.colors
    var showDatePicker by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var showWeeksDialog by remember { mutableStateOf(false) }
    var showSectionsDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCourseList by remember { mutableStateOf(false) }

    val courseNames = remember(courses) { courses.map { it.name }.distinct() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            LargeTitle(title = "设置", subtitle = "数据全部保存在本机，应用不联网")
            Spacer(Modifier.height(2.dp))
        }

        // ---------------- 学期 ----------------
        item {
            SettingsGroup(title = "学期") {
                SettingRow(
                    title = "开学第一周周一",
                    value = settings.termStartDate.ifBlank { "未设置" },
                    onClick = { showDatePicker = true }
                )
                Hairline()
                SettingRow(
                    title = "学期总周数",
                    value = "${settings.totalWeeks} 周",
                    onClick = { showWeeksDialog = true }
                )
                Hairline()
                SettingRow(
                    title = "每日节数",
                    value = "${settings.sectionCount} 节",
                    subtitle = "课表默认显示行数，实际以课程为准",
                    onClick = { showSectionsDialog = true }
                )
                Hairline()
                SettingRow(
                    title = "当前周",
                    value = if (settings.currentWeekOverride > 0) {
                        "手动指定：第 ${settings.currentWeekOverride} 周"
                    } else {
                        "按开学日期自动推算"
                    },
                    subtitle = "点击可逐周手动切换，超过总周数后恢复自动",
                    onClick = {
                        val next = if (settings.currentWeekOverride >= settings.totalWeeks) 0
                        else settings.currentWeekOverride + 1
                        onSettingsChange(settings.copy(currentWeekOverride = next))
                    }
                )
            }
        }

        // ---------------- 外观 ----------------
        item {
            SettingsGroup(title = "外观") {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("主题模式", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            ChoiceChip(
                                text = mode.label,
                                selected = settings.themeMode == mode,
                                onClick = { onSettingsChange(settings.copy(themeMode = mode)) }
                            )
                        }
                    }
                }
                Hairline()
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("强调色", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentPalette.forEachIndexed { index, pair ->
                            val selected = settings.accentIndex == index
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(pair.start)
                                    .then(
                                        if (selected) Modifier.border(3.dp, c.textPrimary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSettingsChange(settings.copy(accentIndex = index)) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(c.card)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = AccentPalette[settings.accentIndex.coerceIn(0, AccentPalette.size - 1)].name,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary
                    )
                }
                Hairline()
                SettingRow(
                    title = "显示周末",
                    subtitle = "关闭后课表仅显示周一至周五",
                    trailing = {
                        Switch(
                            checked = settings.showWeekend,
                            onCheckedChange = { onSettingsChange(settings.copy(showWeekend = it)) }
                        )
                    }
                )
            }
        }

        // ---------------- 课程与时段 ----------------
        item {
            SettingsGroup(title = "课程与时段") {
                SettingRow(
                    title = "查看全部课程",
                    value = "${courseNames.size} 门 / ${courses.size} 个时段",
                    subtitle = "同一门课可在不同周次安排不同时间或地点",
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { showCourseList = true }
                )
            }
        }

        // ---------------- 作息时间 ----------------
        item {
            SettingsGroup(title = "作息时间") {
                settings.timeSlots.forEachIndexed { index, slot ->
                    SettingRow(
                        title = "第 ${slot.section} 节",
                        value = "${slot.startTime} - ${slot.endTime}",
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = { editingSlot = slot }
                    )
                    if (index != settings.timeSlots.lastIndex) Hairline()
                }
                Hairline()
                SettingRow(
                    title = "恢复默认作息",
                    subtitle = "12 节制（上午 4 · 下午 4 · 晚间 4）",
                    onClick = {
                        onSettingsChange(settings.copy(timeSlots = TimeSlot.default(), sectionCount = 12))
                    }
                )
            }
        }

        // ---------------- 数据 ----------------
        item {
            SettingsGroup(title = "数据") {
                SettingRow(
                    title = "导入课表文件",
                    subtitle = "支持 .xls / .xlsx / csv 及网页表格",
                    trailing = {
                        Icon(
                            Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = OriginTheme.accentStart,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onImport
                )
                Hairline()
                SettingRow(
                    title = "清空全部课程",
                    subtitle = "删除本机所有课程数据",
                    trailing = {
                        Icon(Icons.Rounded.Delete, null, tint = c.danger, modifier = Modifier.size(20.dp))
                    },
                    onClick = { showClearConfirm = true }
                )
            }
        }

        item {
            SettingsGroup(title = "关于") {
                SettingRow(title = "版本", value = "1.0.0")
                Hairline()
                SettingRow(
                    title = "课程表",
                    subtitle = "本地离线课表 · 支持导入教务导出表格"
                )
            }
        }
    }

    // ---------------- 对话框 ----------------

    if (showDatePicker) {
        val initial = TimetableRepository.parseDate(settings.termStartDate) ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val monday = TimetableRepository.mondayOf(date)
                        onSettingsChange(settings.copy(termStartDate = monday.toString()))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSettingsChange(settings.copy(termStartDate = ""))
                    showDatePicker = false
                }) { Text("清除") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showWeeksDialog) {
        NumberDialog(
            title = "学期总周数",
            value = settings.totalWeeks,
            range = 4..31,
            onDismiss = { showWeeksDialog = false },
            onConfirm = {
                onSettingsChange(settings.copy(totalWeeks = it))
                showWeeksDialog = false
            }
        )
    }

    if (showSectionsDialog) {
        NumberDialog(
            title = "每日节数",
            value = settings.sectionCount,
            range = 4..16,
            onDismiss = { showSectionsDialog = false },
            onConfirm = {
                onSettingsChange(settings.copy(sectionCount = it))
                showSectionsDialog = false
            }
        )
    }

    editingSlot?.let { slot ->
        TimeSlotDialog(
            slot = slot,
            onDismiss = { editingSlot = null },
            onConfirm = { start, end ->
                val updated = settings.timeSlots.map {
                    if (it.section == slot.section) it.copy(startTime = start, endTime = end) else it
                }
                onSettingsChange(settings.copy(timeSlots = updated))
                editingSlot = null
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部课程？") },
            text = { Text("该操作会删除本机保存的所有课程记录，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) { Text("清空", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showCourseList) {
        CourseListDialog(
            courses = courses,
            onDismiss = { showCourseList = false },
            onEdit = { course ->
                showCourseList = false
                onEditCourse(course)
            }
        )
    }
}

@Composable
private fun CourseListDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onEdit: (Course) -> Unit
) {
    val c = OriginTheme.colors
    val grouped = remember(courses) {
        courses.groupBy { it.name }.toList().sortedBy { it.first }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(28.dp),
            color = c.card
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("课程与时段", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "共 ${grouped.size} 门课 · ${courses.size} 个时段",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.cardElevated)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, "关闭", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                if (courses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "课表为空，可先导入或手动添加课程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textTertiary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (name, slots) ->
                            item(key = "group-$name") {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(
                                                courseColor(slots.first().colorIndex).foreground(c.isLight)
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    if (slots.size > 1) {
                                        Text(
                                            "${slots.size} 个时段",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OriginTheme.accentStart
                                        )
                                    }
                                }
                            }
                            items(slots.size) { index ->
                                val course = slots[index]
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = c.cardElevated,
                                    onClick = { onEdit(course) }
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            text = course.scheduleText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = c.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = listOfNotNull(
                                                course.weekText,
                                                course.location.takeIf { it.isNotBlank() },
                                                course.teacher.takeIf { it.isNotBlank() }
                                            ).joinToString("  ·  ").ifBlank { "未填写其它信息" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(OriginTheme.colors.divider)
    )
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OriginTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accentSoft(if (c.isLight) 0.14f else 0.24f) else c.cardElevated)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OriginTheme.accentStart else c.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun NumberDialog(
    title: String,
    value: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var current by remember { mutableStateOf(value.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = current.toInt().toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = OriginTheme.accentStart
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = current,
                    onValueChange = { current = it },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toInt().coerceIn(range)) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TimeSlotDialog(
    slot: TimeSlot,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var start by remember { mutableStateOf(slot.startTime) }
    var end by remember { mutableStateOf(slot.endTime) }
    val valid = isValidTime(start) && isValidTime(end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 ${slot.section} 节时间") },
        text = {
            Column {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.take(5) },
                    label = { Text("开始时间") },
                    singleLine = true,
                    isError = !isValidTime(start),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it.take(5) },
                    label = { Text("结束时间") },
                    singleLine = true,
                    isError = !isValidTime(end),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "格式：HH:mm，例如 08:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = OriginTheme.colors.textTertiary
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(start, end) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun isValidTime(text: String): Boolean {
    val t = text.trim()
    if (t.length != 5 || t[2] != ':') return false
    val h = t.substring(0, 2).toIntOrNull() ?: return false
    val m = t.substring(3, 5).toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}
