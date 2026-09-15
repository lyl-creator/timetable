package com.lyl.timetable.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 排版：采用 Material 3 的字阶（Roboto / 系统无衬线），并保留语义化别名，
 * 便于页面按「大标题 / 正文 / 脚注」等语义引用。
 */
object AppType {
    val LargeTitle = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    )
    val Title1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    )
    val Title2 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )
    val Title3 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.15.sp
    )
    val Headline = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )
    val Body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    val Callout = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.4.sp
    )
    val Subheadline = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
    val Footnote = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    )
    val Caption1 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    val Caption2 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
}

/** Material 3 语义槽位映射 */
val AppTypography = Typography(
    displayLarge = AppType.LargeTitle.copy(fontSize = 45.sp, lineHeight = 52.sp),
    displayMedium = AppType.LargeTitle.copy(fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall = AppType.LargeTitle,
    headlineLarge = AppType.Title1.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = AppType.Title1,
    headlineSmall = AppType.Title2,
    titleLarge = AppType.Title3,
    titleMedium = AppType.Headline,
    titleSmall = AppType.Subheadline.copy(fontWeight = FontWeight.Medium),
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Subheadline,
    bodySmall = AppType.Footnote,
    labelLarge = AppType.Subheadline.copy(fontWeight = FontWeight.Medium),
    labelMedium = AppType.Caption1,
    labelSmall = AppType.Caption2
)

/** Material 3 形状：4 / 8 / 12 / 16 / 28 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Material 3 布局尺寸 */
object AppDimens {
    /** 卡片 / 分组的圆角（M3 large） */
    val GroupRadius = 16.dp
    /** 卡片内小元素圆角（M3 medium） */
    val InnerRadius = 12.dp
    /** 胶囊 */
    val Capsule = 999.dp
    /** 页面左右留白（M3 规范 16dp） */
    val ScreenPadding = 16.dp
    /** 分组之间间距 */
    val GroupGap = 12.dp
    /** 列表行最小高度（M3 ListItem 单行 56dp） */
    val RowHeight = 56.dp
    /** 分隔线左缩进 */
    val SeparatorInset = 16.dp
    /** 底部导航栏高度（M3 NavigationBar） */
    val TabBarHeight = 80.dp
}
