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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lyl.timetable.data.ThemeMode

val LocalAppPalette = staticCompositionLocalOf { LightPalette }
val LocalAccentIndex = staticCompositionLocalOf { 0 }

/** 全局取值入口 */
object AppTheme {
    val colors: AppPalette
        @Composable get() = LocalAppPalette.current

    val accent: Color
        @Composable get() = colors.accent

    val accentOption: AccentOption
        @Composable get() = AccentOptions[LocalAccentIndex.current.coerceIn(0, AccentOptions.size - 1)]
}

@Composable
fun AppTheme(
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
    val option = AccentOptions[accentIndex.coerceIn(0, AccentOptions.size - 1)]
    val palette = base.copy(accent = if (dark) option.darkColor else option.color)

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
            secondary = palette.accent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.fill,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.separator,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.fill,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.separator,
            error = palette.danger
        )
    }

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalAccentIndex provides accentIndex
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
