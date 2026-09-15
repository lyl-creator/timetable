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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import com.lyl.timetable.ui.components.ChoiceChip
import com.lyl.timetable.ui.components.GlassTextField
import com.lyl.timetable.ui.components.PillButton
import com.lyl.timetable.ui.components.PillStyle
import com.lyl.timetable.ui.components.SegmentedPicker
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
import com.lyl.timetable.ui.theme.CourseColors

private enum class WeekEditMode(val label: String) {
    RANGE("按区间"),
    CUSTOM("逐周")
}

/**
 * 课程编辑抽屉（iOS sheet 形态）。
 *
 * 周次支持两种方式：按区间（起止周 + 单双周）与逐周勾选（任意周次组合）。
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
    val p = AppTheme.colors
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

    /**
     * 点击节次：未选中则并入当前区间，已选中则移出区间。
     * 取消端点即收缩区间；取消中间节时收缩到前半段（保证节次始终连续）。
     */
    fun toggleSection(section: Int) {
        val count = endSection - startSection + 1
        when {
            section in startSection..endSection -> {
                if (count <= 1) return
                when (section) {
                    startSection -> startSection += 1
                    endSection -> endSection -= 1
                    else -> endSection = section - 1
                }
            }

            section < startSection -> startSection = section
            else -> endSection = section
        }
    }

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
        containerColor = p.card,
        scrimColor = p.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(p.separator)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (existing == null) "新建课程" else "编辑课程",
                        style = AppType.Title2,
                        color = p.textPrimary
                    )
                    if (existing != null && sameNameSlots > 1) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "「${existing.name}」共有 $sameNameSlots 个时段，这里只编辑当前一个",
                            style = AppType.Footnote,
                            color = p.textSecondary
                        )
                    }
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(p.fill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, "关闭", tint = p.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            FieldLabel("课程名称")
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "例如 高等数学",
                imeAction = ImeAction.Next
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("任课教师")
                    GlassTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        placeholder = "选填",
                        imeAction = ImeAction.Next
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("上课地点")
                    GlassTextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = "选填",
                        imeAction = ImeAction.Next
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
                    ChoiceChip(
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
                    ChoiceChip(
                        text = s.toString(),
                        selected = s in startSection..endSection,
                        onClick = { toggleSection(s) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "第 $startSection – $endSection 节" +
                        settings.timeRangeText(startSection, endSection)
                            .takeIf { it.isNotBlank() }
                            ?.let { "（$it）" }
                            .orEmpty(),
                style = AppType.Footnote,
                color = p.textSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "点击选择节次，再次点击即可取消；节次需保持连续",
                style = AppType.Caption1,
                color = p.textTertiary
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("上课周次")
                Spacer(Modifier.weight(1f))
                SegmentedPicker(
                    options = WeekEditMode.entries.toList(),
                    selected = mode,
                    onSelect = { mode = it },
                    label = { it.label },
                    modifier = Modifier.width(170.dp).height(36.dp)
                )
            }

            when (mode) {
                WeekEditMode.RANGE -> {
                    Text(
                        text = "${weekRange.start.toInt()} – ${weekRange.endInclusive.toInt()} 周" +
                                if (parity != WeekParity.ALL) " · ${parity.label}" else "",
                        style = AppType.Headline,
                        color = p.accent
                    )
                    RangeSlider(
                        value = weekRange,
                        onValueChange = { weekRange = it },
                        valueRange = 1f..settings.totalWeeks.toFloat(),
                        steps = (settings.totalWeeks - 2).coerceAtLeast(0)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekParity.entries.forEach { wp ->
                            ChoiceChip(
                                text = wp.label,
                                selected = parity == wp,
                                onClick = { parity = wp }
                            )
                        }
                    }
                }

                WeekEditMode.CUSTOM -> {
                    Text(
                        text = "已选 ${customWeeks.size} 周" +
                                if (customWeeks.isEmpty()) "" else "：${customWeeks.sorted().joinToString("、")}",
                        style = AppType.Headline,
                        color = p.accent
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("全选") { customWeeks = (1..settings.totalWeeks).toSet() }
                        SmallAction("清空") { customWeeks = emptySet() }
                        SmallAction("单周") {
                            customWeeks = (1..settings.totalWeeks).filter { it % 2 == 1 }.toSet()
                        }
                        SmallAction("双周") {
                            customWeeks = (1..settings.totalWeeks).filter { it % 2 == 0 }.toSet()
                        }
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(set.accent(true))
                            .then(if (selected) Modifier.border(2.5.dp, p.textPrimary, CircleShape) else Modifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { colorIndex = index }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            FieldLabel("备注")
            GlassTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "选填，如考核方式、周次说明等",
                singleLine = false,
                minLines = 2
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null && onDelete != null) {
                    PillButton(
                        text = "删除",
                        onClick = { showDeleteConfirm = true },
                        style = PillStyle.Destructive,
                        icon = Icons.Rounded.Delete
                    )
                }
                PillButton(
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
                }) { Text("删除", color = p.danger) }
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
    val p = AppTheme.colors
    val perRow = 7
    val rows = (1..totalWeeks).chunked(perRow)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { w ->
                    val on = selected.contains(w)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(AppDimens.InnerRadius))
                            .background(if (on) p.accent else p.fill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onToggle(w) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = w.toString(),
                            style = AppType.Subheadline,
                            color = if (on) p.onAccent else p.textPrimary,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
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
private fun SmallAction(text: String, onClick: () -> Unit) {
    val p = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.Capsule))
            .background(p.fill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = text, style = AppType.Footnote, color = p.textPrimary)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = AppType.Footnote,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 7.dp, start = 2.dp)
    )
}
