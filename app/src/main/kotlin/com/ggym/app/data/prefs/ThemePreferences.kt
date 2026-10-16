package com.ggym.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Light / Dark / follow the system. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        /** Tolerant parse: an unknown or missing stored value falls back to [System]. */
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: System
    }
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    /**
     * Material You colours taken from the wallpaper. Off by default: it replaces the brand
     * palette, which is the whole point of GGym's look.
     */
    val useWallpaperColors: Boolean = false,
)

/**
 * Theme preferences, as an interface, so the UI depends on behaviour rather than on DataStore
 * internals and so tests can supply a DataStore backed by a temp file (no Android context).
 */
interface ThemePreferences {
    val settings: Flow<ThemeSettings>

    suspend fun setMode(mode: ThemeMode)

    suspend fun setUseWallpaperColors(enabled: Boolean)
}

class DataStoreThemePreferences(
    private val dataStore: DataStore<Preferences>,
) : ThemePreferences {

    override val settings: Flow<ThemeSettings> = dataStore.data.map { prefs ->
        ThemeSettings(
            mode = ThemeMode.fromStorage(prefs[KEY_THEME_MODE]),
            useWallpaperColors = prefs[KEY_USE_WALLPAPER_COLORS] ?: false,
        )
    }

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setUseWallpaperColors(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_WALLPAPER_COLORS] = enabled }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_USE_WALLPAPER_COLORS = booleanPreferencesKey("use_wallpaper_colors")
    }
}
