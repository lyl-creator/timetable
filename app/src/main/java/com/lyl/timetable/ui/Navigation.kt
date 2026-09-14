package com.lyl.timetable.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.OriginTheme

enum class AppTab(val label: String, val icon: ImageVector) {
    WEEK("课表", Icons.Rounded.CalendarMonth),
    TODAY("今日", Icons.Rounded.Today),
    SETTINGS("设置", Icons.Rounded.Settings)
}

/** 悬浮胶囊底部导航（OriginOS 原子组件风格） */
@Composable
fun FloatingBottomBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OriginTheme.colors
    Surface(
        modifier = modifier
            .height(62.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(31.dp),
        color = c.card,
        shadowElevation = if (c.isLight) 8.dp else 0.dp,
        border = if (c.isLight) null else androidx.compose.foundation.BorderStroke(1.dp, c.divider)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppTab.entries.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = tab == current,
                    onClick = { onSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = OriginTheme.colors
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, tween(220), label = "navScale")

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) c.accentSoft(if (c.isLight) 0.12f else 0.20f) else c.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) OriginTheme.accentStart else c.textTertiary,
            modifier = Modifier.size(22.dp)
        )
        if (selected) {
            Spacer(Modifier.width(7.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                color = OriginTheme.accentStart
            )
        }
    }
}

/** 顶部状态栏占位 */
@Composable
fun StatusBarSpacer(modifier: Modifier = Modifier) {
    Box(modifier.height(0.dp).width(0.dp))
}

/** 简易的分组卡片容器，用于设置页 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = OriginTheme.colors
    Column(modifier) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = c.card,
            shadowElevation = if (c.isLight) 3.dp else 0.dp,
            border = if (c.isLight) null else androidx.compose.foundation.BorderStroke(1.dp, c.divider)
        ) {
            Column { content() }
        }
    }
}
