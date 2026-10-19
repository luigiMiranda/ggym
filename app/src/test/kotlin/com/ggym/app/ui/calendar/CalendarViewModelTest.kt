package com.ggym.app.ui.calendar

import app.cash.turbine.test
import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.testing.TestData
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The calendar over a real database: which workouts land on which day, which day is selected, and
 * what a deletion does to both.
 *
 * The clock is an explicit 14 October 2026, so "today" is a fact the test owns rather than whatever
 * day the suite happens to run on.
 */
class CalendarViewModelTest : ViewModelTest() {

    private val today = TestData.date(2026, 10, 14)
    private val october = YearMonth.of(2026, 10)

    private val viewModel by lazy { CalendarViewModel(historyRepository, today = { today }) }

    @Test
    fun `the month is bucketed by local date and today is selected`() = runTest {
        saveWorkout(TestData.date(2026, 10, 3), dayName = "Pull A")
        saveWorkout(TestData.date(2026, 10, 14), dayName = "Push A", durationSeconds = 3120)
        saveWorkout(TestData.date(2026, 10, 14), dayName = "Push B", durationSeconds = 1800)

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.sessionsByDay.isNotEmpty() }

            assertEquals(october, state.month)
            assertEquals(today, state.selectedDate)
            assertEquals(
                setOf(TestData.date(2026, 10, 3), TestData.date(2026, 10, 14)),
                state.sessionsByDay.keys,
            )
            assertEquals(1, state.workoutCount(TestData.date(2026, 10, 3)))
            assertEquals(2, state.workoutCount(TestData.date(2026, 10, 14)))
            assertEquals(0, state.workoutCount(TestData.date(2026, 10, 5)))

            assertEquals(listOf("Push A", "Push B"), state.selectedSessions.map { it.dayName })
            assertEquals(3120, state.selectedSessions.first().durationSeconds)

