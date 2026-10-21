package com.ggym.app.ui.settings

import app.cash.turbine.test
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeThemePreferences
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.testing.awaitState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Settings tab's state: the theme and the rest timer's switch.
 *
 * The switch is checked on the state the screen reads and on the command the running countdown
 * would receive, because "off" is only true if both agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()
    private val workoutPreferences = FakeWorkoutPreferences()

    private val timer = RestTimerUseCases(
        store = restStore,
        commands = restCommands,
        preferences = workoutPreferences,
        now = { 1_000L },
    )

    private val viewModel by lazy { SettingsViewModel(FakeThemePreferences(), timer, workoutPreferences) }

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the switch reads what the preference says, on by default`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitItem().restTimerEnabled)

            workoutPreferences.setRestTimerEnabled(false)

            assertFalse(awaitState { !it.restTimerEnabled }.restTimerEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning the switch off stops a rest that is running`() = runTest {
        restStore.set(RestTimerState(endsAtMillis = 91_000, totalSeconds = 90, exerciseLogId = 7, exerciseName = "Bench Press"))

        viewModel.uiState.test {
            awaitState { it.restTimerEnabled }

            viewModel.setRestTimerEnabled(false)

            assertFalse(awaitState { !it.restTimerEnabled }.restTimerEnabled)
            assertEquals(listOf(RecordedCommand.Stop), restCommands.received)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning the switch on again sends nothing`() = runTest {
        workoutPreferences.setRestTimerEnabled(false)

        viewModel.uiState.test {
            awaitState { !it.restTimerEnabled }

            viewModel.setRestTimerEnabled(true)

            assertTrue(awaitState { it.restTimerEnabled }.restTimerEnabled)
            assertTrue("nothing was running, so nothing is sent", restCommands.received.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the theme choice still reaches the preference`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setThemeMode(ThemeMode.Dark)

            assertEquals(ThemeMode.Dark, awaitState { it.theme.mode == ThemeMode.Dark }.theme.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
