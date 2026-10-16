package com.ggym.app.data.room

import app.cash.turbine.test
import com.ggym.app.testing.RoomTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenExercise
import com.ggym.app.testing.givenExerciseLog
import com.ggym.app.testing.givenSession
import com.ggym.app.testing.givenSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Training history: the tree query, the half-open date range the calendar uses, the cascade that
 * keeps a deleted session from leaving sets behind, and the query behind "current working weight".
 */
class WorkoutHistoryDaoTest : RoomTest() {

    @Test
    fun `a session comes back with its exercises and its sets`() = runTest {
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseNameSnapshot = "Bench Press")
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 80.0, reps = 10)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 2, weight = 82.5, reps = 8)

        val row = database.workoutHistoryDao().getAllSessions().single()

        assertEquals(TestData.timestamp(TestData.date(day = 5)), row.session.dateTimestamp)
        assertEquals("Bench Press", row.exercises.single().log.exerciseNameSnapshot)
        assertEquals(listOf(1, 2), row.exercises.single().sets.map { it.setNumber })
    }

    @Test
    fun `the range stops at the midnight that starts the last day, so callers pass an exclusive end`() = runTest {
        database.givenSession(date = TestData.date(day = 4))
        database.givenSession(date = TestData.date(day = 5))
        database.givenSession(date = TestData.date(day = 6))

        val range = database.workoutHistoryDao()
            .observeSessionsInRange(
                fromInclusive = TestData.timestamp(TestData.date(day = 5)),
                toExclusive = TestData.timestamp(TestData.date(day = 6)),
            )
            .first()

        assertEquals(listOf(TestData.timestamp(TestData.date(day = 5))), range.map { it.session.dateTimestamp })
    }

    @Test
    fun `sessions in a range come back oldest first`() = runTest {
        database.givenSession(date = TestData.date(day = 7))
        database.givenSession(date = TestData.date(day = 5))
        database.givenSession(date = TestData.date(day = 6))

        val range = database.workoutHistoryDao()
            .observeSessionsInRange(fromInclusive = 0, toExclusive = Long.MAX_VALUE)
            .first()

        assertEquals(listOf(5, 6, 7), range.map { java.time.Instant.ofEpochMilli(it.session.dateTimestamp).atZone(TestData.zone).dayOfMonth })
    }

    @Test
    fun `a new session shows up for a collector already watching the range`() = runTest {
        database.workoutHistoryDao()
            .observeSessionsInRange(fromInclusive = 0, toExclusive = Long.MAX_VALUE)
            .test {
                assertEquals(emptyList<Any>(), awaitItem())

                database.givenSession(date = TestData.date(day = 5))
                assertEquals(1, awaitItem().size)

                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun `deleting a session takes its exercises and their sets with it`() = runTest {
        val doomedId = database.givenSession(date = TestData.date(day = 5))
        val doomedLogId = database.givenExerciseLog(sessionId = doomedId)
        database.givenSet(workoutExerciseLogId = doomedLogId)

        val keptId = database.givenSession(date = TestData.date(day = 6))
        val keptLogId = database.givenExerciseLog(sessionId = keptId)
        database.givenSet(workoutExerciseLogId = keptLogId)

        database.workoutHistoryDao().deleteById(doomedId)

        assertEquals(1, database.countRows("workout_session"))
        assertEquals(1, database.countRows("workout_exercise_log"))
        assertEquals(1, database.countRows("workout_set_log"))
        assertEquals(keptId, database.workoutHistoryDao().getAllSessions().single().session.id)
    }

    @Test
    fun `the last completed set comes from the most recent session, not the last row written`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")

        // The newer session is written first, so a query that trusted insertion order would fail.
        val newerSessionId = database.givenSession(date = TestData.date(month = 6, day = 1))
        val newerLogId = database.givenExerciseLog(sessionId = newerSessionId, exerciseId = exerciseId)
        database.givenSet(workoutExerciseLogId = newerLogId, setNumber = 1, weight = 85.0, reps = 8)

        val olderSessionId = database.givenSession(date = TestData.date(month = 1, day = 5))
        val olderLogId = database.givenExerciseLog(sessionId = olderSessionId, exerciseId = exerciseId)
        database.givenSet(workoutExerciseLogId = olderLogId, setNumber = 5, weight = 77.5, reps = 12)

        val latest = database.exerciseProgressDao().latestCompletedSet(exerciseId)

        assertEquals(85.0, latest?.weight!!, 0.0)
        assertEquals(newerSessionId, latest.sessionId)
        assertEquals(TestData.timestamp(TestData.date(month = 6, day = 1)), latest.sessionDateTimestamp)
    }

    @Test
    fun `the last completed set is the highest set number in that session`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 80.0, reps = 10)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 2, weight = 82.5, reps = 9)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 3, weight = 85.0, reps = 6)

        assertEquals(85.0, database.exerciseProgressDao().latestCompletedSet(exerciseId)?.weight!!, 0.0)
    }

    @Test
    fun `sets that were not completed are not a weight the user has lifted`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val doneLogId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId)
        database.givenSet(workoutExerciseLogId = doneLogId, setNumber = 1, weight = 80.0, reps = 10, isCompleted = true)
        database.givenSet(workoutExerciseLogId = doneLogId, setNumber = 2, weight = 90.0, reps = 5, isCompleted = false)

        assertEquals(80.0, database.exerciseProgressDao().latestCompletedSet(exerciseId)?.weight!!, 0.0)
    }

    @Test
    fun `a set completed inside an exercise the user then skipped still counts as lifted`() = runTest {
        // `wasCompleted` on the exercise means "finished the planned work", not "never touched a
        // weight": the set itself is the evidence, and it is the set that counts here.
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId, wasCompleted = false)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 80.0, reps = 10, isCompleted = true)

        assertEquals(80.0, database.exerciseProgressDao().latestCompletedSet(exerciseId)?.weight!!, 0.0)
    }

    @Test
    fun `an exercise with no completed set has no last set`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 80.0, reps = 10, isCompleted = false)

        assertNull(database.exerciseProgressDao().latestCompletedSet(exerciseId))
        assertNull(database.exerciseProgressDao().latestCompletedSet(exerciseId = 999))
    }

    @Test
    fun `a set whose dictionary entry was deleted cannot be anyone's last set`() = runTest {
        val exerciseId = database.givenExercise(name = "Deltoid Raise")
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId)
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 12.5, reps = 12)

        database.exerciseDao().deleteById(exerciseId)

        assertNull(database.exerciseProgressDao().latestCompletedSet(exerciseId))
    }

    @Test
    fun `a set can be corrected and removed`() = runTest {
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId)
        val setId = database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 80.0, reps = 10)

        val stored = database.workoutHistoryDao().getAllSessions().single().exercises.single().sets.single()
        database.workoutHistoryDao().updateSet(stored.copy(weight = 82.5, reps = 8))
        val corrected = database.workoutHistoryDao().getAllSessions().single().exercises.single().sets.single()
        assertEquals(82.5, corrected.weight, 0.0)
        assertEquals(8, corrected.reps)

        database.workoutHistoryDao().deleteSetById(setId)
        assertEquals(0, database.countRows("workout_set_log"))
        assertEquals(1, database.countRows("workout_exercise_log"))
    }

    @Test
    fun `one session can be watched on its own`() = runTest {
        val sessionId = database.givenSession(date = TestData.date(day = 5))
        val logId = database.givenExerciseLog(sessionId = sessionId)
        database.givenSet(workoutExerciseLogId = logId)

        val session = database.workoutHistoryDao().observeSession(sessionId).first()

        assertEquals(LocalDate.of(2026, 1, 5), java.time.Instant.ofEpochMilli(session!!.session.dateTimestamp).atZone(TestData.zone).toLocalDate())
        assertNull(database.workoutHistoryDao().observeSession(4242).first())
    }
}
