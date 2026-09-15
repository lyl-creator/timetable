package com.lyl.timetable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.ScheduleTemplate
import com.lyl.timetable.data.TimeSlot
import com.lyl.timetable.data.TimeText
import com.lyl.timetable.ui.components.GlassIconButton
import com.lyl.timetable.ui.components.GroupSection
import com.lyl.timetable.ui.components.StepperDialog
import com.lyl.timetable.ui.components.PillButton
import com.lyl.timetable.ui.components.RowItem
import com.lyl.timetable.ui.components.RowSeparator
import com.lyl.timetable.ui.components.Stepper
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType

/**
 * 作息时间编辑器。
 *
 * 支持两件事：
 *  1. 选择「每天几节课」——上午 / 下午 / 晚间分别设置节数；
 *  2. 指定「每节时长」与「课间休息」，一键生成全部节次的开始与结束时间；
 *     生成后仍可逐节微调（开始时间、结束时间、持续时间三者联动）。
 */
@Composable
fun ScheduleEditorScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.colors
    val template = settings.scheduleTemplate
    var editingSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var editingBreakAfter by remember { mutableStateOf<Int?>(null) }
    var confirmGenerate by remember { mutableStateOf(false) }

    fun updateTemplate(block: (ScheduleTemplate) -> ScheduleTemplate) {
        onSettingsChange(settings.copy(scheduleTemplate = block(template)))
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---------------- 顶部导航 ----------------
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
                Text("作息时间", style = AppType.Title2, color = p.textPrimary)
                Text(
                    "共 ${settings.timeSlots.size} 节 · 每节 ${template.lessonMinutes} 分钟",
                    style = AppType.Footnote,
                    color = p.textSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                top = 14.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.GroupGap)
        ) {
            // ---------------- 每天节数 ----------------
            item {
                GroupSection(
                    title = "作息参数",
                    footer = "合计 ${template.totalCount} 节；设为 0 即不排该时段。" +
                            "单节时长与课间是生成时间表时使用的默认值，个别节次可在下方「当前作息」中单独调整。"
                ) {
                    RowItem(
                        title = "上午",
                        value = "${template.morningCount} 节",
                        trailing = {
                            Stepper(
                                value = template.morningCount,
                                onValueChange = { v -> updateTemplate { it.copy(morningCount = v) } },
                                range = 0..12
                            )
                        }
                    )
                    RowSeparator()
                    RowItem(
                        title = "下午",
                        value = "${template.afternoonCount} 节",
                        trailing = {
                            Stepper(
                                value = template.afternoonCount,
                                onValueChange = { v -> updateTemplate { it.copy(afternoonCount = v) } },
                                range = 0..12
                            )
                        }
                    )
                    RowSeparator()
                    RowItem(
                        title = "晚间",
                        value = "${template.eveningCount} 节",
                        trailing = {
                            Stepper(
                                value = template.eveningCount,
                                onValueChange = { v -> updateTemplate { it.copy(eveningCount = v) } },
                                range = 0..12
                            )
                        }
                    )
                    RowSeparator()
                    RowItem(
                        title = "默认单节时长",
                        value = "${template.lessonMinutes} 分钟",
                        trailing = {
                            Stepper(
                                value = template.lessonMinutes,
                                onValueChange = { v -> updateTemplate { it.copy(lessonMinutes = v) } },
                                range = 20..180,
                                step = 5
                            )
                        }
                    )
                    RowSeparator()
                    RowItem(
                        title = "默认课间",
                        value = "${template.breakMinutes} 分钟",
                        trailing = {
                            Stepper(
                                value = template.breakMinutes,
                                onValueChange = { v -> updateTemplate { it.copy(breakMinutes = v) } },
                                range = 0..60,
                                step = 5,
                                label = "默认课间时长"
                            )
                        }
                    )
                }
            }

            // ---------------- 各段开始时间 ----------------
            item {
                GroupSection(
                    title = "各时段开始时间",
                    footer = "上午从第 1 节开始，下午与晚间接续编号。"
                ) {
                    TimeRowItem(
                        title = "上午第一节",
                        value = template.morningStart,
                        onChange = { v -> updateTemplate { it.copy(morningStart = v) } }
                    )
                    RowSeparator()
                    TimeRowItem(
                        title = "下午第一节",
                        value = template.afternoonStart,
                        onChange = { v -> updateTemplate { it.copy(afternoonStart = v) } }
                    )
                    RowSeparator()
                    TimeRowItem(
                        title = "晚间第一节",
                        value = template.eveningStart,
                        onChange = { v -> updateTemplate { it.copy(eveningStart = v) } }
                    )
                }
            }

            // ---------------- 生成 ----------------
            item {
                Column {
                    PillButton(
                        text = "重新生成时间表",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = { confirmGenerate = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "将按上述参数重排全部 ${template.totalCount} 个节次的开始与结束时间，" +
                                "已有课程的上课时段不受影响。",
                        style = AppType.Footnote,
                        color = p.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            // ---------------- 当前作息 ----------------
            item {
                Text(
                    text = "当前作息",
                    style = AppType.Footnote,
                    color = p.textSecondary,
                    modifier = Modifier.padding(start = AppDimens.SeparatorInset, bottom = 8.dp)
                )
            }

            item {
                GroupSection(
                    footer = "点击某一节可单独调整开始时间、结束时间与时长；点击课间可单独调整该课间的长短。" +
                            "改动后其后的节次会自动顺延（跨时段时回到该时段的固定开始时间）。"
                ) {
                    val slots = settings.timeSlots
                    val total = slots.size
                    slots.forEachIndexed { index, slot ->
                        val minutes = TimeText.minutesBetween(slot.startTime, slot.endTime)
                            ?: template.lessonMinutesAt(slot.section)
                        RowItem(
                            title = "第 ${slot.section} 节",
                            subtitle = "${slot.startTime} – ${slot.endTime}",
                            value = "$minutes 分钟",
                            showChevron = true,
                            onClick = { editingSlot = slot }
                        )
                        // 最后一节之后没有课间，因此不显示
                        if (index != slots.lastIndex) {
                            RowSeparator(inset = AppDimens.SeparatorInset)
                            val gap = template.breakMinutesAfter(slot.section)
                            RowItem(
                                title = "课间",
                                subtitle = "第 ${slot.section} 节之后",
                                value = "$gap 分钟",
                                showChevron = true,
                                trailing = {
                                    if (template.breakOverrides.containsKey(slot.section)) {
                                        Text(
                                            text = "已自定义",
                                            style = AppType.Caption2,
                                            color = p.accent
                                        )
                                    }
                                },
                                onClick = { editingBreakAfter = slot.section }
                            )
                        }
                        if (index != slots.lastIndex) RowSeparator(inset = AppDimens.SeparatorInset)
                    }
                    if (total == 0) {
                        RowItem(
                            title = "暂无节次",
                            subtitle = "请先在上方设置每天节数并生成时间表",
                            centered = true
                        )
                    }
                }
            }
        }
    }

    // ---------------- 逐节编辑 ----------------
    editingSlot?.let { slot ->
        SlotEditDialog(
            slot = slot,
            onDismiss = { editingSlot = null },
            onConfirm = { start, end ->
                val minutes = TimeText.minutesBetween(start, end)
                    ?: template.lessonMinutesAt(slot.section)
                val newTemplate = template.copy(
                    lessonOverrides = template.lessonOverrides + (slot.section to minutes)
                )
                val withEdited = settings.timeSlots.map {
                    if (it.section == slot.section) it.copy(startTime = start, endTime = end) else it
                }
                onSettingsChange(
                    settings.copy(
                        scheduleTemplate = newTemplate,
                        // 该节的时长变化后，其后的节次自动顺延
                        timeSlots = newTemplate.reflow(withEdited, slot.section)
                    )
                )
                editingSlot = null
            }
        )
    }

    // ---------------- 课间编辑 ----------------
    editingBreakAfter?.let { afterSection ->
        StepperDialog(
            title = "第 $afterSection 节之后的课间",
            initial = template.breakMinutesAfter(afterSection),
            range = 0..120,
            suffix = " 分钟",
            step = 1,
            hint = "该时长决定第 ${afterSection + 1} 节的开始时间",
            onDismiss = { editingBreakAfter = null },
            onConfirm = { minutes ->
                val newTemplate = template.copy(
                    breakOverrides = template.breakOverrides + (afterSection to minutes)
                )
                onSettingsChange(
                    settings.copy(
                        scheduleTemplate = newTemplate,
                        // 课间变化后，从下一节起整体顺延
                        timeSlots = newTemplate.reflow(settings.timeSlots, afterSection + 1)
                    )
                )
                editingBreakAfter = null
            }
        )
    }

    // ---------------- 生成确认 ----------------
    if (confirmGenerate) {
        val preview = template.toTimeSlots()
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            title = { Text("重新生成时间表？") },
            text = {
                Text(
                    if (preview.isEmpty()) {
                        "当前设置为 0 节，生成后作息表将为空。"
                    } else {
                        "将生成 ${preview.size} 个节次：" +
                                "第 1 节 ${preview.first().startTime} 开始，" +
                                "第 ${preview.size} 节 ${preview.last().endTime} 结束。\n" +
                                "已有的节次时间会被覆盖，课程安排本身不变。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val slots = template.toTimeSlots()
                    onSettingsChange(
                        settings.copy(
                            timeSlots = slots,
                            sectionCount = slots.size.coerceAtLeast(1)
                        )
                    )
                    confirmGenerate = false
                }) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGenerate = false }) { Text("取消") }
            }
        )
    }
}

