package com.ggym.app.testing

import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.data.prefs.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The theme preference in memory. The screens' tests are about what they ask for and what they
 * show, not about DataStore, which has its own round-trip tests.
 */
internal class FakeThemePreferences(
    initial: ThemeSettings = ThemeSettings(),
) : ThemePreferences {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<ThemeSettings> = state

    override suspend fun setMode(mode: ThemeMode) {
        state.value = state.value.copy(mode = mode)
    }

    override suspend fun setUseWallpaperColors(enabled: Boolean) {
        state.value = state.value.copy(useWallpaperColors = enabled)
    }
}
