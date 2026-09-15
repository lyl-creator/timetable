package com.lyl.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.TimetableRepository
import com.lyl.timetable.ui.screens.CourseEditorSheet
import com.lyl.timetable.ui.screens.ImportScreen
import com.lyl.timetable.ui.screens.ScheduleEditorScreen
import com.lyl.timetable.ui.screens.SettingsScreen
import com.lyl.timetable.ui.screens.TodayScreen
import com.lyl.timetable.ui.screens.WeekScreen
import com.lyl.timetable.ui.theme.AppTheme

private data class EditorRequest(
    val course: Course?,
    val dayOfWeek: Int = 1,
    val section: Int = 1
)

@Composable
fun AppRoot(
    repository: TimetableRepository,
    incomingUri: String? = null
) {
    val settings by repository.settings.collectAsState()
    val courses by repository.courses.collectAsState()

    var tab by remember { mutableStateOf(AppTab.WEEK) }
    var week by remember { mutableStateOf(repository.currentWeek()) }
    var showImport by remember { mutableStateOf(incomingUri != null) }
    var importUri by remember { mutableStateOf(incomingUri) }
    var showSchedule by remember { mutableStateOf(false) }
    var editorRequest by remember { mutableStateOf<EditorRequest?>(null) }

    // 学期起始日期或手动指定周次变化后，重新对齐当前周
    LaunchedEffect(settings.termStartDate, settings.currentWeekOverride) {
        week = repository.currentWeek()
    }

    val autoWeek = repository.currentWeek()

    // 返回手势与返回键：先退出二级页面，其次回到课表页，最后才退出应用
    BackHandler(enabled = showImport || showSchedule || tab != AppTab.WEEK) {
        when {
            showImport -> {
                showImport = false
                importUri = null
            }

            showSchedule -> showSchedule = false

            else -> tab = AppTab.WEEK
        }
    }

    AppTheme(
        themeMode = settings.themeMode,
        accentIndex = settings.accentIndex,
        dynamicColor = settings.useDynamicColor
    ) {
        Scaffold(
            bottomBar = {
                if (!showImport && !showSchedule) {
                    FloatingTabBar(
                        current = tab,
                        onSelect = { tab = it }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    showImport -> ImportScreen(
                        initialUri = importUri,
                        onBack = {
                            showImport = false
                            importUri = null
                        },
                        onConfirmImport = { imported, replace ->
                            repository.importCourses(imported, replace)
                            showImport = false
                            importUri = null
                            week = repository.currentWeek()
                            tab = AppTab.WEEK
                        }
                    )

                    showSchedule -> ScheduleEditorScreen(
                        settings = settings,
                        onSettingsChange = { repository.updateSettings(it) },
                        onBack = { showSchedule = false }
                    )

                    else -> Crossfade(
                        targetState = tab,
                        animationSpec = tween(220),
                        label = "tab"
                    ) { current ->
                        when (current) {
                            AppTab.WEEK -> WeekScreen(
                                courses = courses,
                                settings = settings,
                                week = week,
                                autoWeek = autoWeek,
                                onWeekChange = { week = it },
                                onCourseClick = { editorRequest = EditorRequest(it) },
                                onEmptyAreaClick = { day, section ->
                                    editorRequest = EditorRequest(null, day, section)
                                },
                                onImport = {
                                    importUri = null
                                    showImport = true
                                }
                            )

                            AppTab.TODAY -> TodayScreen(
                                courses = courses,
                                settings = settings,
                                // 今日页固定显示「今天所在周」的课程，
                                // 不受课表页当前选中的周次影响
                                week = autoWeek,
                                onCourseClick = { editorRequest = EditorRequest(it) },
                                onAddCourse = { editorRequest = EditorRequest(null) }
                            )

                            AppTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                courses = courses,
                                onSettingsChange = { repository.updateSettings(it) },
                                onImport = {
                                    importUri = null
                                    showImport = true
                                },
                                onClearAll = { repository.clearCourses() },
                                onEditCourse = { editorRequest = EditorRequest(it) },
                                onOpenSchedule = { showSchedule = true }
                            )
                        }
                    }
                }
            }
        }

        editorRequest?.let { request ->
            val target = request.course
            CourseEditorSheet(
                existing = target,
                settings = settings,
                sameNameSlots = if (target == null) 0 else courses.count { it.name == target.name },
                defaultDay = request.dayOfWeek.coerceIn(1, 7),
                defaultSection = request.section.coerceIn(1, settings.sectionCount),
                onDismiss = { editorRequest = null },
                onSave = { course ->
                    if (course.id == 0L) repository.addCourse(course)
                    else repository.updateCourse(course)
                    editorRequest = null
                },
                onDelete = { course ->
                    repository.deleteCourse(course.id)
                    editorRequest = null
                }
            )
        }
    }
}
