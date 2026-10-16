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

    @Test
    fun `a workout in progress is written when it starts and is not history`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")

        val draftId = historyRepository.startDraft(
            TestData.session(
                date = TestData.date(day = 5),
                startedAtMillis = 1_759_000_000_000,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        restSeconds = 150,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 8, isCompleted = false),
                            TestData.setLog(setNumber = 2, weight = 80.0, reps = 8, isCompleted = false),
                        ),
                    ),
                ),
            ),
        )

        val draft = historyRepository.observeDraft().first()!!

        assertTrue("starting a workout returns its id", draftId > 0)
        assertEquals(draftId, draft.id)
        assertTrue(draft.isDraft)
        assertEquals("the workout knows when it began", 1_759_000_000_000, draft.startedAtMillis)
        assertEquals("the rest time rode along, so a resume needs no lookup", 150, draft.exercises.single().restSeconds)
        assertEquals(listOf(1, 2), draft.exercises.single().sets.map { it.setNumber })
        assertTrue("the sets are prefills, not lifts", draft.exercises.single().sets.none { it.isCompleted })
        assertEquals("the log knows which workout it belongs to", draftId, draft.exercises.single().sessionId)
        assertEquals(
            "each prefill points at its own exercise log",
            draft.exercises.single().id,
            draft.exercises.single().sets.first().workoutExerciseLogId,
        )

        assertEquals("the screen watches the workout it was opened on", draftId, historyRepository.observeDraft(draftId).first()!!.id)
        assertNull("a draft is not a session to inspect", historyRepository.observeSession(draftId).first())
        assertTrue(
            "and it is not on the calendar",
            historyRepository.observeSessionsInRange(TestData.date(month = 1, day = 1), TestData.date(month = 12, day = 31)).first().isEmpty(),
        )
    }

    @Test
    fun `finishing a workout writes it as history and removes the draft, in one step`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val draftId = historyRepository.startDraft(
            TestData.session(
                date = TestData.date(day = 5),
                startedAtMillis = 1_759_000_000_000,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 80.0, reps = 8, isCompleted = false)),
                    ),
                ),
            ),
        )

        val finishedId = historyRepository.finishDraft(
            draftId,
            TestData.session(
                date = TestData.date(day = 5),
                durationSeconds = 3600,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 82.5, reps = 8, isCompleted = true)),
                    ),
                ),
            ),
        )

        assertTrue("the finished workout is a new row, not the draft renamed", finishedId != draftId)
        assertNull("the draft is gone", historyRepository.observeDraft().first())
        assertEquals("exactly one workout exists", 1, database.countRows("workout_session"))
        assertEquals(1, database.countRows("workout_exercise_log"))
        assertEquals(1, database.countRows("workout_set_log"))

        val saved = historyRepository.observeSession(finishedId).first()!!
        assertEquals(false, saved.isDraft)
        assertEquals(3600L, saved.durationSeconds)
        assertEquals(82.5, saved.exercises.single().sets.single().weight, 0.0)
        assertEquals("what was actually lifted is now what I lifted last", 82.5, historyRepository.latestCompletedSet(exerciseId)!!.weightKg, 0.0)
    }

    @Test
    fun `an id on the session handed to finish is ignored`() = runTest {
        val draftId = historyRepository.startDraft(TestData.session(startedAtMillis = 1_759_000_000_000))

        val finishedId = historyRepository.finishDraft(draftId, TestData.session(id = 99, durationSeconds = 60))

        assertTrue(finishedId != 99L)
        assertNull(historyRepository.observeSession(99).first())
        assertNotNull(historyRepository.observeSession(finishedId).first())
    }

    @Test
    fun `discarding a workout in progress leaves no trace of it`() = runTest {
        val draftId = historyRepository.startDraft(
            TestData.session(
                startedAtMillis = 1_759_000_000_000,
                exercises = listOf(
                    TestData.exerciseLog(exerciseId = null, sets = listOf(TestData.setLog(isCompleted = false))),
                ),
            ),
        )
        val keptId = historyRepository.saveSession(TestData.session(date = TestData.date(day = 6)))

        historyRepository.discardDraft(draftId)

        assertNull(historyRepository.observeDraft().first())
        assertNull(historyRepository.observeDraft(draftId).first())
        assertEquals(1, database.countRows("workout_session"))
        assertEquals(0, database.countRows("workout_exercise_log"))
        assertEquals(0, database.countRows("workout_set_log"))
        assertNotNull("the saved workout is untouched", historyRepository.observeSession(keptId).first())
    }

    @Test
    fun `sets can be added to a workout in progress and corrected while it runs`() = runTest {
        val draftId = historyRepository.startDraft(
            TestData.session(
                startedAtMillis = 1_759_000_000_000,
                exercises = listOf(TestData.exerciseLog(exerciseId = null, sets = listOf(TestData.setLog(setNumber = 1, isCompleted = false)))),
            ),
        )
        val logId = historyRepository.observeDraft().first()!!.exercises.single().id

        val newSetId = historyRepository.appendSet(
            workoutExerciseLogId = logId,
            set = TestData.setLog(setNumber = 2, weight = 82.5, reps = 8, isCompleted = false),
        )
        assertTrue("the new set has an id of its own", newSetId > 0)

        var sets = historyRepository.observeDraft(draftId).first()!!.exercises.single().sets
        assertEquals("the added set is there, after the one that was", listOf(1, 2), sets.map { it.setNumber })
        assertTrue(sets.all { it.workoutExerciseLogId == logId })

        val added = sets.single { it.id == newSetId }
        historyRepository.updateSet(added.copy(weight = 85.0, reps = 6, isCompleted = true))
        historyRepository.deleteSet(sets.first { it.id != newSetId }.id)

        sets = historyRepository.observeDraft(draftId).first()!!.exercises.single().sets
        assertEquals("one set left, the one that was corrected", listOf(newSetId), sets.map { it.id })
        assertEquals(85.0, sets.single().weight, 0.0)
        assertEquals(true, sets.single().isCompleted)
    }

    @Test
    fun `several sets are written as one`() = runTest {
        val draftId = historyRepository.startDraft(
            TestData.session(
                startedAtMillis = 1_759_000_000_000,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = null,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 8, isCompleted = false),
                            TestData.setLog(setNumber = 2, weight = 80.0, reps = 8, isCompleted = false),
                        ),
                    ),
                ),
            ),
        )
        val sets = historyRepository.observeDraft(draftId).first()!!.exercises.single().sets

        historyRepository.updateSets(
            listOf(
                sets[0].copy(weight = 85.0, reps = 9, isCompleted = true),
                sets[1].copy(weight = 85.0),
            ),
        )

        val stored = historyRepository.observeDraft(draftId).first()!!.exercises.single().sets
        assertEquals(listOf(85.0, 85.0), stored.map { it.weight })
        assertEquals(listOf(9, 8), stored.map { it.reps })
        assertEquals(listOf(true, false), stored.map { it.isCompleted })
    }

    @Test
    fun `the write path decides whether a session is a draft`() = runTest {
        // The models' flag is what a read reports; the method is what a write means. A finished
        // session cannot end up on the workout screen and a draft cannot end up on the calendar.
        val savedId = historyRepository.saveSession(TestData.session(isDraft = true))
        assertNull("saveSession writes history, whatever the model said", historyRepository.observeDraft().first())
        assertNotNull(historyRepository.observeSession(savedId).first())

        val draftId = historyRepository.startDraft(TestData.session(isDraft = false))
        assertNotNull("startDraft writes a workout in progress", historyRepository.observeDraft().first())
        assertNull(historyRepository.observeSession(draftId).first())
    }
}
