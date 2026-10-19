package com.ggym.app.ui.history

import app.cash.turbine.test
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.testing.TestData
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One past workout, corrected: the set that changes, the ones that do not, and the two destructive
 * paths.
 *
 * Every assertion about a write is made against the database, because "history is corrected and
 * never extended" is a promise about the rows, not about the screen's copy of them.
 */
class HistoryDetailViewModelTest : ViewModelTest() {

    @Test
    fun `the workout is read with its exercises and sets in the order they happened`() = runTest {
        val sessionId = givenWorkout()

        viewModel(sessionId).uiState.test {
            val state = awaitState { !it.isLoading }

            assertEquals(TestData.date(2026, 10, 14), state.date)
            assertEquals("Push A", state.dayName)
            assertEquals("Push Day", state.routineName)
            assertEquals(3120, state.durationSeconds)
            assertEquals(listOf("Bench Press", "Cable Fly"), state.exercises.map { it.name })
            assertEquals(listOf(1, 2, 3), state.exercises[0].sets.map { it.setNumber })
            assertEquals(listOf(80.0, 82.5, 82.5), state.exercises[0].sets.map { it.weightKg })
            assertEquals(listOf(10, 9, 8), state.exercises[0].sets.map { it.reps })
            assertFalse(state.isMissing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a corrected set writes exactly that row`() = runTest {
        val sessionId = givenWorkout()
        val viewModel = viewModel(sessionId)

        viewModel.uiState.test {
            val loaded = awaitState { !it.isLoading }
            val second = loaded.exercises[0].sets[1]

            viewModel.editSet(second)
            val editing = awaitState { it.editor != null }
            val prefill = requireNotNull(editing.editor)
            assertEquals(82.5, prefill.set.weightKg, 0.0)
            assertEquals(9, prefill.set.reps)

            viewModel.saveSet(85.0, 6)

            val saved = awaitState { it.editor == null && it.exercises[0].sets[1].weightKg == 85.0 }

            assertEquals(6, saved.exercises[0].sets[1].reps)
            assertEquals("the first set is untouched", 80.0, saved.exercises[0].sets[0].weightKg, 0.0)
            assertEquals("the third set is untouched", 82.5, saved.exercises[0].sets[2].weightKg, 0.0)

            val stored = storedSession(sessionId).exercises[0].sets
            assertEquals(listOf(80.0, 85.0, 82.5), stored.map { it.weight })
            assertEquals(listOf(10, 6, 8), stored.map { it.reps })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a weight that is not a number is refused and the editor stays open`() = runTest {
        val sessionId = givenWorkout()
        val viewModel = viewModel(sessionId)

        viewModel.uiState.test {
            val loaded = awaitState { !it.isLoading }
            viewModel.editSet(loaded.exercises[0].sets.first())
            awaitState { it.editor != null }

            viewModel.saveSet(null, 10)

            val refused = awaitState { it.editor?.error == SetEditError.WeightInvalid }
            assertEquals(10, refused.editor!!.set.reps)
            assertEquals(80.0, storedSession(sessionId).exercises[0].sets.first().weight, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reps of zero are refused`() = runTest {
        val sessionId = givenWorkout()
        val viewModel = viewModel(sessionId)

        viewModel.uiState.test {
            val loaded = awaitState { !it.isLoading }
            viewModel.editSet(loaded.exercises[0].sets.first())
            awaitState { it.editor != null }

            viewModel.saveSet(80.0, 0)

            val refused = awaitState { it.editor?.error == SetEditError.RepsInvalid }
            assertEquals(80.0, refused.editor!!.set.weightKg, 0.0)
            assertEquals(10, storedSession(sessionId).exercises[0].sets.first().reps)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing a set deletes it and leaves the others`() = runTest {
        val sessionId = givenWorkout()
        val viewModel = viewModel(sessionId)

        viewModel.uiState.test {
            val loaded = awaitState { !it.isLoading }
            val second = loaded.exercises[0].sets[1]

            viewModel.askDeleteSet(second)
            awaitState { it.pendingDelete != null }
            viewModel.confirmDelete()

            val after = awaitState { it.pendingDelete == null && it.exercises[0].sets.size == 2 }

            assertEquals(listOf(1, 3), after.exercises[0].sets.map { it.setNumber })
            assertEquals(listOf(80.0, 82.5), storedSession(sessionId).exercises[0].sets.map { it.weight })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting the workout removes it and tells the screen to leave`() = runTest {
        val sessionId = givenWorkout()
        val viewModel = viewModel(sessionId)

        viewModel.effects.test {
            viewModel.uiState.test {
                awaitState { !it.isLoading }
                viewModel.askDeleteWorkout()
                awaitState { it.pendingDelete == HistoryDeleteTarget.Workout }
                viewModel.confirmDelete()

                awaitState { it.isMissing }

                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(HistoryDetailEffect.WorkoutDeleted, awaitItem())
            assertNull("the workout is gone", historyRepository.observeSession(sessionId).first())
            assertEquals(0, database.countRows("workout_exercise_log"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a workout deleted while the screen is open shows the gone state`() = runTest {
        val sessionId = givenWorkout()
        val viewModel = viewModel(sessionId)

        viewModel.uiState.test {
            awaitState { !it.isLoading }

            historyRepository.deleteSession(sessionId)

            val gone = awaitState { it.isMissing }
            assertTrue(gone.exercises.isEmpty())
            assertNull(gone.date)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(sessionId: Long): HistoryDetailViewModel =
        HistoryDetailViewModel(sessionId, historyRepository)

    private suspend fun storedSession(sessionId: Long): WorkoutSession =
        requireNotNull(historyRepository.observeSession(sessionId).first()) { "the workout is gone" }

    /** A workout with three bench sets and a cable-fly set. */
    private suspend fun givenWorkout(): Long = historyRepository.saveSession(
        TestData.session(
            date = TestData.date(2026, 10, 14),
            dayNameSnapshot = "Push A",
            durationSeconds = 3120,
            exercises = listOf(
                TestData.exerciseLog(
                    exerciseId = null,
                    exerciseNameSnapshot = "Bench Press",
                    orderIndex = 0,
                    sets = listOf(
                        TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                        TestData.setLog(setNumber = 2, weight = 82.5, reps = 9),
                        TestData.setLog(setNumber = 3, weight = 82.5, reps = 8),
                    ),
                ),
                TestData.exerciseLog(
                    exerciseId = null,
                    exerciseNameSnapshot = "Cable Fly",
                    orderIndex = 1,
                    sets = listOf(TestData.setLog(setNumber = 1, weight = 20.0, reps = 12)),
                ),
            ),
        ),
    )
}
