package com.ggym.app.data.room

import android.database.sqlite.SQLiteConstraintException
import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.testing.RoomTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.givenExercise
import com.ggym.app.testing.givenExerciseLog
import com.ggym.app.testing.givenPlannedExercise
import com.ggym.app.testing.givenRoutine
import com.ggym.app.testing.givenRoutineDay
import com.ggym.app.testing.givenSession
import com.ggym.app.testing.givenSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dictionary table: the two rules that protect its identity (one name, case-insensitively) and
 * the two foreign keys that decide what a delete does to everything pointing at it.
 */
class ExerciseDaoTest : RoomTest() {

    @Test
    fun `the dictionary is ordered by name, ignoring case`() = runTest {
        database.givenExercise(name = "squat")
        database.givenExercise(name = "Bench Press")
        database.givenExercise(name = "deadlift")

        assertEquals(
            listOf("Bench Press", "deadlift", "squat"),
            database.exerciseDao().observeAll().first().map { it.name },
        )
    }

    @Test
    fun `the same name in a different case is refused by the index`() = runTest {
        database.givenExercise(name = "Bench Press")

        val failure = runCatching {
            database.exerciseDao().insert(
                ExerciseEntity(name = "bench press", currentWorkingWeight = null, defaultRestSeconds = 90),
            )
        }.exceptionOrNull()

        assertTrue("expected the unique index to refuse it, got $failure", failure is SQLiteConstraintException)
    }

    @Test
    fun `a conflicting insert reports -1 instead of failing when the caller asks it to`() = runTest {
        database.givenExercise(name = "Bench Press")

        val id = database.exerciseDao().insertIgnoringNameConflicts(
            ExerciseEntity(name = "BENCH PRESS", currentWorkingWeight = 100.0, defaultRestSeconds = 60),
        )

        assertEquals(-1L, id)
        assertEquals(1, database.exerciseDao().getAll().size)
    }

    @Test
    fun `an exact lookup ignores case`() = runTest {
        database.givenExercise(name = "Bench Press")

        assertEquals("Bench Press", database.exerciseDao().findByName("bench press")?.name)
        assertNull(database.exerciseDao().findByName("Bench Press Incline"))
    }

    @Test
    fun `autocomplete matches the beginning of the name`() = runTest {
        database.givenExercise(name = "Bench Press")
        database.givenExercise(name = "Bench Press Incline")
        database.givenExercise(name = "Squat")

        val matches = database.exerciseDao().searchByName("bench%").first().map { it.name }

        assertEquals(listOf("Bench Press", "Bench Press Incline"), matches)
    }

    @Test
    fun `autocomplete can look for a literal percent sign`() = runTest {
        database.givenExercise(name = "100% Effort")
        database.givenExercise(name = "Bench Press")

        // Without ESCAPE the '%' in the user's text would match anything.
        assertEquals(
            listOf("100% Effort"),
            database.exerciseDao().searchByName("100\\%%").first().map { it.name },
        )
    }

    @Test
    fun `the working weight can be written in place`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press", currentWorkingWeight = null)

        database.exerciseDao().updateCurrentWorkingWeight(exerciseId, 85.0)

        assertEquals(85.0, database.exerciseDao().getAll().single().currentWorkingWeight!!, 0.0)
    }

    @Test
    fun `an exercise a routine plans cannot be deleted`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = database.givenRoutine(name = "Push Day")
        val dayId = database.givenRoutineDay(routineId = routineId, dayName = "Push")
        database.givenPlannedExercise(routineDayId = dayId, exerciseId = exerciseId)

        val failure = runCatching { database.exerciseDao().deleteById(exerciseId) }.exceptionOrNull()

        assertTrue("expected RESTRICT to refuse the delete, got $failure", failure is SQLiteConstraintException)
        assertEquals(1, database.exerciseDao().getAll().size)
    }

    @Test
    fun `an exercise is deletable once no routine plans it`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = database.givenRoutine(name = "Push Day")
        val dayId = database.givenRoutineDay(routineId = routineId, dayName = "Push")
        val plannedId = database.givenPlannedExercise(routineDayId = dayId, exerciseId = exerciseId)

        database.plannedExerciseDao().deleteById(plannedId)
        database.exerciseDao().deleteById(exerciseId)

        assertEquals(emptyList<ExerciseEntity>(), database.exerciseDao().getAll())
    }

    @Test
    fun `deleting an exercise detaches history but never deletes it`() = runTest {
        val exerciseId = database.givenExercise(name = "Deltoid Raise")
        val sessionId = database.givenSession(date = TestData.date())
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId, exerciseNameSnapshot = "Deltoid Raise")
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 12.5, reps = 12)

        database.exerciseDao().deleteById(exerciseId)

        val log = database.workoutHistoryDao().getAllSessions().single().exercises.single().log
        assertNull("the id is detached", log.exerciseId)
        assertEquals("the snapshot keeps it readable", "Deltoid Raise", log.exerciseNameSnapshot)
        assertEquals(1, database.workoutHistoryDao().getAllSessions().single().exercises.single().sets.size)
    }

    @Test
    fun `the routines that plan an exercise are listed once each`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val pushId = database.givenRoutine(name = "Push Day")
        val pullId = database.givenRoutine(name = "Pull Day")
        val pushDayId = database.givenRoutineDay(routineId = pushId, dayName = "Push A")
        val pushDayBId = database.givenRoutineDay(routineId = pushId, dayName = "Push B", orderIndex = 1)
        database.givenPlannedExercise(routineDayId = pushDayId, exerciseId = exerciseId)
        database.givenPlannedExercise(routineDayId = pushDayBId, exerciseId = exerciseId)

        val pullDayId = database.givenRoutineDay(routineId = pullId, dayName = "Pull")
        val otherExerciseId = database.givenExercise(name = "Barbell Row")
        database.givenPlannedExercise(routineDayId = pullDayId, exerciseId = otherExerciseId)

        assertEquals(listOf("Push Day"), database.exerciseDao().routineNamesUsing(exerciseId))
    }
}
