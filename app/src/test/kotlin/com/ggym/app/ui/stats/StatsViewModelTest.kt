package com.ggym.app.ui.stats

import app.cash.turbine.test
import com.ggym.app.domain.stats.BodyWeightRange
import com.ggym.app.domain.stats.BodyWeightUseCases
import com.ggym.app.domain.stats.CalendarMonths
import com.ggym.app.testing.TestData
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Stats tab over a real database: the four sections, the two filters, and the body-weight log.
 *
 * The clock is pinned to 19 October 2026 so the twelve-month window and "today" are facts the test
 * owns. Assertions about a write are made against the repository where the promise is about rows
 * ("one measurement per day"), and against the state where it is about what the screen shows.
 */
class StatsViewModelTest : ViewModelTest() {

    private val today = TestData.date(2026, 10, 19)
    private val useCases by lazy { BodyWeightUseCases(bodyWeightRepository) }
    private val viewModel by lazy {
        StatsViewModel(
            history = historyRepository,
            exercises = exerciseRepository,
            bodyWeights = useCases,
            todayProvider = { today },
        )
    }

    @Test
    fun `the screen opens on the three-month window and the first exercise`() = runTest {
        exerciseRepository.findOrCreate("Cable Fly")
        exerciseRepository.findOrCreate("Bench Press")

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.exercises.size == 2 }

            assertEquals(today, state.today)
            assertEquals(BodyWeightRange.THREE_MONTHS, state.bodyRange)
            assertEquals(listOf("Bench Press", "Cable Fly"), state.exercises.map { it.name })
            assertEquals("Bench Press", state.selectedExerciseName)
            assertEquals(0, state.bodyStats.count)
            assertTrue(state.bodyLog.isEmpty())
            assertTrue(state.progression.isEmpty)
            assertEquals(CalendarMonths.ATTENDANCE_MONTHS, state.attendance.size)
            assertTrue("twelve zero bars, not an empty chart", state.attendance.all { it.workoutDays == 0 })
            assertTrue(!state.hasWorkouts)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `attendance counts distinct days over the twelve-month window`() = runTest {
        saveWorkout(TestData.date(2026, 10, 14))
        saveWorkout(TestData.date(2026, 10, 14)) // a second workout, not a second trained day
        saveWorkout(TestData.date(2026, 10, 19))
        saveWorkout(TestData.date(2026, 9, 2))
        saveWorkout(TestData.date(2026, 8, 1))
        saveWorkout(TestData.date(2024, 1, 1)) // outside the window

        // A workout in progress is not attendance: it has not happened yet.
        historyRepository.startDraft(
            TestData.session(
                date = TestData.date(2026, 10, 10),
                isDraft = true,
                startedAtMillis = 1_760_000_000_000,
            ),
        )

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.hasWorkouts }

