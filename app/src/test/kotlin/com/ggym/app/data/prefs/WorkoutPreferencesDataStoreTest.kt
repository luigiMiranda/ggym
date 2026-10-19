package com.ggym.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
}
