package com.lyl.timetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppType

// ---------------------------------------------------------------------------
//  卡片
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(
            containerColor = if (strong) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 强调色卡片（用于高亮信息块，M3 的 primaryContainer 配色） */
@Composable
fun AccentGlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.GroupRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

// ---------------------------------------------------------------------------
//  分组列表
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.Headline,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = AppDimens.SeparatorInset, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.Footnote,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = AppDimens.SeparatorInset, top = 8.dp, end = 8.dp)
    )
}

/** M3 分组：标题 + 卡片 + 底部说明 */
@Composable
fun GroupSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) SectionHeader(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.GroupRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(content = content)
        }
        if (!footer.isNullOrBlank()) SectionFooter(footer)
    }
}

/** 行内分隔线（M3 HorizontalDivider，左缩进与文字对齐） */
@Composable
fun RowSeparator(modifier: Modifier = Modifier, inset: Dp = AppDimens.SeparatorInset) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/**
 * 列表行（M3 ListItem）：
 * 标题 16sp、副标题 14sp、尾部值 14sp，最小高度 56dp。
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
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        headlineContent = {
            Text(
                text = title,
                style = AppType.Body,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (subtitle.isNullOrBlank()) null else {
            {
                Text(
                    text = subtitle,
                    style = AppType.Subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = leading,
        trailingContent = if (value == null && trailing == null && !showChevron) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!value.isNullOrBlank()) {
                        Text(
                            text = value,
                            style = AppType.Subheadline,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(10.dp))
                        trailing()
                    }
                    if (showChevron) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** M3 开关 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
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
    height: Dp = 48.dp
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1)
    }
    val sized = modifier.height(height)

    when (style) {
        PillStyle.Filled -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            content = content
        )

        PillStyle.Glass -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            content = content
        )

        PillStyle.Plain -> TextButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            content = content
        )

        PillStyle.Destructive -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            content = content
        )
    }
}

/** 图标按钮（M3 IconButton / FilledIconButton） */
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
    if (filled) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(size)) {
            Icon(icon, contentDescription, Modifier.size(size * 0.48f))
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier.size(size)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.52f),
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 选项标签（M3 FilterChip） */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = text, maxLines = 1) },
        enabled = enabled,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
//  分段按钮（M3 SegmentedButton）
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val index = options.indexOf(selected).coerceAtLeast(0)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = i == index,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                label = {
                    Text(
                        text = label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  步进器：保留 +/− 按钮，点击中间数字可直接键入
// ---------------------------------------------------------------------------

@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    step: Int = 1,
    label: String? = null
) {
    var editing by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.Capsule),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = value - step >= range.first,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "减小",
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "$value$suffix",
                style = AppType.Headline,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.Capsule))
                    .clickable { editing = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            IconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = value + step <= range.last,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "增大",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (editing) {
        NumberInputDialog(
            title = label ?: "输入数值",
            initial = value,
            range = range,
            suffix = suffix,
            onDismiss = { editing = false },
            onConfirm = {
                onValueChange(it)
                editing = false
            }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppType.Title1,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = AppType.Subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = AppType.Title3,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = AppType.Subheadline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun VGap(height: Dp = AppDimens.GroupGap) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp = 10.dp) = Spacer(Modifier.width(width))

/** 列表项左侧的圆形色点 */
@Composable
fun ColorDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
