package com.ggym.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.LanguagePreferences
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.domain.repository.WorkoutPreferences
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.RingPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Settings tab's state holder: the theme, the rest timer's master switch, and the alert's sound
 * and ring policy.
 *
 * The switch is written through [RestTimerUseCases.setEnabled], not straight into the preference:
 * turning it off also ends a rest that is running, and that rule is the use case's, so this screen
 * cannot forget it. The alert's two settings carry no such rule, so they go to
 * [WorkoutPreferences] the way the theme's do.
 */
class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val languagePreferences: LanguagePreferences,
    private val restTimer: RestTimerUseCases,
    private val workoutPreferences: WorkoutPreferences,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferences.settings,
        languagePreferences.language,
        restTimer.isEnabled,
        workoutPreferences.alertSound,
        workoutPreferences.ringPolicy,
    ) { theme, language, restTimerEnabled, alertSound, ringPolicy ->
        SettingsUiState(
            theme = theme,
            language = language,
            restTimerEnabled = restTimerEnabled,
            alertSound = alertSound,
            ringPolicy = ringPolicy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setMode(mode) }
    }

    fun setUseWallpaperColors(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setUseWallpaperColors(enabled) }
    }

    /**
     * Writes the language choice. On Android 13+ the platform applies it and recreates the activity
     * itself; below that the activity notices the change and recreates, so the whole UI — and the
     * view models' default `Locale` — is rebuilt in the new language.
     */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { languagePreferences.setLanguage(language) }
    }

    fun setRestTimerEnabled(enabled: Boolean) {
        viewModelScope.launch { restTimer.setEnabled(enabled) }
    }

    fun setAlertSound(sound: AlertSound) {
        viewModelScope.launch { workoutPreferences.setAlertSound(sound) }
    }

    fun setRingPolicy(policy: RingPolicy) {
        viewModelScope.launch { workoutPreferences.setRingPolicy(policy) }
    }

    /**
     * One tap of the seconds stepper, in steps of ±1.
     *
     * The arithmetic and the bounds are [RingPolicy.step]'s, so the screen never holds a limit and
     * cannot store a value the alert could not use.
     */
    fun stepRingSeconds(steps: Int) {
        val seconds = (uiState.value.ringPolicy as? RingPolicy.Automatic)?.seconds
            ?: RingPolicy.DEFAULT_SECONDS
        setRingPolicy(RingPolicy.step(seconds, steps))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
