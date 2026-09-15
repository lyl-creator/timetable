package com.lyl.timetable.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import com.lyl.timetable.data.TimetableRepository
import com.lyl.timetable.ui.components.ColorDot
import com.lyl.timetable.ui.components.GlassIconButton
import com.lyl.timetable.ui.components.GroupSection
import com.lyl.timetable.ui.components.LargeTitleHeader
import com.lyl.timetable.ui.components.RowItem
import com.lyl.timetable.ui.components.RowSeparator
import com.lyl.timetable.ui.components.SegmentedPicker
import com.lyl.timetable.ui.components.Stepper
import com.lyl.timetable.ui.components.AppSwitch
import com.lyl.timetable.ui.theme.AccentOptions
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
import com.lyl.timetable.ui.theme.courseColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lyl.timetable.BuildConfig
import com.lyl.timetable.data.TimeText
import com.lyl.timetable.reminder.ReminderPermissions
import com.lyl.timetable.reminder.ReminderPlanner
import com.lyl.timetable.updater.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    courses: List<Course>,
    onSettingsChange: (AppSettings) -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
    onEditCourse: (Course) -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCourseList by remember { mutableStateOf(false) }

    // 授权状态在每次回到前台时重新读取：用户可能刚在系统设置里补授了权限
    var permissionTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    val notificationsGranted = remember(permissionTick, context) {
        ReminderPermissions.notificationsGranted(context)
    }
    val exactAlarmAllowed = remember(permissionTick, context) {
        ReminderPermissions.exactAlarmAllowed(context)
    }
    val batteryRestricted = remember(permissionTick, context) {
        ReminderPermissions.batteryRestricted(context)
    }

    val courseNames = remember(courses) { courses.map { it.name }.distinct() }
    val scheduleSummary = remember(settings) {
        "${settings.timeSlots.size} 节 · 每节 ${settings.scheduleTemplate.lessonMinutes} 分钟"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenPadding),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.GroupGap)
    ) {
        item {
            LargeTitleHeader(title = "设置", subtitle = "数据全部保存在本机，应用不联网")
        }

        // ---------------- 学期 ----------------
        item {
            GroupSection(
                title = "学期",
                footer = "当前周按开学日期自动推算，也可手动指定。"
            ) {
                RowItem(
                    title = "开学第一周周一",
                    value = settings.termStartDate.ifBlank { "未设置" },
                    showChevron = true,
                    onClick = { showDatePicker = true }
                )
                RowSeparator()
                RowItem(
                    title = "学期总周数",
                    subtitle = "决定周次选择范围",
                    trailing = {
                        Stepper(
                            value = settings.totalWeeks,
                            onValueChange = { onSettingsChange(settings.copy(totalWeeks = it)) },
                            range = 4..31,
                            suffix = " 周",
                            label = "学期总周数"
                        )
                    }
                )
                RowSeparator()
                RowItem(
                    title = "当前周",
                    value = if (settings.currentWeekOverride > 0) {
                        "手动 · 第 ${settings.currentWeekOverride} 周"
                    } else {
                        "自动推算"
                    },
                    onClick = {
                        val next = if (settings.currentWeekOverride >= settings.totalWeeks) 0
                        else settings.currentWeekOverride + 1
                        onSettingsChange(settings.copy(currentWeekOverride = next))
                    }
                )
            }
        }

        // ---------------- 上课时间 ----------------
        item {
            GroupSection(
                title = "上课时间",
                footer = "可设置每天节数、每节时长与课间休息，并逐节微调。"
            ) {
                RowItem(
                    title = "作息与节次",
                    value = scheduleSummary,
                    showChevron = true,
                    onClick = onOpenSchedule
                )
            }
        }

        // ---------------- 课程提醒 ----------------
        item {
            val lead = settings.effectiveReminderLead
            val termReady = settings.termStartDate.isNotBlank()
            val upcomingToday = remember(courses, settings, permissionTick) {
                if (!settings.canScheduleReminder) {
                    emptyList()
                } else {
                    ReminderPlanner.previewForToday(courses, settings, LocalDate.now())
                        .filter { it.triggerAt.isAfter(java.time.LocalDateTime.now()) }
                }
            }
            GroupSection(
                title = "课程提醒",
                footer = "提醒由系统闹钟在本机触发，不依赖应用常驻后台：清理后台、划掉最近任务后仍会照常提醒，" +
                        "重启手机也会自动重排。若在系统设置里「强行停止」过本应用，需重新打开一次才能恢复。" +
                        "相邻的同一门课（如 1-2 节与 3-4 节连堂）只提醒一次。"
            ) {
                RowItem(
                    title = "上课提醒",
                    subtitle = if (termReady) {
                        "按课表在课前提醒，已预排未来两周"
                    } else {
                        "需先在「学期」中设置开学第一周周一"
                    },
                    trailing = {
                        AppSwitch(
                            checked = settings.reminderEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(reminderEnabled = it)) }
                        )
                    }
                )
                if (settings.reminderEnabled) {
                    RowSeparator()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("提前时间", style = AppType.Body, color = p.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        SegmentedPicker(
                            options = AppSettings.REMINDER_LEADS,
                            selected = lead,
                            onSelect = { onSettingsChange(settings.copy(reminderLeadMinutes = it)) },
                            label = { "$it 分钟" },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                !termReady -> "设置开学日期后即可自动排布提醒"
                                upcomingToday.isEmpty() -> "今天已无待提醒的课程"
                                else -> {
                                    val first = upcomingToday.first()
                                    "今天还有 ${upcomingToday.size} 次提醒 · " +
                                            "最近一次 ${TimeText.format(first.triggerAt.toLocalTime())} " +
                                            first.courseName
                                }
                            },
                            style = AppType.Caption1,
                            color = p.textSecondary
                        )
                    }

                    if (!notificationsGranted) {
                        RowSeparator()
                        RowItem(
                            title = "允许发送通知",
                            subtitle = "系统未授权，到点也不会有任何提示",
                            showChevron = true,
                            onClick = {
                                if (ReminderPermissions.needsNotificationRequest()) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    launchSettings(context, ReminderPermissions.notificationSettingsIntent(context))
                                }
                            }
                        )
                    }
                    if (!exactAlarmAllowed) {
                        RowSeparator()
                        RowItem(
                            title = "允许精确提醒",
                            subtitle = "未授权时可能晚几分钟，但仍会提醒",
                            showChevron = true,
                            onClick = {
                                launchSettings(context, ReminderPermissions.exactAlarmSettingsIntent(context))
                            }
                        )
                    }
                    if (batteryRestricted) {
                        RowSeparator()
                        RowItem(
                            title = "关闭电池优化",
                            subtitle = "加入白名单可避免后台被系统限制",
                            showChevron = true,
                            onClick = {
                                launchSettings(context, ReminderPermissions.batteryOptimizationIntent(context))
                            }
                        )
                    }
                }
            }
        }

        // ---------------- 外观 ----------------
        item {
            GroupSection(title = "外观") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("主题", style = AppType.Body, color = p.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    SegmentedPicker(
                        options = ThemeMode.entries.toList(),
                        selected = settings.themeMode,
                        onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RowSeparator()
                RowItem(
                    title = "系统取色",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "跟随壁纸自动生成配色（Material You）"
                    } else {
                        "需要 Android 12 及以上"
                    },
                    trailing = {
                        AppSwitch(
                            checked = settings.useDynamicColor &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onCheckedChange = { onSettingsChange(settings.copy(useDynamicColor = it)) }
                        )
                    }
                )
                RowSeparator()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("强调色", style = AppType.Body, color = p.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentOptions.forEachIndexed { index, option ->
                            val selected = settings.accentIndex == index
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(option.color)
                                    .then(
                                        if (selected) Modifier.border(2.5.dp, p.textPrimary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSettingsChange(settings.copy(accentIndex = index)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = AccentOptions[settings.accentIndex.coerceIn(0, AccentOptions.size - 1)].name,
                        style = AppType.Caption1,
                        color = p.textSecondary
                    )
                }
                RowSeparator()
                RowItem(
                    title = "显示周末",
                    subtitle = "关闭后课表仅显示周一至周五",
                    trailing = {
                        AppSwitch(
                            checked = settings.showWeekend,
                            onCheckedChange = { onSettingsChange(settings.copy(showWeekend = it)) }
                        )
                    }
                )
            }
        }

        // ---------------- 课程 ----------------
        item {
            GroupSection(title = "课程") {
                RowItem(
                    title = "全部课程与时段",
                    value = "${courseNames.size} 门 / ${courses.size} 个时段",
                    showChevron = true,
                    onClick = { showCourseList = true }
                )
            }
        }

        // ---------------- 数据 ----------------
        item {
            GroupSection(
                title = "数据",
                footer = "支持 Excel 97-2003 (.xls)、.xlsx、网页表格伪 xls 与 csv。"
            ) {
                RowItem(
                    title = "导入课表文件",
                    leading = {
                        Icon(
                            Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = p.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    showChevron = true,
                    onClick = onImport
                )
                RowSeparator()
                RowItem(
                    title = "清空全部课程",
                    titleColor = p.danger,
                    leading = {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = p.danger,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    onClick = { showClearConfirm = true }
                )
            }
        }

        // ---------------- 关于 ----------------
        item {
            GroupSection(
                title = "关于",
                footer = "检查更新只读取 GitHub 上的版本号与下载地址，课程数据始终保存在本机。"
            ) {
                RowItem(title = "应用", value = "课程表")
                RowSeparator()
                RowItem(title = "版本", value = BuildConfig.VERSION_NAME)
                RowSeparator()
                RowItem(
                    title = "检查更新",
                    value = if (checking) "正在检查…" else null,
                    showChevron = true,
                    trailing = {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = p.accent
                            )
                        }
                    },
                    onClick = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                updateResult = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                checking = false
                            }
                        }
                    }
                )
            }
        }
    }

    // ---------------- 日期选择 ----------------
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
                        onSettingsChange(settings.copy(termStartDate = TimetableRepository.mondayOf(date).toString()))
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

    // ---------------- 清空确认 ----------------
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部课程？") },
            text = { Text("该操作会删除本机保存的所有课程记录，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) { Text("清空", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    // ---------------- 课程列表 ----------------
    if (showCourseList) {
        CourseListSheet(
            courses = courses,
            onDismiss = { showCourseList = false },
            onEdit = {
                showCourseList = false
                onEditCourse(it)
            }
        )
    }

    // ---------------- 更新检查结果 ----------------
    updateResult?.let { result ->
        when (result) {
            is UpdateChecker.Result.Available -> AlertDialog(
                onDismissRequest = { updateResult = null },
                title = { Text("发现新版本 ${result.info.version}") },
                text = {
                    Column {
                        Text(
                            result.info.releaseName,
                            style = AppType.Headline,
                            color = p.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result.info.notes.take(600).ifBlank { "包含若干改进与修复。" },
                            style = AppType.Footnote,
                            color = p.textSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "当前版本 ${BuildConfig.VERSION_NAME}",
                            style = AppType.Caption1,
                            color = p.textTertiary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        openUrl(context, result.info.apkUrl ?: result.info.releaseUrl)
                        updateResult = null
                    }) { Text("前往下载") }
                },
                dismissButton = {
                    TextButton(onClick = { updateResult = null }) { Text("稍后") }
                }
            )

            UpdateChecker.Result.UpToDate -> AlertDialog(
                onDismissRequest = { updateResult = null },
                title = { Text("已是最新版本") },
                text = { Text("当前版本 ${BuildConfig.VERSION_NAME}，未发现更新。") },
                confirmButton = {
                    TextButton(onClick = { updateResult = null }) { Text("好") }
                }
            )

            is UpdateChecker.Result.Failed -> AlertDialog(
                onDismissRequest = { updateResult = null },
                title = { Text("检查更新失败") },
                text = { Text(result.message) },
                confirmButton = {
                    TextButton(onClick = {
                        openUrl(context, UpdateChecker.RELEASES_PAGE)
                        updateResult = null
                    }) { Text("打开 Release 页面") }
                },
                dismissButton = {
                    TextButton(onClick = { updateResult = null }) { Text("关闭") }
                }
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 打开系统设置页面（授权引导）；个别 ROM 缺少对应入口时静默忽略 */
private fun launchSettings(context: android.content.Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun CourseListSheet(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onEdit: (Course) -> Unit
) {
    val p = AppTheme.colors
    val grouped = remember(courses) {
        courses.groupBy { it.name }.toList().sortedBy { it.first }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("全部课程", style = AppType.Title2, color = p.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "共 ${grouped.size} 门课 · ${courses.size} 个时段",
                            style = AppType.Footnote,
                            color = p.textSecondary
                        )
                    }
                    GlassIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        onClick = onDismiss,
                        size = 36.dp
                    )
                }

                if (courses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "课表为空，可先导入或手动添加",
                            style = AppType.Subheadline,
                            color = p.textTertiary
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
                                    ColorDot(
                                        color = courseColor(slots.first().colorIndex).accent(p.isLight),
                                        size = 9.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        name,
                                        style = AppType.Headline,
                                        color = p.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (slots.size > 1) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "${slots.size} 个时段",
                                            style = AppType.Caption1,
                                            color = p.accent
                                        )
                                    }
                                }
                            }
                            items(slots.size) { index ->
                                val course = slots[index]
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppDimens.InnerRadius))
                                        .background(p.fill)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onEdit(course) }
                                        .padding(14.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = course.scheduleText,
                                            style = AppType.Body,
                                            color = p.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = listOfNotNull(
                                                course.weekText,
                                                course.location.takeIf { it.isNotBlank() },
                                                course.teacher.takeIf { it.isNotBlank() }
                                            ).joinToString("  ·  ").ifBlank { "未填写其它信息" },
                                            style = AppType.Footnote,
                                            color = p.textSecondary
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
