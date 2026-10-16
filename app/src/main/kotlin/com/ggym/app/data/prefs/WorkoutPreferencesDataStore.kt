package com.ggym.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ggym.app.domain.repository.WorkoutPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The workout switch, in the app's preferences DataStore.
 *
 * It shares a file with the theme settings: unlike the countdown, which the service rewrites on its
 * own schedule, this is a setting the user changes by hand, so it belongs with the rest of them.
 */
class DataStoreWorkoutPreferences(
    private val dataStore: DataStore<Preferences>,
) : WorkoutPreferences {

    /** On until the user says otherwise: the rest timer is a feature, not an opt-in experiment. */
    override val restTimerEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_REST_TIMER_ENABLED] ?: true }

    override suspend fun setRestTimerEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REST_TIMER_ENABLED] = enabled }
    }

    private companion object {
        val KEY_REST_TIMER_ENABLED = booleanPreferencesKey("rest_timer_enabled")
    }
}
