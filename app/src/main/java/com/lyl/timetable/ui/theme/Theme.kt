package com.lyl.timetable.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lyl.timetable.data.ThemeMode

val LocalOriginPalette = staticCompositionLocalOf { LightPalette }
val LocalAccentIndex = staticCompositionLocalOf { 0 }

/** 全局取值入口 */
object OriginTheme {
    val colors: OriginPalette
        @Composable get() = LocalOriginPalette.current

    val accentStart: Color
        @Composable get() = AccentPalette[LocalAccentIndex.current % AccentPalette.size].start

    val accentEnd: Color
        @Composable get() = AccentPalette[LocalAccentIndex.current % AccentPalette.size].end
}

@Composable
fun OriginTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = if (dark) DarkPalette else LightPalette
    val accent = AccentPalette[accentIndex.coerceIn(0, AccentPalette.size - 1)]
    val palette = base.copy(accent = accent.start, accentEnd = accent.end)

    // 状态栏 / 导航栏图标明暗跟随主题
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentEnd,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentEnd,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
            error = palette.danger
        )
    }

    CompositionLocalProvider(
        LocalOriginPalette provides palette,
        LocalAccentIndex provides accentIndex
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OriginTypography,
            shapes = OriginShapes,
            content = content
        )
    }
}
