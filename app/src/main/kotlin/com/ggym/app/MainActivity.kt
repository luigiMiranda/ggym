package com.ggym.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.ggym.app.data.prefs.ThemeSettings
import com.ggym.app.ui.nav.GgymNavHost
import com.ggym.app.ui.theme.GgymTheme
import com.ggym.app.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

/**
 * Single-activity host.
 *
 * The splash screen is held until the first theme preference arrives, so a user who chose
 * Dark never sees a light frame flash on launch.
 */
class MainActivity : ComponentActivity() {

    /**
     * `null` means "preferences not read yet" — that is the splash hold condition. Held as plain
     * Compose state rather than a lifecycle-aware collection because the splash screen must be
     * released by the *first* emission, before any composable exists.
     */
    private val themeSettings = mutableStateOf<ThemeSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = (application as GgymApplication).container

        lifecycleScope.launch {
            container.themePreferences.settings.collect { themeSettings.value = it }
        }

        splashScreen.setKeepOnScreenCondition { themeSettings.value == null }

        setContent {
            val settings = themeSettings.value ?: ThemeSettings()
            val darkTheme = isDarkTheme(settings.mode)

            // System bars follow the *resolved* theme, not the system one, so forcing
            // Light or Dark keeps the status/navigation icons legible.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(darkTheme),
                    navigationBarStyle = systemBarStyle(darkTheme),
                )
            }

            GgymTheme(settings = settings) {
                GgymNavHost(container = container)
            }
        }
    }
}

private fun systemBarStyle(dark: Boolean): SystemBarStyle =
    if (dark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