            val byMonth = state.attendance.associate { it.month to it.workoutDays }
            assertEquals(2, byMonth.getValue(YearMonth.of(2026, 10)))
            assertEquals(1, byMonth.getValue(YearMonth.of(2026, 9)))
            assertEquals(1, byMonth.getValue(YearMonth.of(2026, 8)))
            assertEquals(0, byMonth.getValue(YearMonth.of(2026, 7)))
            assertEquals(4, state.totalWorkoutDays)
            assertTrue("a month outside the window has no bar", !byMonth.containsKey(YearMonth.of(2024, 1)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logging a weight updates the chart and the log at once`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }

            viewModel.openLogSheet()
            val sheet = awaitState { it.sheet != null }
            val open = requireNotNull(sheet.sheet)
            assertEquals(today, open.date)
            assertNull("nothing to edit yet", open.entryId)

            viewModel.saveSheet(82.4)

            val saved = awaitState {
                it.sheet == null && it.bodyLog.size == 1 && it.bodyStats.count == 1
            }
            assertEquals(82.4, saved.bodyLog.single().weightKg, 0.0)
            assertEquals(1, saved.bodyStats.count)
            assertEquals(82.4, saved.bodyStats.averageKg!!, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second log on the same day updates the one measurement`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }

            useCases.log(today, 82.4)
            awaitState { it.bodyLog.size == 1 && it.bodyStats.count == 1 }

            // "+ Log" opens a new measurement for today, blank: the rule, not the form, decides
            // that saving it updates the row that is already there.
            viewModel.openLogSheet()
            val sheet = awaitState { it.sheet != null }
            val open = requireNotNull(sheet.sheet)
            assertNull("a new measurement, not an edit of today's row", open.entryId)
            assertEquals(today, open.date)

            viewModel.saveSheet(82.7)

            val saved = awaitState {
                it.sheet == null && it.bodyLog.singleOrNull()?.weightKg == 82.7 && it.bodyStats.maxKg == 82.7
            }
            assertEquals(1, saved.bodyLog.size)
            assertEquals(82.7, saved.bodyStats.maxKg!!, 0.0)

            val rows = bodyWeightRepository.observeRange(today, today).first()
            assertEquals(1, rows.size)
            assertEquals(82.7, rows.single().weightKg, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an out-of-range weight is refused and the sheet stays open`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }
            viewModel.openLogSheet()
            awaitState { it.sheet != null }

            viewModel.saveSheet(400.0)

            val refused = awaitState { it.sheet?.invalid == true }
            assertNull("nothing was written", useCases.observeAll(today).first().firstOrNull())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the range re-reads its window`() = runTest {
        useCases.log(today, 82.4)
        useCases.log(today.minusMonths(2), 83.0)
        useCases.log(today.minusMonths(5), 84.0)
        useCases.log(today.minusYears(2), 90.0)

        viewModel.uiState.test {
            awaitState { !it.isLoading && it.bodyStats.count == 2 }

            viewModel.selectRange(BodyWeightRange.ONE_MONTH)
            val oneMonth = awaitState { it.bodyRange == BodyWeightRange.ONE_MONTH && it.bodyStats.count == 1 }
            assertEquals(82.4, oneMonth.bodyStats.maxKg!!, 0.0)

            viewModel.selectRange(BodyWeightRange.SIX_MONTHS)
            val sixMonths = awaitState { it.bodyRange == BodyWeightRange.SIX_MONTHS && it.bodyStats.count == 3 }
            assertEquals(84.0, sixMonths.bodyStats.maxKg!!, 0.0)

            viewModel.selectRange(BodyWeightRange.ALL)
            val all = awaitState { it.bodyRange == BodyWeightRange.ALL && it.bodyStats.count == 4 }
            assertEquals(90.0, all.bodyStats.maxKg!!, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the exercise re-reads its progression`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press").id
        val fly = exerciseRepository.findOrCreate("Cable Fly").id

        saveWorkout(TestData.date(2026, 9, 1), exerciseId = bench, weight = 80.0)
        saveWorkout(TestData.date(2026, 9, 15), exerciseId = bench, weight = 85.0)
        saveWorkout(TestData.date(2026, 10, 1), exerciseId = bench, weight = 90.0)
        saveWorkout(TestData.date(2026, 10, 5), exerciseId = fly, weight = 20.0)

        viewModel.uiState.test {
            val benchView = awaitState { !it.isLoading && it.progression.points.size == 3 }
            assertEquals("Bench Press", benchView.selectedExerciseName)
            assertNotNull("three points make a trend", benchView.progression.trend)

            viewModel.selectExercise(fly)

            val flyView = awaitState {
                it.selectedExerciseName == "Cable Fly" && it.progression.points.size == 1
            }
            assertEquals(20.0, flyView.progression.points.single().maxWeightKg, 0.0)
            assertNull("one point is not a trend", flyView.progression.trend)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a deleted measurement leaves the chart and the log`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }

            useCases.log(TestData.date(2026, 10, 1), 82.4)
            useCases.log(today, 82.7)
            val loaded = awaitState { it.bodyLog.size == 2 }

            viewModel.askDeleteEntry(loaded.bodyLog.first())
            awaitState { it.pendingDelete != null }
            viewModel.confirmDelete()

            val after = awaitState {
                it.pendingDelete == null && it.bodyLog.size == 1 && it.bodyStats.count == 1
            }
            assertEquals(1, after.bodyStats.count)
            assertEquals(82.4, after.bodyStats.maxKg!!, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `one point draws a point but no trend`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press").id
        saveWorkout(today, exerciseId = bench, weight = 80.0)

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.progression.points.size == 1 }

            assertNull(state.progression.trend)
            assertEquals(80.0, state.progression.points.single().maxWeightKg, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exercise with no logged set has an empty progression`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press").id
        // A finished workout that skipped the exercise contributes no point.
        historyRepository.saveSession(
            TestData.session(
                date = today,
                exercises = listOf(
                    TestData.exerciseLog(exerciseId = bench, wasCompleted = false, sets = emptyList()),
                ),
            ),
        )

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.exercises.isNotEmpty() }

            assertTrue(state.progression.isEmpty)
            assertNull(state.progression.trend)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun saveWorkout(
        date: LocalDate,
        exerciseId: Long = 0,
        weight: Double = 80.0,
    ): Long = historyRepository.saveSession(
        TestData.session(
            date = date,
            exercises = listOf(
                TestData.exerciseLog(
                    exerciseId = exerciseId.takeIf { it != 0L },
                    sets = listOf(TestData.setLog(weight = weight, reps = 8)),
                ),
            ),
        ),
    )
}
