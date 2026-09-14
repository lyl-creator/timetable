package com.lyl.timetable.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.OriginDimens
import com.lyl.timetable.ui.theme.OriginTheme

// ---------------------------------------------------------------------------
//  卡片
// ---------------------------------------------------------------------------

@Composable
fun OriginCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(OriginDimens.CardRadius),
    color: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = OriginTheme.colors
    val surfaceColor = color ?: c.card
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier.then(clickable),
        shape = shape,
        color = surfaceColor,
        shadowElevation = if (c.isLight) 3.dp else 0.dp,
        border = if (c.isLight) null else BorderStroke(1.dp, c.divider)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 强调色柔和底卡片 */
@Composable
fun AccentSoftCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = OriginTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OriginDimens.CardRadius),
        color = c.accent.copy(alpha = if (c.isLight) 0.08f else 0.16f),
        border = BorderStroke(1.dp, c.accent.copy(alpha = if (c.isLight) 0.16f else 0.28f))
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

// ---------------------------------------------------------------------------
//  文本
// ---------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = OriginTheme.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun LargeTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = OriginTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = c.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
        }
        actions()
    }
}

// ---------------------------------------------------------------------------
//  按钮
// ---------------------------------------------------------------------------

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    val c = OriginTheme.colors
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(OriginDimens.InnerRadius + 2.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        OriginTheme.accentStart.copy(alpha = alpha),
                        OriginTheme.accentEnd.copy(alpha = alpha)
                    )
                )
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = c.onAccent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null
) {
    val c = OriginTheme.colors
    val fg = tint ?: c.textPrimary
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(OriginDimens.InnerRadius))
            .background(c.cardElevated)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, color = fg)
        }
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color? = null,
    tint: Color? = null
) {
    val c = OriginTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background ?: c.card)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: c.textPrimary,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ---------------------------------------------------------------------------
//  分段控件（OriginOS 胶囊风格，带滑动指示器）
// ---------------------------------------------------------------------------

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val c = OriginTheme.colors
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(OriginDimens.ChipRadius))
            .background(c.cardElevated)
            .padding(4.dp)
    ) {
        val itemWidth = (maxWidth - 0.dp) / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = tween(260),
            label = "segment"
        )
        // 指示器
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .height(32.dp)
                .clip(RoundedCornerShape(OriginDimens.ChipRadius))
                .background(
                    Brush.linearGradient(
                        listOf(OriginTheme.accentStart, OriginTheme.accentEnd)
                    )
                )
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(OriginDimens.ChipRadius))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    val fg by animateColorAsState(
                        targetValue = if (index == selectedIndex) c.onAccent else c.textSecondary,
                        animationSpec = tween(260),
                        label = "segmentFg"
                    )
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  其它
// ---------------------------------------------------------------------------

@Composable
fun Dot(active: Boolean, modifier: Modifier = Modifier) {
    val c = OriginTheme.colors
    val color by animateColorAsState(
        if (active) OriginTheme.accentStart else c.textTertiary.copy(alpha = 0.4f),
        label = "dot"
    )
    Box(
        modifier = modifier
            .size(if (active) 6.dp else 5.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val c = OriginTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.cardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

/** 极淡的分隔线 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OriginTheme.colors.divider)
    )
}

/** 列表行：左标题 + 右值 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val c = OriginTheme.colors
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(end = if (trailing != null) 10.dp else 0.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

/** 用于对齐「卡片内分组」的纵向间距 */
@Composable
fun VGap(height: Dp = OriginDimens.CardGap) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp = 8.dp) = Spacer(Modifier.width(width))

/** 轻阴影修饰符（供自定义容器使用） */
@Composable
fun Modifier.softShadow(radius: Dp = OriginDimens.CardRadius, elevation: Dp = 3.dp): Modifier {
    val c = OriginTheme.colors
    return this
        .then(if (c.isLight) Modifier.shadow(elevation, RoundedCornerShape(radius), clip = false) else Modifier)
        .clip(RoundedCornerShape(radius))
}