            val card = state.selectedSessions.first()
            assertEquals(listOf("Bench Press", "Overhead Press"), card.exercises.map { it.name })
            assertEquals(2, card.exercises[0].setCount)
            assertEquals(82.5, card.exercises[0].maxWeightKg!!, 0.0)
            assertTrue("an exercise with no completed set is skipped", card.exercises[1].skipped)
            assertNull("the card does not invent a weight", card.exercises[1].maxWeightKg)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a past month selects its first trained day, not the first day`() = runTest {
        saveWorkout(TestData.date(2026, 9, 3), dayName = "Pull A")
        saveWorkout(TestData.date(2026, 9, 30), dayName = "Push A")

        viewModel.uiState.test {
            awaitState { !it.isLoading }
            viewModel.showPreviousMonth()

            val state = awaitState {
                it.month == YearMonth.of(2026, 9) && !it.isLoading && it.sessionsByDay.isNotEmpty()
            }

            assertEquals(TestData.date(2026, 9, 3), state.selectedDate)
            assertEquals(1, state.workoutCount(TestData.date(2026, 9, 3)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a month with no workouts still selects a day`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }
            viewModel.showPreviousMonth()

            val state = awaitState { it.month == YearMonth.of(2026, 9) && !it.isLoading }

            assertEquals(TestData.date(2026, 9, 1), state.selectedDate)
            assertFalse(state.hasWorkoutsThisMonth)
            assertTrue(state.selectedSessions.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a day selects it and shows that day's workouts`() = runTest {
        saveWorkout(TestData.date(2026, 10, 5), dayName = "Legs")
        saveWorkout(TestData.date(2026, 10, 14), dayName = "Push A")

        viewModel.uiState.test {
            awaitState { !it.isLoading && it.sessionsByDay.isNotEmpty() }

            viewModel.selectDate(TestData.date(2026, 10, 5))
            val selected = awaitState { it.selectedDate == TestData.date(2026, 10, 5) }
            assertEquals(listOf("Legs"), selected.selectedSessions.map { it.dayName })

            // A day nobody trained on is still selectable, and says so with an empty list.
            viewModel.selectDate(TestData.date(2026, 10, 6))
            val empty = awaitState { it.selectedDate == TestData.date(2026, 10, 6) }
            assertTrue(empty.selectedSessions.isEmpty())
            assertTrue(empty.hasWorkoutsThisMonth)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a day of an adjacent month turns the page to it`() = runTest {
        saveWorkout(TestData.date(2026, 9, 30), dayName = "Push A")

        viewModel.uiState.test {
            awaitState { !it.isLoading }

            viewModel.selectDate(TestData.date(2026, 9, 30))

            val state = awaitState {
                it.month == YearMonth.of(2026, 9) && it.selectedDate == TestData.date(2026, 9, 30) &&
                    !it.isLoading && it.sessionsByDay.isNotEmpty()
            }
            assertEquals(listOf("Push A"), state.selectedSessions.map { it.dayName })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `paging across a year boundary applies the same selection rule`() = runTest {
        saveWorkout(TestData.date(2027, 1, 5), dayName = "Push A")

        viewModel.uiState.test {
            awaitState { !it.isLoading }

            viewModel.showNextMonth() // November
            viewModel.showNextMonth() // December
            viewModel.showNextMonth() // January 2027

            val state = awaitState {
                it.month == YearMonth.of(2027, 1) && !it.isLoading && it.sessionsByDay.isNotEmpty()
            }
            assertEquals(TestData.date(2027, 1, 5), state.selectedDate)

            viewModel.showToday()
            val back = awaitState {
                it.month == october && it.selectedDate == today && !it.isLoading
            }
            assertEquals(today, back.selectedDate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a workout removes exactly that one and its dot`() = runTest {
        saveWorkout(TestData.date(2026, 10, 14), dayName = "Push A")
        saveWorkout(TestData.date(2026, 10, 14), dayName = "Push B")

        viewModel.uiState.test {
            val loaded = awaitState { !it.isLoading && it.selectedSessions.size == 2 }

            viewModel.askDelete(loaded.selectedSessions.first())
            awaitState { it.pendingDelete != null }
            viewModel.confirmDelete()

            val after = awaitState { it.pendingDelete == null && it.selectedSessions.size == 1 }

            assertEquals(listOf("Push B"), after.selectedSessions.map { it.dayName })
            assertEquals(1, after.workoutCount(TestData.date(2026, 10, 14)))
            assertEquals(
                listOf("Push B"),
                historyRepository.observeSessionsInRange(october.atDay(1), october.atEndOfMonth())
                    .first()
                    .map { it.dayNameSnapshot },
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a draft is never on the calendar`() = runTest {
        saveWorkout(TestData.date(2026, 10, 14), dayName = "Push A")
        historyRepository.startDraft(
            TestData.session(
                date = TestData.date(2026, 10, 13),
                dayNameSnapshot = "Pull A",
                isDraft = true,
                startedAtMillis = 1_760_000_000_000,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = null,
                        exerciseNameSnapshot = "Barbell Row",
                        sets = listOf(TestData.setLog(weight = 60.0, reps = 10)),
                    ),
                ),
            ),
        )

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.sessionsByDay.isNotEmpty() }

            assertEquals(setOf(TestData.date(2026, 10, 14)), state.sessionsByDay.keys)
            assertFalse("the running workout stays on the Workouts tab", state.hasWorkoutOn(TestData.date(2026, 10, 13)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A finished workout with a performed exercise and a skipped one. */
    private suspend fun saveWorkout(
        date: LocalDate,
        dayName: String,
        durationSeconds: Long = 3120,
    ): Long = historyRepository.saveSession(
        TestData.session(
            date = date,
            dayNameSnapshot = dayName,
            durationSeconds = durationSeconds,
            exercises = listOf(
                TestData.exerciseLog(
                    exerciseId = null,
                    exerciseNameSnapshot = "Bench Press",
                    orderIndex = 0,
                    sets = listOf(
                        TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                        TestData.setLog(setNumber = 2, weight = 82.5, reps = 8),
                    ),
                ),
                skippedPress(),
            ),
        ),
    )

    private fun skippedPress(): WorkoutExerciseLog = TestData.exerciseLog(
        exerciseId = null,
        exerciseNameSnapshot = "Overhead Press",
        wasCompleted = false,
        orderIndex = 1,
        sets = emptyList(),
    )
}

/** Whether a day has a workout, phrased for the assertion above. */
private fun CalendarUiState.hasWorkoutOn(date: LocalDate): Boolean = workoutCount(date) > 0
