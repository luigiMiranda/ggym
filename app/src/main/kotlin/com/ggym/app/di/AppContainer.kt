package com.ggym.app.di

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ggym.app.data.prefs.DataStoreThemePreferences
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.data.prefs.createPreferencesDataStore
import com.ggym.app.ui.settings.SettingsViewModel

/**
 * Manual dependency container — the app's composition root (docs/specs/00-spine.md §4).
 *
 * Created once by [com.ggym.app.GgymApplication]. Milestones add their repositories and
 * use-cases here; screens never reach for singletons themselves.
 */
class AppContainer(context: Context) {

    private val preferencesDataStore = createPreferencesDataStore(context.applicationContext)

    val themePreferences: ThemePreferences = DataStoreThemePreferences(preferencesDataStore)

    val settingsViewModelFactory = viewModelFactory {
        initializer { SettingsViewModel(themePreferences) }
    }
}
