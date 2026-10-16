package com.ggym.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ggym.app.data.prefs.DataStoreThemePreferences
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import androidx.datastore.preferences.core.edit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import app.cash.turbine.test

/**
 * Round-trips the real DataStore implementation against a temp file, on the JVM.
 *
 * This is why [DataStoreThemePreferences] takes a `DataStore<Preferences>` instead of a
 * `Context`: no Robolectric, no instrumentation, and the app and the test share one code path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("theme-${System.nanoTime()}.preferences_pb")
        }

    @Test
    fun `defaults are system theme with wallpaper colours off`() = runTest {
        val preferences = DataStoreThemePreferences(createDataStore(backgroundScope))

        assertEquals(ThemeSettings(), preferences.settings.first())
    }

    @Test
    fun `theme mode and wallpaper colours survive a round trip`() = runTest {
        val preferences = DataStoreThemePreferences(createDataStore(backgroundScope))

        preferences.setMode(ThemeMode.Dark)
        preferences.setUseWallpaperColors(true)

        assertEquals(
            ThemeSettings(mode = ThemeMode.Dark, useWallpaperColors = true),
            preferences.settings.first(),
        )
    }

    @Test
    fun `changes are emitted to existing collectors`() = runTest {
        val preferences = DataStoreThemePreferences(createDataStore(backgroundScope))

        preferences.settings.test {
            assertEquals(ThemeSettings(), awaitItem())

            preferences.setMode(ThemeMode.Light)
            assertEquals(ThemeSettings(mode = ThemeMode.Light), awaitItem())
        }
    }

    @Test
    fun `unknown stored theme mode falls back to system`() = runTest {
        val dataStore = createDataStore(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        DataStoreThemePreferences(dataStore).setMode(ThemeMode.Dark)

        // Simulate a value written by a newer/older build.
        dataStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")] = "Neon" }

        assertEquals(ThemeMode.System, DataStoreThemePreferences(dataStore).settings.first().mode)
    }
}
