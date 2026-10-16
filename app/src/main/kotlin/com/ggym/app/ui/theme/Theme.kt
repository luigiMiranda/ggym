package com.ggym.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemeSettings

/**
 * GGym's single theme entry point.
 *
 * Colour resolution, in order of precedence:
 *  1. wallpaper colours — only if the user explicitly opted in **and** the device supports
 *     dynamic colour (API 31+); this replaces the brand palette by definition;
 *  2. the brand schemes, Light or Dark.
 *
 * Typography, shapes and motion are brand-owned and never come from the wallpaper.
 */
@Composable
fun GgymTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = isDarkTheme(settings.mode)
    val context = LocalContext.current

    val colorScheme = when {
        settings.useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> GgymDarkColorScheme
        else -> GgymLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = GgymMotionScheme,
        typography = GgymTypography,
        shapes = GgymShapes,
        content = content,
    )
}

/**
 * Resolves the effective light/dark choice. Shared with `MainActivity` so the system bar
 * icon colours can be set without duplicating the [ThemeMode] logic.
 */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
