package com.ggym.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.ggym.app.data.prefs.ThemeSettings
import com.ggym.app.service.EXTRA_OPEN_WORKOUT
import com.ggym.app.service.EXTRA_SILENCE_ALERT
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

    /**
     * How many times a rest-timer notification has asked for the running workout. A counter rather
     * than a flag, so a second tap while the app is open is a second request, not a no-op.
     */
    private val openWorkoutRequests = mutableIntStateOf(0)

    /**
     * The container, read lazily: [onNewIntent] needs it and runs after `onCreate`, but not always
     * after the first composition, so it cannot be a local built inside `onCreate`.
     */
    private val container by lazy { (application as GgymApplication).container }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenWorkoutRequest(intent)
        consumeSilenceRequest(intent)
    }

    /**
     * A tap on the finished alert's body: the rest is already over, so the tap only has to stop the
     * ringing. Sent as the same silence command the notification's Stop action sends, so there is one
     * meaning for "stop ringing" wherever it is asked for.
     */
    private fun consumeSilenceRequest(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_SILENCE_ALERT, false)) return
        intent.removeExtra(EXTRA_SILENCE_ALERT)
        lifecycleScope.launch { container.restTimerUseCases.silenceAlert() }
    }

    /**
     * Counts the request and removes the extra, so that a configuration change — which recreates
     * the activity with the same intent — does not send the user back into the workout a second time.
     */
    private fun consumeOpenWorkoutRequest(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_WORKOUT, false)) return
        intent.removeExtra(EXTRA_OPEN_WORKOUT)
        openWorkoutRequests.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = this.container

        lifecycleScope.launch {
            container.themePreferences.settings.collect { themeSettings.value = it }
        }

        splashScreen.setKeepOnScreenCondition { themeSettings.value == null }
        consumeOpenWorkoutRequest(intent)
        consumeSilenceRequest(intent)

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
                GgymNavHost(container = container, openWorkoutRequests = openWorkoutRequests.intValue)
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
