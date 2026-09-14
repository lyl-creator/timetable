package com.lyl.timetable.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * Liquid Glass 材质（贴近 iOS 26 观感）。
 *
 * 玻璃由三层构成：
 *   1. **背景**：真实模糊（浮层，见 [hazeGlass]）或半透明底色（内容内卡片）；
 *   2. **边缘**：由亮到透明的渐变描边，模拟玻璃边缘的折射亮线，底部转为暗边形成厚度；
 *   3. **投影**：大范围低透明度的柔和阴影，制造浮起感。
 *
 * 说明：Android 上「模糊背后内容」需要按层采样，浮层可借助 Haze 实现真模糊；
 * 内容内部的卡片下方是纯色背景，模糊无视觉差异，因此使用半透明 + 描边即可。
 */

/** 玻璃边缘描边：顶部高光 → 中部透明 → 底部暗边 */
@Composable
fun Modifier.glassOutline(shape: Shape, width: Dp = 1.dp): Modifier {
    val p = AppTheme.colors
    return this.border(
        width = width,
        brush = Brush.verticalGradient(
            0.0f to p.glassHighlight,
            0.35f to p.glassHighlight.copy(alpha = 0.30f),
            0.72f to Color.Transparent,
            1.0f to p.glassEdge
        ),
        shape = shape
    )
}

/** 内容内的玻璃卡片（半透明底 + 描边 + 柔和阴影） */
@Composable
fun Modifier.liquidGlass(
    radius: Dp = AppDimens.GroupRadius,
    strong: Boolean = false,
    elevation: Dp = 12.dp
): Modifier {
    val p = AppTheme.colors
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(
            elevation = if (p.isLight) elevation else elevation * 0.45f,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.14f)
        )
        .clip(shape)
        .background(if (strong) p.glassStrong else p.glass)
        .glassOutline(shape)
}

/**
 * 浮层玻璃（真实背景模糊）。
 *
 * 使用前需由宿主提供 [HazeState]：内容层加 `Modifier.haze(state)`，
 * 本修饰符加在浮层上。API 31+ 为真实模糊，低版本自动回退为半透明底色。
 */
@Composable
fun Modifier.hazeGlass(
    state: HazeState,
    radius: Dp = AppDimens.GroupRadius,
    blurRadius: Dp = 26.dp,
    elevation: Dp = 14.dp,
    strong: Boolean = false
): Modifier {
    val p = AppTheme.colors
    val shape = RoundedCornerShape(radius)
    val style = HazeStyle(
        tint = if (p.isLight) {
            Color.White.copy(alpha = if (strong) 0.62f else 0.48f)
        } else {
            Color(0xFF1C1C1E).copy(alpha = if (strong) 0.62f else 0.48f)
        },
        blurRadius = blurRadius,
        noiseFactor = 0.05f
    )
    return this
        .shadow(
            elevation = if (p.isLight) elevation else elevation * 0.5f,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.16f)
        )
        .hazeChild(state = state, shape = shape, style = style)
        .glassOutline(shape)
}

/** 玻璃内容柔光：在内容之上叠一层顶部渐隐高光 */
@Composable
fun Modifier.glassSheen(
    radius: Dp = AppDimens.GroupRadius,
    intensity: Float? = null
): Modifier {
    val p = AppTheme.colors
    val alpha = intensity ?: if (p.isLight) 0.40f else 0.10f
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(p.glassHighlight.copy(alpha = alpha), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.5f
                )
            )
        }
}

/**
 * 不透明分组卡片（iOS 分组列表容器）。
 */
@Composable
fun Modifier.groupCard(radius: Dp = AppDimens.GroupRadius): Modifier {
    val p = AppTheme.colors
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(
            elevation = if (p.isLight) 6.dp else 0.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = Color.Black.copy(alpha = 0.09f)
        )
        .clip(shape)
        .background(p.card)
        .then(
            if (p.isLight) Modifier
            else Modifier.border(0.5.dp, p.separator.copy(alpha = 0.45f), shape)
        )
}

/** 控件底填充（分段控件槽、输入框、步进器） */
@Composable
fun Modifier.controlFill(radius: Dp = AppDimens.InnerRadius): Modifier {
    val p = AppTheme.colors
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(p.fill)
}

/** 视图内的细分隔线 */
@Composable
fun Modifier.hairlineTop(): Modifier {
    val p = AppTheme.colors
    return this.drawWithContent {
        drawContent()
        drawLine(
            color = p.separator,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1f
        )
    }
}

/**
 * 环境光斑背景：在页面底色上叠加两团大尺寸柔光。
 *
 * iOS 26 的玻璃观感依赖"背后有可透出的内容"，纯色背景上的玻璃几乎看不出效果；
 * 这层柔光既提升整体质感，也让浮层玻璃的模糊结果有色彩层次。
 */
@Composable
fun Modifier.ambientBackdrop(): Modifier {
    val p = AppTheme.colors
    val primary = p.accent
    val secondary = if (p.isLight) Color(0xFF7A5CFF) else Color(0xFF9A7CFF)

    return this.drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height

        // 左上主光斑
        val r1 = size.minDimension * (if (p.isLight) 0.78f else 0.88f)
        val c1 = Offset(w * 0.16f, h * 0.10f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = if (p.isLight) 0.20f else 0.26f),
                    Color.Transparent
                ),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )

        // 右下副光斑
        val r2 = size.minDimension * (if (p.isLight) 0.62f else 0.72f)
        val c2 = Offset(w * 0.88f, h * 0.42f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = if (p.isLight) 0.14f else 0.18f),
                    Color.Transparent
                ),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )

        // 底部淡淡的收束光，避免下半屏过于空
        val r3 = size.minDimension * 0.7f
        val c3 = Offset(w * 0.30f, h * 0.98f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = if (p.isLight) 0.10f else 0.14f),
                    Color.Transparent
                ),
                center = c3,
                radius = r3
            ),
            radius = r3,
            center = c3
        )
    }
}
