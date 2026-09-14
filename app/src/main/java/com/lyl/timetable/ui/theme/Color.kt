package com.lyl.timetable.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
//  基础色板（OriginOS 取向：高明度、低饱和底 + 克制的中饱和前景）
// ---------------------------------------------------------------------------

val OriginBlue = Color(0xFF2E5BFF)
val OriginBlueEnd = Color(0xFF6A4CFF)
val OriginPurple = Color(0xFF7A4CFF)
val OriginGreen = Color(0xFF12A37A)
val OriginOrange = Color(0xFFFF7A38)
val OriginPink = Color(0xFFFF5C93)
val OriginGraphite = Color(0xFF4A4F5C)

/** 强调色候选（对应 AppSettings.ACCENTS） */
val AccentPalette: List<AccentPair> = listOf(
    AccentPair("星海蓝", Color(0xFF2E5BFF), Color(0xFF6A4CFF)),
    AccentPair("晨曦紫", Color(0xFF7A4CFF), Color(0xFFB44CFF)),
    AccentPair("青竹绿", Color(0xFF12A37A), Color(0xFF3FCF9C)),
    AccentPair("落日橙", Color(0xFFFF7A38), Color(0xFFFFB03A)),
    AccentPair("樱粉", Color(0xFFFF5C93), Color(0xFFFF8FB8)),
    AccentPair("午夜灰", Color(0xFF4A4F5C), Color(0xFF7B8194))
)

@Immutable
data class AccentPair(val name: String, val start: Color, val end: Color)

// ---------------------------------------------------------------------------
//  课程卡片配色：浅色模式为浅底深字，深色模式为深底浅字
// ---------------------------------------------------------------------------

@Immutable
data class CourseColorSet(
    val lightBg: Color,
    val lightFg: Color,
    val darkBg: Color,
    val darkFg: Color
) {
    fun background(isLight: Boolean): Color = if (isLight) lightBg else darkBg
    fun foreground(isLight: Boolean): Color = if (isLight) lightFg else darkFg
}

val CourseColors: List<CourseColorSet> = listOf(
    CourseColorSet(Color(0xFFE8EDFF), Color(0xFF2A4BC0), Color(0xFF1B2350), Color(0xFFA8BCFF)), // 蓝
    CourseColorSet(Color(0xFFEFE9FF), Color(0xFF5B3BC4), Color(0xFF241C47), Color(0xFFBFA8FF)), // 紫
    CourseColorSet(Color(0xFFE0F4F3), Color(0xFF12766C), Color(0xFF12302F), Color(0xFF7FD8CD)), // 青
    CourseColorSet(Color(0xFFFFEDE0), Color(0xFFB2591A), Color(0xFF3A2413), Color(0xFFFFB784)), // 橙
    CourseColorSet(Color(0xFFFFE7EF), Color(0xFFB02F63), Color(0xFF3A1524), Color(0xFFFF9BBD)), // 粉
    CourseColorSet(Color(0xFFE6F5E2), Color(0xFF2C7A28), Color(0xFF16301A), Color(0xFF96DA8F)), // 绿
    CourseColorSet(Color(0xFFFFF4D6), Color(0xFF8A6410), Color(0xFF33290D), Color(0xFFF2D07A)), // 黄
    CourseColorSet(Color(0xFFE8EEF5), Color(0xFF3F5A78), Color(0xFF1C2733), Color(0xFF9FBCD8)), // 灰蓝
    CourseColorSet(Color(0xFFE7EAFB), Color(0xFF3A3F9E), Color(0xFF1B1E42), Color(0xFFA9AEF5)), // 靛
    CourseColorSet(Color(0xFFFFE8E5), Color(0xFFB33A2E), Color(0xFF3A1A16), Color(0xFFFFA79B)), // 红
    CourseColorSet(Color(0xFFE4F6EC), Color(0xFF1F7A4A), Color(0xFF13301F), Color(0xFF8ADCAF)), // 薄荷
    CourseColorSet(Color(0xFFF2EAE3), Color(0xFF7A5238), Color(0xFF2E241D), Color(0xFFD8B79A))  // 可可
)

fun courseColor(index: Int): CourseColorSet = CourseColors[((index % CourseColors.size) + CourseColors.size) % CourseColors.size]

// ---------------------------------------------------------------------------
//  语义色板
// ---------------------------------------------------------------------------

@Immutable
data class OriginPalette(
    val isLight: Boolean,
    val background: Color,
    val backgroundElevated: Color,
    val card: Color,
    val cardElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val gridLine: Color,
    val accent: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val danger: Color,
    val scrim: Color
) {
    fun accentSoft(alpha: Float = if (isLight) 0.10f else 0.18f): Color = accent.copy(alpha = alpha)
}

val LightPalette = OriginPalette(
    isLight = true,
    background = Color(0xFFF2F3F5),
    backgroundElevated = Color(0xFFF7F8FA),
    card = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF131418),
    textSecondary = Color(0xFF85868D),
    textTertiary = Color(0xFFAEAEB6),
    divider = Color(0xFFECEDF0),
    gridLine = Color(0xFFE8E9ED),
    accent = OriginBlue,
    accentEnd = OriginBlueEnd,
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFE0483B),
    scrim = Color(0x66000000)
)

val DarkPalette = OriginPalette(
    isLight = false,
    background = Color(0xFF09090C),
    backgroundElevated = Color(0xFF101116),
    card = Color(0xFF17181D),
    cardElevated = Color(0xFF1F2027),
    textPrimary = Color(0xFFF4F5F7),
    textSecondary = Color(0xFF9A9BA3),
    textTertiary = Color(0xFF6E6F78),
    divider = Color(0xFF26272E),
    gridLine = Color(0xFF23242B),
    accent = Color(0xFF6E8BFF),
    accentEnd = Color(0xFF9A7CFF),
    onAccent = Color(0xFF0B0B0F),
    danger = Color(0xFFFF6B5E),
    scrim = Color(0x99000000)
)
