package com.ggym.app.testing

import app.cash.turbine.ReceiveTurbine
import com.ggym.app.domain.workout.WorkingWeightUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * A test for a ViewModel over a real in-memory database.
 *
 * `viewModelScope` needs a main dispatcher, and the app's view-models are built on the real
 * repositories, so this base is [RepositoryTest] plus the dispatcher swap: everything a screen
 * shows here comes from SQLite, not from a fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelTest : RepositoryTest() {

    @Before
    fun setMainDispatcher() {
        // Unconfined: a view-model launched write runs before the test's next assertion, which is
        // what a test of "the user tapped save" should observe anyway.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    internal val workingWeights by lazy {
        WorkingWeightUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
        )
    }
}

/**
 * The next state that satisfies [predicate], skipping intermediate emissions.
 *
 * A combined `uiState` emits once per source — the dialog flow, the database flow — so a test that
 * counted emissions would break every time a screen gained a field. Waiting for the state the test
 * is about keeps the assertion about behaviour rather than about plumbing.
 */
internal suspend fun <T> ReceiveTurbine<T>.awaitState(predicate: (T) -> Boolean): T {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) return state
    }
}
