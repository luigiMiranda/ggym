package com.ggym.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The rest-timer switch against the real DataStore, on a temp file.
 *
 * It shares the app's preferences file in production, which is exactly what this exercises: the
 * implementation only ever sees a `DataStore<Preferences>`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPreferencesDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("workout-${System.nanoTime()}.preferences_pb")
        }

    @Test
    fun `the rest timer is on until the user turns it off`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        assertTrue(preferences.restTimerEnabled.first())
    }

    @Test
    fun `the switch survives a round trip both ways`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        preferences.setRestTimerEnabled(false)
        assertFalse(preferences.restTimerEnabled.first())

        preferences.setRestTimerEnabled(true)
        assertTrue(preferences.restTimerEnabled.first())
    }

    @Test
    fun `the switch is emitted to existing collectors`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        preferences.restTimerEnabled.test {
            assertTrue(awaitItem())

            preferences.setRestTimerEnabled(false)
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `the notification prompt is not yet shown, and the answer sticks`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        assertFalse(preferences.notificationPromptShown.first())

        preferences.setNotificationPromptShown(true)
        assertTrue(preferences.notificationPromptShown.first())
    }

    /** A fresh install rings the platform's default sound, automatically, for twenty seconds. */
    @Test
    fun `the alert's defaults are the system sound and an automatic twenty seconds`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        assertEquals(AlertSound.SystemDefault, preferences.alertSound.first())
        assertEquals(RingPolicy.Automatic(20), preferences.ringPolicy.first())
        assertEquals(RingPolicy.Default, preferences.ringPolicy.first())
    }

    @Test
    fun `every sound survives a round trip`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))
        val ringtone = AlertSound.Ringtone("content://media/external/audio/media/42")

        preferences.setAlertSound(ringtone)
        assertEquals(ringtone, preferences.alertSound.first())

        preferences.setAlertSound(AlertSound.None)
        assertEquals(AlertSound.None, preferences.alertSound.first())

        preferences.setAlertSound(AlertSound.SystemDefault)
        assertEquals(AlertSound.SystemDefault, preferences.alertSound.first())
    }

    @Test
    fun `every ring policy survives a round trip`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        preferences.setRingPolicy(RingPolicy.Automatic(45))
        assertEquals(RingPolicy.Automatic(45), preferences.ringPolicy.first())

        preferences.setRingPolicy(RingPolicy.Manual)
        assertEquals(RingPolicy.Manual, preferences.ringPolicy.first())
    }

    /** The stepper's range is enforced on the way in, so no caller can store an unusable ring. */
    @Test
    fun `the seconds are clamped to the stepper's range`() = runTest {
        val preferences = DataStoreWorkoutPreferences(createDataStore(backgroundScope))

        preferences.setRingPolicy(RingPolicy.Automatic(500))
        assertEquals(RingPolicy.Automatic(RingPolicy.MAX_SECONDS), preferences.ringPolicy.first())

        preferences.setRingPolicy(RingPolicy.Automatic(0))
        assertEquals(RingPolicy.Automatic(RingPolicy.MIN_SECONDS), preferences.ringPolicy.first())
    }

    /**
     * The corrupt-file path, written the way a corrupt file would be: the sound is recorded as a
     * ringtone but no URI came with it.
     *
     * It must still produce an alert, so the read degrades to the platform default rather than
     * throwing or resolving to `None` — a settings file must never be able to silence the alert.
     */
    @Test
    fun `a ringtone with no uri reads as the system default`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        dataStore.edit { it[stringPreferencesKey("alert_sound_type")] = "ringtone" }

        val preferences = DataStoreWorkoutPreferences(dataStore)

        assertEquals(AlertSound.SystemDefault, preferences.alertSound.first())
    }

    /**
     * Choosing System or None clears the URI rather than leaving the last ringtone behind: the type
     * and the URI are written as one edit, so a later read cannot pair a new type with an old URI.
     * Writing the type back to `ringtone` by hand is how this test looks at the URI that is no longer
     * stored.
     */
    @Test
    fun `choosing another sound clears the stored uri`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val preferences = DataStoreWorkoutPreferences(dataStore)
        val ringtoneKey = stringPreferencesKey("alert_sound_uri")

        preferences.setAlertSound(AlertSound.Ringtone("content://media/external/audio/media/42"))
        assertEquals(
            "content://media/external/audio/media/42",
            dataStore.data.first()[ringtoneKey],
        )

        preferences.setAlertSound(AlertSound.None)
        dataStore.edit { it[stringPreferencesKey("alert_sound_type")] = "ringtone" }

        assertEquals(AlertSound.SystemDefault, preferences.alertSound.first())
    }
}
