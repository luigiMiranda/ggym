package com.ggym.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.ggym.app.domain.repository.RestTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The countdown against the real DataStore, on a temp file.
 *
 * This is why [DataStoreRestTimerStore] takes a `DataStore<Preferences>` rather than a `Context`:
 * no Robolectric, no instrumentation, and the app and the test share one code path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerStoreDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("rest-timer-${System.nanoTime()}.preferences_pb")
        }

    private fun rest(
        endsAtMillis: Long = 1_750_000_090_000L,
        exerciseName: String = "Bench Press",
    ): RestTimerState = RestTimerState(
        endsAtMillis = endsAtMillis,
        totalSeconds = 90,
        exerciseLogId = 42,
        exerciseName = exerciseName,
    )

    @Test
    fun `no rest is running to begin with`() = runTest {
        val store = DataStoreRestTimerStore(createDataStore(backgroundScope))

        assertNull(store.state.first())
    }

    @Test
    fun `a countdown keeps its deadline, length and exercise`() = runTest {
        val store = DataStoreRestTimerStore(createDataStore(backgroundScope))
        val state = rest()

        store.set(state)

        assertEquals(state, store.state.first())
    }

    @Test
    fun `a new countdown replaces the one that was running`() = runTest {
        val store = DataStoreRestTimerStore(createDataStore(backgroundScope))
        val second = rest(endsAtMillis = 1_750_000_250_000L, exerciseName = "Squat")

        store.set(rest())
        store.set(second)

        assertEquals(second, store.state.first())
    }

    @Test
    fun `clearing the timer leaves nothing behind`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val store = DataStoreRestTimerStore(dataStore)
        store.set(rest())

        store.set(null)

        assertNull(store.state.first())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `a countdown is emitted to existing collectors`() = runTest {
        val store = DataStoreRestTimerStore(createDataStore(backgroundScope))
        val state = rest()

        store.state.test {
            assertNull(awaitItem())

            store.set(state)
            assertEquals(state, awaitItem())

            store.set(null)
            assertNull(awaitItem())
        }
    }
}
