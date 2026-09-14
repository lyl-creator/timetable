package com.lyl.timetable.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lyl.timetable.ui.theme.AppDimens
import com.lyl.timetable.ui.theme.AppTheme
import com.lyl.timetable.ui.theme.AppType
import com.lyl.timetable.ui.theme.liquidGlass

enum class AppTab(val label: String, val icon: ImageVector) {
    WEEK("课表", Icons.Rounded.CalendarMonth),
    TODAY("今日", Icons.Rounded.Today),
    SETTINGS("设置", Icons.Rounded.Settings)
}

/**
 * 底部玻璃 Tab Bar（iOS 26 的浮动胶囊形态）。
 * 选中项使用强调色，图标带轻微弹性缩放。
 */
@Composable
fun FloatingTabBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.colors
    Row(
        modifier = modifier
            .height(58.dp)
            .fillMaxWidth()
            .liquidGlass(radius = AppDimens.Capsule, strong = true, elevation = 14.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppTab.entries.forEach { tab ->
            TabBarItem(
                tab = tab,
                selected = tab == current,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun TabBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val p = AppTheme.colors
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "tabScale"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) p.accent else p.textSecondary,
        animationSpec = tween(200),
        label = "tabTint"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .width(84.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = AppType.Caption2,
            color = tint
        )
    }
}

/** 顶部状态栏占位（由外层统一处理 Insets 时可省略） */
@Composable
fun StatusBarSpacer(modifier: Modifier = Modifier) {
    Box(modifier.height(0.dp).width(0.dp))
}
