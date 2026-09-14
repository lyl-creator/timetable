package com.lyl.timetable.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ---------------------------------------------------------------------------
//  iOS 系统色（浅色 / 深色两套，取自 Apple Human Interface Guidelines）
// ---------------------------------------------------------------------------

object SystemColors {
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF30D158)
    val Indigo = Color(0xFF5856D6)
    val IndigoDark = Color(0xFF5E5CE6)
    val Orange = Color(0xFFFF9500)
    val OrangeDark = Color(0xFFFF9F0A)
    val Pink = Color(0xFFFF2D55)
    val PinkDark = Color(0xFFFF375F)
    val Purple = Color(0xFFAF52DE)
    val PurpleDark = Color(0xFFBF5AF2)
    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)
    val Teal = Color(0xFF5AC8FA)
    val TealDark = Color(0xFF64D2FF)
    val Yellow = Color(0xFFFFCC00)
    val YellowDark = Color(0xFFFFD60A)
    val Mint = Color(0xFF00C7BE)
    val MintDark = Color(0xFF63E6E2)
    val Brown = Color(0xFFA2845E)
    val BrownDark = Color(0xFFAC8E68)
    val Gray = Color(0xFF8E8E93)
    val GrayDark = Color(0xFF98989D)
}

// ---------------------------------------------------------------------------
//  强调色（保留可切换能力，改为 iOS 系统色）
// ---------------------------------------------------------------------------

@Immutable
data class AccentOption(val name: String, val color: Color, val darkColor: Color) {
    fun value(isLight: Boolean): Color = if (isLight) color else darkColor
}

val AccentOptions: List<AccentOption> = listOf(
    AccentOption("系统蓝", SystemColors.Blue, SystemColors.BlueDark),
    AccentOption("系统紫", SystemColors.Purple, SystemColors.PurpleDark),
    AccentOption("系统绿", SystemColors.Green, SystemColors.GreenDark),
    AccentOption("系统橙", SystemColors.Orange, SystemColors.OrangeDark),
    AccentOption("系统粉", SystemColors.Pink, SystemColors.PinkDark),
    AccentOption("系统靛", SystemColors.Indigo, SystemColors.IndigoDark)
)

// ---------------------------------------------------------------------------
//  课程配色：单一基色派生淡化底与强调前景，贴近 iOS 日历的观感
// ---------------------------------------------------------------------------

@Immutable
data class CourseColorSet(val base: Color) {
    fun background(isLight: Boolean): Color =
        if (isLight) lerp(base, Color.White, 0.84f) else lerp(base, Color.Black, 0.58f)

    fun foreground(isLight: Boolean): Color =
        if (isLight) lerp(base, Color.Black, 0.20f) else lerp(base, Color.White, 0.16f)

    /** 用于列表左侧色条、圆点等纯色场景 */
    fun accent(isLight: Boolean): Color =
        if (isLight) lerp(base, Color.Black, 0.08f) else lerp(base, Color.White, 0.08f)
}

val CourseColors: List<CourseColorSet> = listOf(
    CourseColorSet(SystemColors.Blue),
    CourseColorSet(SystemColors.Purple),
    CourseColorSet(SystemColors.Pink),
    CourseColorSet(SystemColors.Orange),
    CourseColorSet(SystemColors.Yellow),
    CourseColorSet(SystemColors.Green),
    CourseColorSet(SystemColors.Teal),
    CourseColorSet(SystemColors.Mint),
    CourseColorSet(SystemColors.Indigo),
    CourseColorSet(SystemColors.Red),
    CourseColorSet(SystemColors.Brown),
    CourseColorSet(SystemColors.Gray)
)

fun courseColor(index: Int): CourseColorSet =
    CourseColors[((index % CourseColors.size) + CourseColors.size) % CourseColors.size]

// ---------------------------------------------------------------------------
//  语义色板（含 Liquid Glass 材质参数）
// ---------------------------------------------------------------------------

@Immutable
data class AppPalette(
    val isLight: Boolean,

    /** 页面底色：iOS systemGroupedBackground */
    val background: Color,
    /** 页面底色的次级层次 */
    val backgroundSecondary: Color,

    /** 不透明卡片（分组列表容器） */
    val card: Color,
    /** 卡片之上的次级填充（分段控件槽、输入框底） */
    val fill: Color,

    /** 玻璃材质：半透明底 */
    val glass: Color,
    /** 玻璃材质：更高不透明度（浮层、Tab Bar） */
    val glassStrong: Color,
    /** 玻璃边缘高光（顶部最亮） */
    val glassHighlight: Color,
    /** 玻璃边缘暗部 */
    val glassEdge: Color,
    /** 玻璃上的内容着色 */
    val glassTint: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val gridLine: Color,

    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val scrim: Color
) {
    fun accentSoft(alpha: Float = if (isLight) 0.12f else 0.22f): Color = accent.copy(alpha = alpha)
}

val LightPalette = AppPalette(
    isLight = true,
    background = Color(0xFFF2F2F7),
    backgroundSecondary = Color(0xFFEDEDF3),
    card = Color(0xFFFFFFFF),
    fill = Color(0xFFE9E9EF),
    glass = Color(0xBFFFFFFF),          // 75% 白
    glassStrong = Color(0xDBFFFFFF),    // 86% 白
    glassHighlight = Color(0xB3FFFFFF),
    glassEdge = Color(0x73000000),
    glassTint = Color(0xFF000000),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    textTertiary = Color(0x4D3C3C43),
    separator = Color(0x493C3C43),
    gridLine = Color(0x223C3C43),
    accent = SystemColors.Blue,
    onAccent = Color(0xFFFFFFFF),
    danger = SystemColors.Red,
    scrim = Color(0x33000000)
)

val DarkPalette = AppPalette(
    isLight = false,
    background = Color(0xFF000000),
    backgroundSecondary = Color(0xFF0A0A0C),
    card = Color(0xFF1C1C1E),
    fill = Color(0xFF2C2C2E),
    glass = Color(0xB31C1C1E),          // 70% 深灰
    glassStrong = Color(0xD91C1C1E),
    glassHighlight = Color(0x2EFFFFFF),
    glassEdge = Color(0x14000000),
    glassTint = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    textTertiary = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    gridLine = Color(0x1FEBEBF5),
    accent = SystemColors.BlueDark,
    onAccent = Color(0xFFFFFFFF),
    danger = SystemColors.RedDark,
    scrim = Color(0x99000000)
)
