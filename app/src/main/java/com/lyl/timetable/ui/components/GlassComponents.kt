package com.lyl.timetable.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
import com.lyl.timetable.ui.theme.controlFill
import com.lyl.timetable.ui.theme.glassSheen
import com.lyl.timetable.ui.theme.groupCard
import com.lyl.timetable.ui.theme.liquidGlass

// ---------------------------------------------------------------------------
//  玻璃卡片
// ---------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = AppDimens.GroupRadius,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Column(
        modifier = modifier
            .liquidGlass(radius = radius, strong = strong)
            .then(clickable)
            .padding(contentPadding),
        content = content
    )
}

/** 强调色柔光卡片（用于高亮信息块） */
@Composable
fun AccentGlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val p = AppTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.GroupRadius))
            .background(p.accentSoft(if (p.isLight) 0.10f else 0.18f))
            .border(
                width = 0.75.dp,
                color = p.accent.copy(alpha = if (p.isLight) 0.22f else 0.36f),
                shape = RoundedCornerShape(AppDimens.GroupRadius)
            )
            .padding(contentPadding),
        content = content
    )
}

// ---------------------------------------------------------------------------
//  iOS 分组列表
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppType.Footnote,
        color = AppTheme.colors.textSecondary,
        modifier = modifier.padding(start = AppDimens.SeparatorInset, bottom = 8.dp, top = 2.dp)
    )
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.Footnote,
        color = AppTheme.colors.textSecondary,
        modifier = modifier.padding(start = AppDimens.SeparatorInset, top = 8.dp, end = 8.dp)
    )
}

/** iOS 分组：标题 + 圆角卡片 + 底部说明 */
@Composable
fun GroupSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) SectionHeader(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .groupCard(),
            content = content
        )
        if (!footer.isNullOrBlank()) SectionFooter(footer)
    }
}

/** 行内分隔线，左缩进与文字对齐（iOS inset grouped 规范） */
@Composable
fun RowSeparator(modifier: Modifier = Modifier, inset: Dp = AppDimens.SeparatorInset) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(0.5.dp)
            .background(AppTheme.colors.separator)
    )
}

/**
 * 列表行（iOS 风格）。
 * 最小高度 44dp，标题 17pt，副标题 13pt，值右对齐 17pt 次色。
 */
@Composable
fun RowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: Color? = null,
    centered: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val p = AppTheme.colors
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
            .heightIn(min = AppDimens.RowHeight)
            .then(clickable)
            .padding(horizontal = AppDimens.SeparatorInset, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        if (centered) {
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.weight(if (centered) 0f else 1f)) {
            Text(
                text = title,
                style = AppType.Body,
                color = titleColor ?: p.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = AppType.Footnote,
                    color = p.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (centered) {
            Spacer(Modifier.weight(1f))
        }
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                style = AppType.Body,
                color = p.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = p.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** iOS 风格开关 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AppTheme.colors.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = AppTheme.colors.fill,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

// ---------------------------------------------------------------------------
//  按钮
// ---------------------------------------------------------------------------

enum class PillStyle { Filled, Glass, Plain, Destructive }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    val p = AppTheme.colors
    val fg = when (style) {
        PillStyle.Filled -> p.onAccent
        PillStyle.Glass -> p.textPrimary
        PillStyle.Plain -> p.accent
        PillStyle.Destructive -> p.danger
    }
    val alpha = if (enabled) 1f else 0.4f
    val shape = RoundedCornerShape(AppDimens.Capsule)

    Box(
        modifier = modifier
            .height(height)
            .then(
                when (style) {
                    PillStyle.Filled -> Modifier
                        .clip(shape)
                        .background(p.accent.copy(alpha = alpha))
                        .then(
                            if (p.isLight) Modifier.shadow(
                                6.dp, shape, clip = false,
                                ambientColor = p.accent.copy(alpha = 0.35f),
                                spotColor = p.accent.copy(alpha = 0.35f)
                            ) else Modifier
                        )
                    PillStyle.Glass -> Modifier.liquidGlass(
                        radius = AppDimens.Capsule,
                        strong = true,
                        elevation = 6.dp
                    )
                    PillStyle.Plain -> Modifier
                    PillStyle.Destructive -> Modifier
                        .clip(shape)
                        .background(p.danger.copy(alpha = 0.12f * alpha))
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AppType.Headline,
                color = fg.copy(alpha = alpha),
                maxLines = 1
            )
        }
    }
}

/** 圆形玻璃图标按钮 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    tint: Color? = null
) {
    val p = AppTheme.colors
    val fg = tint ?: if (filled) p.onAccent else p.textPrimary
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (filled) {
                    Modifier.clip(CircleShape).background(p.accent)
                } else {
                    Modifier.liquidGlass(radius = AppDimens.Capsule, strong = true, elevation = 5.dp)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/** 胶囊选项（星期、节次、周次等选择场景） */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = AppTheme.colors
    val bg by animateColorAsState(
        if (selected) p.accent else p.fill,
        animationSpec = tween(180),
        label = "chipBg"
    )
    val fg by animateColorAsState(
        if (selected) p.onAccent else p.textPrimary,
        animationSpec = tween(180),
        label = "chipFg"
    )
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(AppDimens.Capsule))
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = AppType.Subheadline, color = fg, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
//  iOS 分段控件
// ---------------------------------------------------------------------------

@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val p = AppTheme.colors
    val index = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.fill)
            .padding(2.dp)
    ) {
        val itemWidth = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = itemWidth * index,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
            label = "segment"
        )
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(itemWidth)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (p.isLight) Color.White else Color(0xFF636366))
                .then(
                    if (p.isLight) Modifier.shadow(
                        3.dp, RoundedCornerShape(8.dp), clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    ) else Modifier
                )
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        style = AppType.Subheadline,
                        color = if (i == index) p.textPrimary else p.textPrimary.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  步进器
// ---------------------------------------------------------------------------

@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    step: Int = 1
) {
    val p = AppTheme.colors
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(AppDimens.Capsule))
            .background(p.fill),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            icon = Icons.Rounded.Remove,
            enabled = value - step >= range.first,
            onClick = { onValueChange((value - step).coerceIn(range)) }
        )
        Box(
            modifier = Modifier.widthIn(min = 54.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$value$suffix",
                style = AppType.Headline,
                color = p.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        StepperButton(
            icon = Icons.Rounded.Add,
            enabled = value + step <= range.last,
            onClick = { onValueChange((value + step).coerceIn(range)) }
        )
    }
}

@Composable
private fun StepperButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val p = AppTheme.colors
    Box(
        modifier = Modifier
            .size(38.dp)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = p.accent.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ---------------------------------------------------------------------------
//  页面标题与空态
// ---------------------------------------------------------------------------

@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val p = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = AppType.LargeTitle, color = p.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, style = AppType.Subheadline, color = p.textSecondary)
            }
        }
        actions()
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val p = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(p.fill),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = p.textTertiary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(text = title, style = AppType.Title3, color = p.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = AppType.Subheadline,
            color = p.textSecondary,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

@Composable
fun VGap(height: Dp = AppDimens.GroupGap) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp = 10.dp) = Spacer(Modifier.width(width))

/** 供列表项使用的圆形色点 */
@Composable
fun ColorDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
