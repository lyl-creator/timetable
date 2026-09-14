package com.lyl.timetable.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.TimetableRepository
import com.lyl.timetable.ui.screens.CourseEditorSheet
import com.lyl.timetable.ui.screens.ImportScreen
import com.lyl.timetable.ui.screens.SettingsScreen
import com.lyl.timetable.ui.screens.TodayScreen
import com.lyl.timetable.ui.screens.WeekScreen
import com.lyl.timetable.ui.theme.OriginTheme

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
    var editorRequest by remember { mutableStateOf<EditorRequest?>(null) }

    // 开学日期或手动指定周次变化后，重新对齐当前周
    LaunchedEffect(settings.termStartDate, settings.currentWeekOverride) {
        week = repository.currentWeek()
    }

    val autoWeek = repository.currentWeek()

    OriginTheme(themeMode = settings.themeMode, accentIndex = settings.accentIndex) {
        val palette = OriginTheme.colors

        Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                if (showImport) {
                    ImportScreen(
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
                } else {
                    Crossfade(
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
                                week = week,
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
                                onEditCourse = { editorRequest = EditorRequest(it) }
                            )
                        }
                    }

                    FloatingBottomBar(
                        current = tab,
                        onSelect = { tab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 14.dp)
                    )
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
                    if (course.id == 0L) {
                        repository.addCourse(course)
                    } else {
                        repository.updateCourse(course)
                    }
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
