package com.lyl.timetable.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass 材质。
 *
 * 真实的背景模糊（blur-behind）在 Android 上仅在部分系统与 API 级别可用，
 * 这里采用在全部 API 26+ 设备上一致的等效做法：
 *   1. 半透明底色，使下层内容透出；
 *   2. 顶部高光 + 底部暗边的渐变描边，模拟玻璃边缘的折射亮线；
 *   3. 柔和外阴影形成浮起层次（浅色模式明显，深色模式以描边区分）。
 */
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
        .border(
            width = 0.75.dp,
            brush = Brush.verticalGradient(
                0.0f to p.glassHighlight,
                0.45f to Color.Transparent,
                1.0f to p.glassEdge
            ),
            shape = shape
        )
}

/** 玻璃内容柔光：在内容之上叠一层顶部渐隐高光，强化材质感 */
@Composable
fun Modifier.glassSheen(
    radius: Dp = AppDimens.GroupRadius,
    intensity: Float? = null
): Modifier {
    val p = AppTheme.colors
    val alpha = intensity ?: if (p.isLight) 0.45f else 0.10f
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
 * 保留极淡阴影以维持层级，深色模式下改用描边区分。
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

/** 视图内的细分隔线（用于补足非卡片场景） */
@Composable
fun Modifier.hairlineTop(): Modifier {
    val p = AppTheme.colors
    return this.drawWithContent {
        drawContent()
        drawLine(
            color = p.separator,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1f
        )
    }
}
