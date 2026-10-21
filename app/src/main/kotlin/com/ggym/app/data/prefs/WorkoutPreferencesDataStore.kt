package com.ggym.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ggym.app.domain.repository.WorkoutPreferences
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The workout's own preferences, in the app's preferences DataStore: the rest timer's master
 * switch, whether the notification permission has been asked for, and — since M6 — which sound the
 * rest-over alert makes and how long it rings.
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

    override val notificationPromptShown: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_NOTIFICATION_PROMPT_SHOWN] ?: false }

    override suspend fun setNotificationPromptShown(shown: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NOTIFICATION_PROMPT_SHOWN] = shown }
    }

    /**
     * The alert's sound. A stored URI with nothing in it is not a choice, so it reads as the
     * default: the type and the URI are written together and a blank one can only mean a corrupt
     * or half-migrated file, which must still produce an alert.
     */
    override val alertSound: Flow<AlertSound> = dataStore.data.map { prefs ->
        when (prefs[KEY_ALERT_SOUND_TYPE]) {
            ALERT_SOUND_NONE -> AlertSound.None
            ALERT_SOUND_RINGTONE -> prefs[KEY_ALERT_SOUND_URI]
                ?.takeIf(String::isNotBlank)
                ?.let(AlertSound::Ringtone)
                ?: AlertSound.SystemDefault

            else -> AlertSound.SystemDefault
        }
    }

    /**
     * Writes the sound and its URI as one edit: [AlertSound.SystemDefault] and [AlertSound.None]
     * clear the URI rather than leaving the last ringtone behind, so a later read cannot pair a new
     * type with an old URI.
     */
    override suspend fun setAlertSound(sound: AlertSound) {
        dataStore.edit { prefs ->
            when (sound) {
                AlertSound.SystemDefault -> {
                    prefs[KEY_ALERT_SOUND_TYPE] = ALERT_SOUND_SYSTEM
                    prefs.remove(KEY_ALERT_SOUND_URI)
                }

                AlertSound.None -> {
                    prefs[KEY_ALERT_SOUND_TYPE] = ALERT_SOUND_NONE
                    prefs.remove(KEY_ALERT_SOUND_URI)
                }

                is AlertSound.Ringtone -> {
                    prefs[KEY_ALERT_SOUND_TYPE] = ALERT_SOUND_RINGTONE
                    prefs[KEY_ALERT_SOUND_URI] = sound.uri
                }
            }
        }
    }

    /** The ring policy, defaulting to automatic for the same reason the switch defaults to on. */
    override val ringPolicy: Flow<RingPolicy> = dataStore.data.map { prefs ->
        if (prefs[KEY_RING_POLICY_AUTOMATIC] ?: true) {
            RingPolicy.automatic(prefs[KEY_RING_POLICY_SECONDS] ?: RingPolicy.DEFAULT_SECONDS)
        } else {
            RingPolicy.Manual
        }
    }

    /** The seconds are clamped here too, so a hand-edited file cannot schedule an absurd ring. */
    override suspend fun setRingPolicy(policy: RingPolicy) {
        dataStore.edit { prefs ->
            when (policy) {
                RingPolicy.Manual -> prefs[KEY_RING_POLICY_AUTOMATIC] = false

                is RingPolicy.Automatic -> {
                    prefs[KEY_RING_POLICY_AUTOMATIC] = true
                    prefs[KEY_RING_POLICY_SECONDS] = policy.seconds
                        .coerceIn(RingPolicy.MIN_SECONDS, RingPolicy.MAX_SECONDS)
                }
            }
        }
    }

    private companion object {
        val KEY_REST_TIMER_ENABLED = booleanPreferencesKey("rest_timer_enabled")
        val KEY_NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")

        /**
         * The sound's type is a word, not an ordinal: an added option must not renumber a choice
         * that is already stored.
         */
        val KEY_ALERT_SOUND_TYPE = stringPreferencesKey("alert_sound_type")
        val KEY_ALERT_SOUND_URI = stringPreferencesKey("alert_sound_uri")

        const val ALERT_SOUND_SYSTEM = "system"
        const val ALERT_SOUND_NONE = "none"
        const val ALERT_SOUND_RINGTONE = "ringtone"

        val KEY_RING_POLICY_AUTOMATIC = booleanPreferencesKey("ring_policy_automatic")
        val KEY_RING_POLICY_SECONDS = intPreferencesKey("ring_policy_seconds")
    }
}
