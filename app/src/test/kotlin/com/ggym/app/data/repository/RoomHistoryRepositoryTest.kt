package com.ggym.app.data.repository

import app.cash.turbine.test
import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Training history as the workout screen and the calendar will use it: a session saved as one
 * tree, a date range that means whole days, and a correction path for a past workout.
 */
class RoomHistoryRepositoryTest : RepositoryTest() {

    @Test
    fun `a session is saved with its exercises and sets, and comes back in training order`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(day = 5),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        exerciseNameSnapshot = "Cable Fly",
                        orderIndex = 1,
                        sets = listOf(TestData.setLog(setNumber = 2, weight = 20.0, reps = 12), TestData.setLog(setNumber = 1, weight = 20.0, reps = 12)),
                    ),
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        exerciseNameSnapshot = "Bench Press",
                        orderIndex = 0,
                        sets = listOf(
                            TestData.setLog(setNumber = 3, weight = 85.0, reps = 6),
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                            TestData.setLog(setNumber = 2, weight = 82.5, reps = 8),
                        ),
                    ),
                ),
            ),
        )

        assertTrue("saving returns the new session id", sessionId > 0)

        val stored = historyRepository.observeSession(sessionId).first()!!

        assertEquals(TestData.date(day = 5), stored.date)
        assertEquals(listOf(0, 1), stored.exercises.map { it.orderIndex })
        assertEquals(listOf("Bench Press", "Cable Fly"), stored.exercises.map { it.exerciseNameSnapshot })
        assertEquals(listOf(1, 2, 3), stored.exercises[0].sets.map { it.setNumber })
        assertEquals(listOf(1, 2), stored.exercises[1].sets.map { it.setNumber })
        assertEquals(85.0, stored.exercises[0].sets.last().weight, 0.0)
        assertTrue("every set points at its own exercise log", stored.exercises.all { log -> log.sets.all { it.workoutExerciseLogId == log.id } })
    }

    @Test
    fun `a session id handed in by the caller is ignored - a workout is written once`() = runTest {
        val sessionId = historyRepository.saveSession(TestData.session(id = 99, date = TestData.date(day = 5)))

        assertTrue(sessionId != 99L)
        assertNull(historyRepository.observeSession(99).first())
        assertEquals(1, database.countRows("workout_session"))
    }

    @Test
    fun `a session with nothing logged is still a session`() = runTest {
        val sessionId = historyRepository.saveSession(TestData.session(date = TestData.date(day = 5)))

        val stored = historyRepository.observeSession(sessionId).first()
        assertNotNull(stored)
        assertTrue(stored!!.exercises.isEmpty())
        assertEquals(0, database.countRows("workout_exercise_log"))
    }

    @Test
    fun `a skipped exercise is stored with no completed sets`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(day = 5),
                exercises = listOf(
                    TestData.exerciseLog(exerciseId = exerciseId, wasCompleted = false, sets = listOf(TestData.setLog(isCompleted = false))),
                ),
            ),
        )

        val stored = historyRepository.observeSession(sessionId).first()!!
        assertEquals(false, stored.exercises.single().wasCompleted)
        assertEquals(false, stored.exercises.single().sets.single().isCompleted)
    }

    @Test
    fun `a log keeps a snapshot of the name even when the dictionary entry is gone`() = runTest {
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(day = 5),
                exercises = listOf(TestData.exerciseLog(exerciseId = null, exerciseNameSnapshot = "Deltoid Raise")),
            ),
        )

        val stored = historyRepository.observeSession(sessionId).first()!!

        assertNull(stored.exercises.single().exerciseId)
        assertEquals("Deltoid Raise", stored.exercises.single().exerciseNameSnapshot)
    }

    @Test
    fun `a date range includes both of its ends`() = runTest {
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 4)))
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 5)))
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 6)))
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 7)))

        val sessions = historyRepository
            .observeSessionsInRange(from = TestData.date(day = 5), to = TestData.date(day = 6))
            .first()

        assertEquals(listOf(TestData.date(day = 5), TestData.date(day = 6)), sessions.map { it.date })
    }

    @Test
    fun `sessions in a range come back oldest first`() = runTest {
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 7)))
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 5)))
        historyRepository.saveSession(TestData.session(date = TestData.date(day = 6)))

        val sessions = historyRepository
            .observeSessionsInRange(from = TestData.date(day = 1), to = TestData.date(day = 31))
            .first()

        assertEquals(listOf(5, 6, 7), sessions.map { it.date.dayOfMonth })
    }

    @Test
    fun `a saved workout reaches the calendar that is already watching`() = runTest {
        historyRepository
            .observeSessionsInRange(from = TestData.date(day = 1), to = TestData.date(day = 31))
            .test {
                assertTrue(awaitItem().isEmpty())

                historyRepository.saveSession(TestData.session(date = TestData.date(day = 5)))
                assertEquals(listOf(5), awaitItem().map { it.date.dayOfMonth })

                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun `the last completed set is the answer to what should I lift next`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(month = 6, day = 1),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                            TestData.setLog(setNumber = 2, weight = 85.0, reps = 6),
                        ),
                    ),
                ),
            ),
        )

        val latest = historyRepository.latestCompletedSet(exerciseId)

        assertEquals(85.0, latest?.weightKg!!, 0.0)
        assertEquals(6, latest.reps)
        assertEquals(2, latest.setNumber)
        assertEquals(sessionId, latest.sessionId)
        assertEquals(TestData.date(month = 6, day = 1), latest.date)
    }

    @Test
    fun `an exercise that was never performed has no last set`() = runTest {
        assertNull(historyRepository.latestCompletedSet(4242))
    }

    @Test
    fun `a set is corrected by rewriting it`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(day = 5),
                exercises = listOf(
                    TestData.exerciseLog(exerciseId = exerciseId, sets = listOf(TestData.setLog(setNumber = 1, weight = 80.0, reps = 10))),
                ),
            ),
        )
        val stored = historyRepository.observeSession(sessionId).first()!!.exercises.single().sets.single()

        historyRepository.updateSet(stored.copy(weight = 82.5, reps = 8))

        val corrected = historyRepository.observeSession(sessionId).first()!!.exercises.single().sets.single()
        assertEquals(82.5, corrected.weight, 0.0)
        assertEquals(8, corrected.reps)
        assertEquals("the set is still the same row", stored.id, corrected.id)
    }

    @Test
    fun `a wrongly logged set can be removed without losing the session`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(day = 5),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                            TestData.setLog(setNumber = 2, weight = 300.0, reps = 1),
                        ),
                    ),
                ),
            ),
        )
        val mistaken = historyRepository.observeSession(sessionId).first()!!.exercises.single().sets.last()

        historyRepository.deleteSet(mistaken.id)

        val remaining = historyRepository.observeSession(sessionId).first()
        assertNotNull(remaining)
        assertEquals(listOf(1), remaining!!.exercises.single().sets.map { it.setNumber })
        assertEquals(1, database.countRows("workout_set_log"))
    }

    @Test
    fun `deleting a session deletes everything inside it`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val doomedId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(day = 5),
                exercises = listOf(TestData.exerciseLog(exerciseId = exerciseId, sets = listOf(TestData.setLog()))),
            ),
        )
        val keptId = historyRepository.saveSession(TestData.session(date = TestData.date(day = 6)))

        historyRepository.deleteSession(doomedId)

        assertNull(historyRepository.observeSession(doomedId).first())
        assertEquals(1, database.countRows("workout_session"))
        assertEquals(0, database.countRows("workout_exercise_log"))
        assertEquals(0, database.countRows("workout_set_log"))
        assertNotNull(historyRepository.observeSession(keptId).first())
    }

    @Test
    fun `a saved session is a complete snapshot of the day it belongs to`() = runTest {
        val session = WorkoutSession(
            date = LocalDate.of(2026, 3, 8),
            routineNameSnapshot = "Push Day",
            dayNameSnapshot = "Push",
            durationSeconds = 3600,
            exercises = listOf(
                WorkoutExerciseLog(
                    exerciseId = null,
                    sessionId = 0,
                    exerciseNameSnapshot = "Weighted Dip",
                    wasCompleted = true,
                    orderIndex = 0,
                    sets = listOf(
                        WorkoutSetLog(
                            workoutExerciseLogId = 0,
                            setNumber = 1,
                            weight = 0.0,
                            reps = 15,
                            isCompleted = true,
                        ),
                    ),
                ),
            ),
        )

        val sessionId = historyRepository.saveSession(session)
        val stored = historyRepository.observeSession(sessionId).first()!!

        assertEquals(session.date, stored.date)
        assertEquals("Push Day", stored.routineNameSnapshot)
        assertEquals(3600L, stored.durationSeconds)
        assertEquals(1, database.countRows("workout_session"))
    }
}
