package com.ggym.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.repository.RestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The running countdown's own DataStore file.
 *
 * It is deliberately not the preferences file: the service writes the timer while the app may be
 * writing a theme setting, and DataStore serializes the writes of a file. Two files means a rest
 * starting or ending can never wait behind a preference write, and the countdown's data is the one
 * thing whose write path deserves to stay short.
 *
 * The function takes a [Context] and the store takes the [DataStore], exactly like
 * `createPreferencesDataStore`: the construction needs Android, everything above it does not.
 */
fun createRestTimerDataStore(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { context.preferencesDataStoreFile(REST_TIMER_STORE_NAME) },
)

internal const val REST_TIMER_STORE_NAME = "ggym_rest_timer"

/**
 * The countdown, stored as four values that are written and cleared together.
 *
 * The deadline is the one that carries meaning on its own; the exercise is what to name and the
 * length is what the rest was meant to be. Nothing is derived from anything else, and `edit`
 * commits the whole tuple or none of it, so there is no such thing as a half-written timer.
 */
class DataStoreRestTimerStore(
    private val dataStore: DataStore<Preferences>,
) : RestTimerStore {

    override val state: Flow<RestTimerState?> = dataStore.data.map { prefs ->
        val endsAtMillis = prefs[KEY_ENDS_AT_MILLIS] ?: return@map null
        RestTimerState(
            endsAtMillis = endsAtMillis,
            totalSeconds = prefs[KEY_TOTAL_SECONDS] ?: 0,
            exerciseLogId = prefs[KEY_EXERCISE_LOG_ID] ?: 0L,
            exerciseName = prefs[KEY_EXERCISE_NAME].orEmpty(),
        )
    }

    override suspend fun set(state: RestTimerState?) {
        dataStore.edit { prefs ->
            if (state == null) {
                prefs.remove(KEY_ENDS_AT_MILLIS)
                prefs.remove(KEY_TOTAL_SECONDS)
                prefs.remove(KEY_EXERCISE_LOG_ID)
                prefs.remove(KEY_EXERCISE_NAME)
            } else {
                prefs[KEY_ENDS_AT_MILLIS] = state.endsAtMillis
                prefs[KEY_TOTAL_SECONDS] = state.totalSeconds
                prefs[KEY_EXERCISE_LOG_ID] = state.exerciseLogId
                prefs[KEY_EXERCISE_NAME] = state.exerciseName
            }
        }
    }

    private companion object {
        val KEY_ENDS_AT_MILLIS = longPreferencesKey("ends_at_millis")
        val KEY_TOTAL_SECONDS = intPreferencesKey("total_seconds")
        val KEY_EXERCISE_LOG_ID = longPreferencesKey("exercise_log_id")
        val KEY_EXERCISE_NAME = stringPreferencesKey("exercise_name")
    }
}
