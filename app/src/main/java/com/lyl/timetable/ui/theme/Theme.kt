package com.lyl.timetable.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lyl.timetable.data.ThemeMode

val LocalAppPalette = staticCompositionLocalOf { lightPaletteFallback() }
val LocalAccentIndex = staticCompositionLocalOf { 0 }
val LocalIsLight = staticCompositionLocalOf { true }

/** 全局取值入口 */
object AppTheme {
    val colors: AppPalette
        @Composable get() = LocalAppPalette.current

    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val accentOption: AccentOption
        @Composable get() = AccentOptions[LocalAccentIndex.current.coerceIn(0, AccentOptions.size - 1)]

    val isLight: Boolean
        @Composable get() = LocalIsLight.current
}

/** 非组合环境下的兜底色板（仅用于 CompositionLocal 默认值） */
private fun lightPaletteFallback(): AppPalette = paletteOf(lightColorScheme(), isLight = true)

/** 由 Material 3 的 ColorScheme 派生语义色板 */
private fun paletteOf(scheme: ColorScheme, isLight: Boolean): AppPalette = AppPalette(
    isLight = isLight,
    background = scheme.background,
    backgroundSecondary = scheme.surfaceVariant.copy(alpha = 0.4f),
    card = scheme.surface,
    cardElevated = if (isLight) Color.White else lerp(scheme.surface, Color.White, 0.06f),
    fill = scheme.surfaceVariant.copy(alpha = 0.6f),
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textTertiary = scheme.outline,
    separator = scheme.outlineVariant,
    gridLine = scheme.outlineVariant.copy(alpha = 0.6f),
    accent = scheme.primary,
    accentContainer = scheme.primaryContainer,
    onAccent = scheme.onPrimary,
    onAccentContainer = scheme.onPrimaryContainer,
    danger = scheme.error,
    scrim = scheme.scrim
)

/** 关闭动态取色时，按所选强调色生成 M3 色板 */
private fun lightSchemeWith(option: AccentOption): ColorScheme {
    val base = lightColorScheme()
    return base.copy(
        primary = option.color,
        onPrimary = Color.White,
        primaryContainer = lerp(base.surface, option.color, 0.16f),
        onPrimaryContainer = lerp(base.onSurface, option.color, 0.72f),
        secondary = lerp(base.onSurface, option.color, 0.55f),
        secondaryContainer = lerp(base.surface, option.color, 0.10f),
        onSecondaryContainer = lerp(base.onSurface, option.color, 0.66f),
        tertiary = lerp(base.onSurface, option.color, 0.42f),
        surfaceTint = option.color
    )
}

private fun darkSchemeWith(option: AccentOption): ColorScheme {
    val base = darkColorScheme()
    return base.copy(
        primary = option.darkColor,
        onPrimary = lerp(base.surface, option.darkColor, 0.85f),
        primaryContainer = lerp(base.surface, option.darkColor, 0.24f),
        onPrimaryContainer = lerp(Color.White, option.darkColor, 0.30f),
        secondary = lerp(base.onSurface, option.darkColor, 0.60f),
        secondaryContainer = lerp(base.surface, option.darkColor, 0.18f),
        onSecondaryContainer = lerp(Color.White, option.darkColor, 0.24f),
        tertiary = lerp(base.onSurface, option.darkColor, 0.45f),
        surfaceTint = option.darkColor
    )
}

@Composable
private fun rememberScheme(dark: Boolean, accentIndex: Int, dynamicColor: Boolean): ColorScheme {
    val context = LocalContext.current
    val option = AccentOptions[accentIndex.coerceIn(0, AccentOptions.size - 1)]
    return when {
        // Material You：Android 12 及以上从壁纸取色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkSchemeWith(option)
        else -> lightSchemeWith(option)
    }
}

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentIndex: Int = 0,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = rememberScheme(dark, accentIndex, dynamicColor)

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

    CompositionLocalProvider(
        LocalAppPalette provides paletteOf(scheme, !dark),
        LocalAccentIndex provides accentIndex,
        LocalIsLight provides !dark
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
