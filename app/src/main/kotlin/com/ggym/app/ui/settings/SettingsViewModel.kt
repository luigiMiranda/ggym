package com.ggym.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.domain.workout.RestTimerUseCases
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Settings tab's state holder: the theme, and the rest timer's master switch.
 *
 * The switch is written through [RestTimerUseCases.setEnabled], not straight into the preference:
 * turning it off also ends a rest that is running, and that rule is the use case's, so this screen
 * cannot forget it.
 */
class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val restTimer: RestTimerUseCases,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferences.settings,
        restTimer.isEnabled,
    ) { theme, restTimerEnabled ->
        SettingsUiState(theme = theme, restTimerEnabled = restTimerEnabled)
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

    fun setRestTimerEnabled(enabled: Boolean) {
        viewModelScope.launch { restTimer.setEnabled(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
