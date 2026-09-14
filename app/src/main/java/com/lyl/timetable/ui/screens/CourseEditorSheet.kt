package com.lyl.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.WeekParity
import com.lyl.timetable.ui.components.GradientButton
import com.lyl.timetable.ui.theme.CourseColors
import com.lyl.timetable.ui.theme.OriginTheme

private enum class WeekEditMode(val label: String) {
    RANGE("按区间"),
    CUSTOM("逐周勾选")
}

/**
 * 课程编辑抽屉。
 *
 * 周次支持两种编辑方式：
 *  · 按区间 —— 起止周 + 单双周（适用于绝大多数规律课程）
 *  · 逐周勾选 —— 任意周次组合（适用于每周安排不同、临时调整等情况）
 *
 * @param existing 为空表示新建
 * @param sameNameSlots 课表中已存在的同名课程时段数量，用于提示「多时段课程」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorSheet(
    existing: Course?,
    settings: AppSettings,
    sameNameSlots: Int = 0,
    defaultDay: Int = 1,
    defaultSection: Int = 1,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: ((Course) -> Unit)? = null
) {
    val c = OriginTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(existing?.teacher.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var day by remember { mutableStateOf(existing?.dayOfWeek ?: defaultDay) }
    var startSection by remember { mutableStateOf(existing?.startSection ?: defaultSection) }
    var endSection by remember { mutableStateOf(existing?.endSection ?: defaultSection) }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: 0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val existingWeeks = remember(existing) { existing?.activeWeeks.orEmpty() }
    val initialStart = existingWeeks.firstOrNull() ?: 1
    val initialEnd = existingWeeks.lastOrNull() ?: settings.totalWeeks
    val initialParity = remember(existing) { Course.detectParity(existingWeeks) }

    // 已有周次若能被「区间 + 单双周」完全还原，则默认区间模式，否则进入逐周模式
    val initiallySimple = remember(existing) {
        if (existing == null || existingWeeks.isEmpty()) true
        else Course.maskOf(initialStart, initialEnd, initialParity) == existing.weekMask
    }

    var mode by remember { mutableStateOf(if (initiallySimple) WeekEditMode.RANGE else WeekEditMode.CUSTOM) }
    var weekRange by remember { mutableStateOf(initialStart.toFloat()..initialEnd.toFloat()) }
    var parity by remember { mutableStateOf(initialParity) }
    var customWeeks by remember {
        mutableStateOf(
            if (existingWeeks.isNotEmpty()) existingWeeks.toSet()
            else (1..settings.totalWeeks).toSet()
        )
    }

    val sectionOptions = (1..maxOf(settings.sectionCount, 12)).toList()
    val canSave = name.isNotBlank() && when (mode) {
        WeekEditMode.RANGE -> true
        WeekEditMode.CUSTOM -> customWeeks.isNotEmpty()
    }

    val resolvedMask = when (mode) {
        WeekEditMode.RANGE ->
            Course.maskOf(weekRange.start.toInt(), weekRange.endInclusive.toInt(), parity)

        WeekEditMode.CUSTOM ->
            customWeeks.fold(0) { acc, w -> acc or (1 shl (w - 1)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.card,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.divider)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (existing == null) "新建课程" else "编辑课程",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary
                    )
                    if (existing != null && sameNameSlots > 1) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "「${existing.name}」在课表中共有 $sameNameSlots 个时段，本页仅编辑当前这一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary
                        )
                    }
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

            Spacer(Modifier.height(18.dp))

            FieldLabel("课程名称")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("例如 高等数学") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("任课教师")
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        placeholder = { Text("选填") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("上课地点")
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = { Text("选填") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            FieldLabel("星期")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..7).forEach { d ->
                    Chip(
                        text = Course.DAY_NAMES[d - 1],
                        selected = day == d,
                        onClick = { day = d }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            FieldLabel("节次（可跨多节）")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sectionOptions.forEach { s ->
                    Chip(
                        text = "$s",
                        selected = s in startSection..endSection,
                        onClick = {
                            when {
                                s < startSection -> startSection = s
                                s > endSection -> endSection = s
                                else -> Unit
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前：第 $startSection - $endSection 节" +
                        settings.timeRangeText(startSection, endSection)
                            .takeIf { it.isNotBlank() }
                            ?.let { "（$it）" }
                            .orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("上课周次")
                Spacer(Modifier.weight(1f))
                MiniSegmented(
                    options = WeekEditMode.entries,
                    selected = mode,
                    label = { it.label },
                    onSelect = { mode = it }
                )
            }

            when (mode) {
                WeekEditMode.RANGE -> {
                    Text(
                        text = "${weekRange.start.toInt()} - ${weekRange.endInclusive.toInt()} 周" +
                                if (parity != WeekParity.ALL) " · ${parity.label}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = OriginTheme.accentStart
                    )
                    RangeSlider(
                        value = weekRange,
                        onValueChange = { weekRange = it },
                        valueRange = 1f..settings.totalWeeks.toFloat(),
                        steps = (settings.totalWeeks - 2).coerceAtLeast(0)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekParity.entries.forEach { p ->
                            Chip(text = p.label, selected = parity == p, onClick = { parity = p })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "适用于规律课程；若每周安排不同，请切换到「逐周勾选」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary
                    )
                }

                WeekEditMode.CUSTOM -> {
                    Text(
                        text = "已选 ${customWeeks.size} 周" +
                                (if (customWeeks.isEmpty()) "" else "：${customWeeks.sorted().joinToString("、")}"),
                        style = MaterialTheme.typography.titleSmall,
                        color = OriginTheme.accentStart
                    )
                    Spacer(Modifier.height(10.dp))
                    WeekToggleGrid(
                        totalWeeks = settings.totalWeeks,
                        selected = customWeeks,
                        onToggle = { w ->
                            customWeeks = if (customWeeks.contains(w)) customWeeks - w else customWeeks + w
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallTextAction("全选") { customWeeks = (1..settings.totalWeeks).toSet() }
                        SmallTextAction("清空") { customWeeks = emptySet() }
                        SmallTextAction("仅单周") { customWeeks = (1..settings.totalWeeks).filter { it % 2 == 1 }.toSet() }
                        SmallTextAction("仅双周") { customWeeks = (1..settings.totalWeeks).filter { it % 2 == 0 }.toSet() }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            FieldLabel("卡片颜色")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CourseColors.forEachIndexed { index, set ->
                    val selected = colorIndex == index
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(set.background(c.isLight))
                            .then(if (selected) Modifier.border(3.dp, c.textPrimary, CircleShape) else Modifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { colorIndex = index }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            FieldLabel("备注")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("选填，如考核方式、周次说明等") },
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null && onDelete != null) {
                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.danger.copy(alpha = 0.10f))
                            .clickable { showDeleteConfirm = true }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Delete, "删除", tint = c.danger, modifier = Modifier.size(20.dp))
                    }
                }
                GradientButton(
                    text = if (existing == null) "添加到课表" else "保存修改",
                    onClick = {
                        onSave(
                            Course(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                teacher = teacher.trim(),
                                location = location.trim(),
                                dayOfWeek = day,
                                startSection = startSection,
                                endSection = endSection,
                                weekMask = resolvedMask,
                                colorIndex = colorIndex,
                                note = note.trim()
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDeleteConfirm && existing != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这个时段？") },
            text = {
                Text(
                    if (sameNameSlots > 1) {
                        "将删除「${existing.name}」的当前时段（${existing.scheduleText}），该课程的其它时段不受影响。"
                    } else {
                        "「${existing.name}」将从课表中移除。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(existing)
                }) { Text("删除", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekToggleGrid(
    totalWeeks: Int,
    selected: Set<Int>,
    onToggle: (Int) -> Unit
) {
    val perRow = 7
    val rows = (1..totalWeeks).chunked(perRow)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { w ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (selected.contains(w)) OriginTheme.accentStart.copy(alpha = 0.16f)
                                else OriginTheme.colors.cardElevated
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onToggle(w) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$w",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected.contains(w)) OriginTheme.accentStart else OriginTheme.colors.textSecondary,
                            fontWeight = if (selected.contains(w)) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                repeat(perRow - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SmallTextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(OriginTheme.colors.cardElevated)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = OriginTheme.colors.textSecondary
        )
    }
}

@Composable
private fun <T> MiniSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val c = OriginTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.cardElevated)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) c.accent.copy(alpha = 0.16f) else c.cardElevated)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) OriginTheme.accentStart else c.textSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = OriginTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 7.dp, start = 2.dp)
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OriginTheme.colors
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) c.accentSoft(if (c.isLight) 0.14f else 0.24f) else c.cardElevated)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OriginTheme.accentStart else c.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