/** 可点击的时间行，弹出时间选择 */
@Composable
private fun TimeRowItem(
    title: String,
    value: String,
    onChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    RowItem(
        title = title,
        value = value,
        showChevron = true,
        onClick = { editing = true }
    )
    if (editing) {
        TimePickDialog(
            title = title,
            initial = value,
            onDismiss = { editing = false },
            onConfirm = {
                onChange(it)
                editing = false
            }
        )
    }
}

/** 时 / 分滚轮式选择（用步进器实现，避免额外依赖） */
@Composable
fun TimePickDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parsed = TimeText.parse(initial) ?: java.time.LocalTime.of(8, 0)
    var hour by remember { mutableStateOf(parsed.hour) }
    var minute by remember { mutableStateOf(parsed.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("时", style = AppType.Footnote, color = AppTheme.colors.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Stepper(value = hour, onValueChange = { hour = it }, range = 0..23)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("分", style = AppType.Footnote, color = AppTheme.colors.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Stepper(value = minute, onValueChange = { minute = it }, range = 0..55, step = 5)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "当前：%02d:%02d".format(hour, minute),
                    style = AppType.Headline,
                    color = AppTheme.accent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(hour, minute)) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 单节编辑：开始时间、结束时间、持续时间三者联动。
 * 调整开始时间时保持时长、整体平移；调整结束时间或时长时改写另一侧。
 */
@Composable
fun SlotEditDialog(
    slot: TimeSlot,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val p = AppTheme.colors
    var start by remember { mutableStateOf(slot.startTime) }
    var end by remember { mutableStateOf(slot.endTime) }

    val duration = TimeText.minutesBetween(start, end) ?: 0
    val startTime = TimeText.parse(start) ?: java.time.LocalTime.of(8, 0)
    val endTime = TimeText.parse(end) ?: java.time.LocalTime.of(8, 45)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 ${slot.section} 节") },
        text = {
            Column {
                Text("开始时间", style = AppType.Footnote, color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper(
                        value = startTime.hour,
                        onValueChange = { h ->
                            val newStart = "%02d:%02d".format(h, startTime.minute)
                            val kept = TimeText.minutesBetween(start, end) ?: 45
                            start = newStart
                            TimeText.plus(newStart, kept)?.let { end = it }
                        },
                        range = 0..23
                    )
                    Stepper(
                        value = startTime.minute,
                        onValueChange = { m ->
                            val newStart = "%02d:%02d".format(startTime.hour, m)
                            val kept = TimeText.minutesBetween(start, end) ?: 45
                            start = newStart
                            TimeText.plus(newStart, kept)?.let { end = it }
                        },
                        range = 0..55,
                        step = 5
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("结束时间", style = AppType.Footnote, color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper(
                        value = endTime.hour,
                        onValueChange = { h -> end = "%02d:%02d".format(h, endTime.minute) },
                        range = 0..23
                    )
                    Stepper(
                        value = endTime.minute,
                        onValueChange = { m -> end = "%02d:%02d".format(endTime.hour, m) },
                        range = 0..55,
                        step = 5
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("持续时间", style = AppType.Footnote, color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                Stepper(
                    value = duration,
                    onValueChange = { minutes ->
                        TimeText.plus(start, minutes)?.let { end = it }
                    },
                    range = 10..240,
                    step = 5,
                    suffix = " 分钟"
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "$start – $end（$duration 分钟）",
                    style = AppType.Headline,
                    color = AppTheme.accent
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(start, end) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
