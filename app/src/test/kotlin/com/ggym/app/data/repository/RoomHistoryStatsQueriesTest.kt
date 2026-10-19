package com.ggym.app.data.repository

import com.ggym.app.domain.model.LoggedSetPoint
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.givenExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The two reads the Stats tab added, on the real schema.
 *
 * They are read-only and answer cheap questions — which days had a workout, what one exercise's
 * completed sets were — so the tests are about the filters: no drafts, no pre-filled sets, no
 * other exercise, and the range's ends included.
 */
class RoomHistoryStatsQueriesTest : RepositoryTest() {

    private val from: LocalDate = TestData.date(2026, 10, 1)
    private val to: LocalDate = TestData.date(2026, 10, 14)

    @Test
    fun `session dates include both ends and leave drafts out`() = runTest {
        historyRepository.saveSession(TestData.session(date = TestData.date(2026, 9, 30)))
        historyRepository.saveSession(TestData.session(date = from))
        historyRepository.saveSession(TestData.session(date = to))
        historyRepository.saveSession(TestData.session(date = TestData.date(2026, 10, 15)))
        historyRepository.startDraft(
            TestData.session(
                date = TestData.date(2026, 10, 10),
                isDraft = true,
                startedAtMillis = 1_760_000_000_000,
            ),
        )

        assertEquals(
            listOf(from, to),
            historyRepository.observeSessionDates(from, to).first(),
        )
    }

    @Test
    fun `two sessions on one day appear twice - distinct days are the caller's rule`() = runTest {
        historyRepository.saveSession(TestData.session(date = to))
        historyRepository.saveSession(TestData.session(date = to))

        assertEquals(listOf(to, to), historyRepository.observeSessionDates(from, to).first())
    }

    @Test
    fun `completed sets for one exercise exclude drafts, prefills and other exercises`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")

        val first = historyRepository.saveSession(
            TestData.session(
                date = LocalDate.of(2026, 10, 1),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench,
                        exerciseNameSnapshot = "Bench Press",
                        orderIndex = 0,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0),
                            TestData.setLog(setNumber = 2, weight = 82.5),
                            TestData.setLog(setNumber = 3, weight = 90.0, isCompleted = false),
                        ),
                    ),
                    TestData.exerciseLog(
                        exerciseId = fly,
                        exerciseNameSnapshot = "Cable Fly",
                        orderIndex = 1,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 20.0)),
                    ),
                ),
            ),
        )
        val second = historyRepository.saveSession(
            TestData.session(
                date = LocalDate.of(2026, 10, 8),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench,
                        exerciseNameSnapshot = "Bench Press",
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 85.0)),
                    ),
                ),
            ),
        )

        // A workout in progress with a completed set: it has not happened yet, so it is not a point.
        val draftSet = TestData.setLog(setNumber = 1, weight = 100.0)
        historyRepository.startDraft(
            TestData.session(
                date = LocalDate.of(2026, 10, 10),
                isDraft = true,
                startedAtMillis = 1_760_000_000_000,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench,
                        exerciseNameSnapshot = "Bench Press",
                        sets = listOf(draftSet),
                    ),
                ),
            ),
        )

        val points: List<LoggedSetPoint> = historyRepository.observeCompletedSetsForExercise(bench).first()

        assertEquals(listOf(80.0, 82.5, 85.0), points.map { it.weightKg })
        assertEquals(
            listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8)),
            points.map { it.date },
        )
        assertEquals(listOf(first, first, second), points.map { it.sessionId })
    }

    @Test
    fun `an exercise with no history has no points`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")

        assertEquals(emptyList<LoggedSetPoint>(), historyRepository.observeCompletedSetsForExercise(bench).first())
    }

    @Test
    fun `deleting a session takes its progression points with it`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = LocalDate.of(2026, 10, 1),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench,
                        sets = listOf(TestData.setLog(weight = 80.0)),
                    ),
                ),
            ),
        )

        assertEquals(1, historyRepository.observeCompletedSetsForExercise(bench).first().size)

        historyRepository.deleteSession(sessionId)

        assertEquals(emptyList<LoggedSetPoint>(), historyRepository.observeCompletedSetsForExercise(bench).first())
        assertEquals(emptyList<LocalDate>(), historyRepository.observeSessionDates(from, to).first())
    }
}
