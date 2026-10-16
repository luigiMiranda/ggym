package com.ggym.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.data.prefs.ThemeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings state holder. Appearance is implemented; the rest-timer toggle and the backup
 * actions join this screen together with those features.
 */
class SettingsViewModel(
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val themeSettings: StateFlow<ThemeSettings> = themePreferences.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ThemeSettings(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setMode(mode) }
    }

    fun setUseWallpaperColors(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setUseWallpaperColors(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
