package com.lyl.timetable.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
//  强调色：Material 3 tonal 色板中的 40 级（浅色）/ 80 级（深色）
//  仅在关闭动态取色时使用
// ---------------------------------------------------------------------------

@Immutable
data class AccentOption(val name: String, val color: Color, val darkColor: Color)

val AccentOptions: List<AccentOption> = listOf(
    AccentOption("蓝", Color(0xFF415F91), Color(0xFFAAC7FF)),
    AccentOption("紫", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    AccentOption("绿", Color(0xFF386A20), Color(0xFF9CD67D)),
    AccentOption("橙", Color(0xFF8B5000), Color(0xFFFFB77C)),
    AccentOption("粉", Color(0xFF8E4585), Color(0xFFFFB0C8)),
    AccentOption("青", Color(0xFF00696E), Color(0xFF80D4DA))
)

// ---------------------------------------------------------------------------
//  课程配色：M3 tonal 风格 —— container 色作底、on-container 色作字
// ---------------------------------------------------------------------------

@Immutable
data class CourseColorSet(
    val lightContainer: Color,
    val lightOn: Color,
    val darkContainer: Color,
    val darkOn: Color
) {
    fun background(isLight: Boolean): Color = if (isLight) lightContainer else darkContainer
    fun foreground(isLight: Boolean): Color = if (isLight) lightOn else darkOn
    /** 用于色条、圆点等需要饱和色的场景 */
    fun accent(isLight: Boolean): Color = if (isLight) lightOn else darkOn
}

val CourseColors: List<CourseColorSet> = listOf(
    CourseColorSet(Color(0xFFD6E3FF), Color(0xFF001B3E), Color(0xFF23405F), Color(0xFFD6E3FF)), // 蓝
    CourseColorSet(Color(0xFFE8DEF8), Color(0xFF211A35), Color(0xFF44345F), Color(0xFFE8DEF8)), // 紫
    CourseColorSet(Color(0xFFFFD8E4), Color(0xFF31101D), Color(0xFF5E2A3E), Color(0xFFFFD8E4)), // 粉
    CourseColorSet(Color(0xFFFFDCC2), Color(0xFF2E1500), Color(0xFF5E3512), Color(0xFFFFDCC2)), // 橙
    CourseColorSet(Color(0xFFF7E6A8), Color(0xFF241A00), Color(0xFF514400), Color(0xFFF7E6A8)), // 黄
    CourseColorSet(Color(0xFFC8E6C9), Color(0xFF0A2E12), Color(0xFF214A2A), Color(0xFFC8E6C9)), // 绿
    CourseColorSet(Color(0xFFC2E7E9), Color(0xFF00201F), Color(0xFF10454A), Color(0xFFC2E7E9)), // 青
    CourseColorSet(Color(0xFFB8EAD1), Color(0xFF00210F), Color(0xFF174733), Color(0xFFB8EAD1)), // 薄荷
    CourseColorSet(Color(0xFFDDE1FF), Color(0xFF10175C), Color(0xFF2E3574), Color(0xFFDDE1FF)), // 靛
    CourseColorSet(Color(0xFFFFDAD6), Color(0xFF410002), Color(0xFF6A1E17), Color(0xFFFFDAD6)), // 红
    CourseColorSet(Color(0xFFEADDCB), Color(0xFF2B1F0F), Color(0xFF4C3B29), Color(0xFFEADDCB)), // 棕
    CourseColorSet(Color(0xFFE2E2E9), Color(0xFF1A1B21), Color(0xFF3A3B43), Color(0xFFE2E2E9))  // 灰
)

fun courseColor(index: Int): CourseColorSet =
    CourseColors[((index % CourseColors.size) + CourseColors.size) % CourseColors.size]

// ---------------------------------------------------------------------------
//  语义色板：由 Material 3 的 ColorScheme 派生，供页面按语义取色
// ---------------------------------------------------------------------------

@Immutable
data class AppPalette(
    val isLight: Boolean,
    val background: Color,
    val backgroundSecondary: Color,
    val card: Color,
    val cardElevated: Color,
    val fill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val gridLine: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val onAccentContainer: Color,
    val danger: Color,
    val scrim: Color
) {
    fun accentSoft(alpha: Float = if (isLight) 0.12f else 0.22f): Color = accent.copy(alpha = alpha)
}
