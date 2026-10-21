package com.ggym.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.ThemeSettings
import com.ggym.app.data.prefs.withAppLanguage
import com.ggym.app.service.EXTRA_OPEN_WORKOUT
import com.ggym.app.service.EXTRA_SILENCE_ALERT
import com.ggym.app.ui.nav.GgymNavHost
import com.ggym.app.ui.theme.GgymTheme
import com.ggym.app.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

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
     * The language choice, as the preference reports it. Read for one reason only: below API 33 the
     * platform does not recreate the activity when the language changes, so the activity has to
     * notice and do it itself.
     */
    private val appLanguage = mutableStateOf<AppLanguage?>(null)

    /** What the base context was built with, so [appLanguage] can be compared against it. */
    private var languageAtAttach: AppLanguage = AppLanguage.System

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

    /**
     * Whether a tap on the finished alert asked for silence. Acted on in [onResume], not here.
     *
     * The command is a *foreground* service start, and the platform refuses one from an app that is
     * not user-visible yet — `onCreate` is before that transition finishes, and asking there crashed
     * the app with `ForegroundServiceStartNotAllowedException` every time the alert's body was tapped.
     * Resumed is the first moment the request is allowed, and still the same `PendingIntent` and the
     * same command as the notification's Stop action.
     */
    private var silenceRequested = false

    /**
     * The app's language is applied to this activity's own resources, and `Locale.setDefault`
     * follows so that the view models, which format weights and dates through
     * `Locale.getDefault()`, agree with what is on screen.
     *
     * This is the whole mechanism on every API level: the app's store is the source of truth (see
     * `LanguagePreferences`), and a base context cannot be rewrapped after it is attached, so this
     * is the one moment the choice can be applied.
     */
    override fun attachBaseContext(newBase: Context) {
        val container = (newBase.applicationContext as GgymApplication).container
        // The one synchronous moment available: a base context cannot be rewrapped later. The read
        // is the same preferences file the splash screen already waits for.
        val language = runBlocking { container.languagePreferences.language.first() }
        languageAtAttach = language
        super.attachBaseContext(newBase.withAppLanguage(language))

        val resolved = resources.configuration.locales.takeIf { !it.isEmpty() }?.get(0)
        if (resolved != null) Locale.setDefault(resolved)
    }

    override fun onResume() {
        super.onResume()
        if (!silenceRequested) return
        silenceRequested = false
        lifecycleScope.launch { container.restTimerUseCases.silenceAlert() }
    }

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
        silenceRequested = true
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
        lifecycleScope.launch {
            container.languagePreferences.language.collect { appLanguage.value = it }
        }

        splashScreen.setKeepOnScreenCondition { themeSettings.value == null }
        consumeOpenWorkoutRequest(intent)
        consumeSilenceRequest(intent)

        setContent {
            val settings = themeSettings.value ?: ThemeSettings()
            val darkTheme = isDarkTheme(settings.mode)
            val language = appLanguage.value

            // A change here rebuilds the activity, which is what re-applies the base context and
            // re-derives the view models' default locale.
            LaunchedEffect(language) {
                if (language != null && language != languageAtAttach) recreate()
            }

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
