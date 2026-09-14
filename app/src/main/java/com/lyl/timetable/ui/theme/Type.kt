package com.lyl.timetable.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 排版：对齐 iOS 的字号阶梯（SF Pro 尺寸表，pt → sp 按 1:1 映射）。
 * Android 上使用系统默认无衬线字体，字号、字重与行高按 iOS 规范取值。
 */
object AppType {
    val LargeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.4.sp
    )
    val Title1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp
    )
    val Title2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp
    )
    val Title3 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.38.sp
    )
    val Headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    )
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    )
    val Callout = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.32).sp
    )
    val Subheadline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    )
    val Footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    )
    val Caption1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
    val Caption2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.07.sp
    )
}

/** 映射到 Material3 语义槽位，使既有调用点直接获得 iOS 字号 */
val AppTypography = Typography(
    displayLarge = AppType.LargeTitle,
    displayMedium = AppType.Title1,
    displaySmall = AppType.Title2,
    headlineLarge = AppType.Title1,
    headlineMedium = AppType.Title2,
    headlineSmall = AppType.Title3,
    titleLarge = AppType.Title2,
    titleMedium = AppType.Title3,
    titleSmall = AppType.Headline,
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Subheadline,
    bodySmall = AppType.Footnote,
    labelLarge = AppType.Subheadline,
    labelMedium = AppType.Footnote,
    labelSmall = AppType.Caption2
)

/**
 * 形状：iOS 分组列表圆角约 10pt，但 Liquid Glass 语言下容器更圆润，
 * 统一取较大的连续圆角以获得玻璃材质的柔和边缘。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

object AppDimens {
    /** 分组卡片圆角 */
    val GroupRadius = 26.dp
    /** 卡片内小元素圆角 */
    val InnerRadius = 14.dp
    /** 胶囊 */
    val Capsule = 999.dp
    /** 页面左右留白 */
    val ScreenPadding = 20.dp
    /** 分组之间间距 */
    val GroupGap = 26.dp
    /** 列表行最小高度（iOS 44pt） */
    val RowHeight = 44.dp
    /** 分隔线左缩进 */
    val SeparatorInset = 16.dp
    /** 底部 Tab Bar 高度 */
    val TabBarHeight = 52.dp
}
