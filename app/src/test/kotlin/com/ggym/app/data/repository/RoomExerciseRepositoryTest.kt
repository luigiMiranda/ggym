package com.ggym.app.data.repository

import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.repository.DeleteExerciseResult
import com.ggym.app.testing.RepositoryTest
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
 * The dictionary as the rest of the app sees it: names that cannot fork, an autocomplete that
 * treats the user's text literally, and a delete that refuses rather than damages.
 */
class RoomExerciseRepositoryTest : RepositoryTest() {

    @Test
    fun `an exercise is created once and then reused`() = runTest {
        val created = exerciseRepository.findOrCreate("Bench Press")
        val reused = exerciseRepository.findOrCreate("Bench Press")

        assertEquals(created.id, reused.id)
        assertEquals(1, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun `reusing a name ignores case and never resets the weight it carries`() = runTest {
        val created = exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.updateCurrentWorkingWeight(created.id, 82.5)

        val reused = exerciseRepository.findOrCreate("BENCH PRESS")

        assertEquals(created.id, reused.id)
        assertEquals("the stored weight survives a reuse", 82.5, reused.currentWorkingWeight!!, 0.0)
    }

    @Test
    fun `names are trimmed before they are stored or looked up`() = runTest {
        val created = exerciseRepository.findOrCreate("  Squat  ")

        assertEquals("Squat", created.name)
        assertEquals(created.id, exerciseRepository.findOrCreate("Squat").id)
        assertEquals(created.id, exerciseRepository.findByName(" squat ")?.id)
    }

    @Test
    fun `an exercise without a name is a programming error, not a row`() = runTest {
        val failure = runCatching { exerciseRepository.findOrCreate("   ") }.exceptionOrNull()

        assertTrue("expected a refusal, got $failure", failure is IllegalArgumentException)
        assertEquals(0, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun `autocomplete matches the beginning of the name, ignoring case`() = runTest {
        exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.findOrCreate("Bench Press Incline")
        exerciseRepository.findOrCreate("Squat")

        assertEquals(
            listOf("Bench Press", "Bench Press Incline"),
            exerciseRepository.searchByName("bench").first().map { it.name },
        )
    }

    @Test
    fun `a typed wildcard looks for a character, not for everything`() = runTest {
        exerciseRepository.findOrCreate("100% Effort")
        exerciseRepository.findOrCreate("100kg Effort")
        exerciseRepository.findOrCreate("Bench_2")
        exerciseRepository.findOrCreate("BenchX2")

        assertEquals(
            listOf("100% Effort"),
            exerciseRepository.searchByName("100%").first().map { it.name },
        )
        assertEquals(
            listOf("Bench_2"),
            exerciseRepository.searchByName("bench_").first().map { it.name },
        )
    }

    @Test
    fun `an empty search shows the whole dictionary`() = runTest {
        exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.findOrCreate("Squat")

        assertEquals(2, exerciseRepository.searchByName("   ").first().size)
    }

    @Test
    fun `the dictionary is handed out in name order, ignoring case`() = runTest {
        exerciseRepository.findOrCreate("squat")
        exerciseRepository.findOrCreate("Bench Press")

        assertEquals(listOf("Bench Press", "squat"), exerciseRepository.observeAll().first().map { it.name })
    }

    @Test
    fun `saving creates a new exercise and then updates it by id`() = runTest {
        val created = exerciseRepository.save(Exercise(name = "Squat", currentWorkingWeight = 100.0, defaultRestSeconds = 120))

        assertTrue("a new row gets an id", created.id > 0)

        val updated = exerciseRepository.save(created.copy(name = "Back Squat", currentWorkingWeight = 105.0))

        assertEquals(created.id, updated.id)
        assertEquals("Back Squat", exerciseRepository.findByName("Back Squat")?.name)
        assertEquals(1, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun `the current working weight is readable right after it is written`() = runTest {
        val created = exerciseRepository.findOrCreate("Bench Press")
        assertNull(created.currentWorkingWeight)

        exerciseRepository.updateCurrentWorkingWeight(created.id, 85.0)

        assertEquals(85.0, exerciseRepository.findByName("Bench Press")?.currentWorkingWeight!!, 0.0)
    }

    @Test
    fun `deleting an exercise that a routine plans is refused, and the routines are named`() = runTest {
        val exerciseId = exerciseRepository.findOrCreate("Bench Press").id
        val routineId = database.givenRoutine(name = "Push Day")
        val dayId = database.givenRoutineDay(routineId = routineId, dayName = "Push")
        database.givenPlannedExercise(routineDayId = dayId, exerciseId = exerciseId)

        val result = exerciseRepository.deleteExercise(exerciseId)

        assertEquals(DeleteExerciseResult.Blocked(listOf("Push Day")), result)
        assertEquals("nothing was deleted", 1, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun `every routine that uses the exercise is named, once each`() = runTest {
        val exerciseId = exerciseRepository.findOrCreate("Bench Press").id
        val pushId = database.givenRoutine(name = "Push Day")
        val pullId = database.givenRoutine(name = "Pull Day", createdAt = 1_750_000_000_001)
        database.givenPlannedExercise(routineDayId = database.givenRoutineDay(routineId = pushId, dayName = "Push A"), exerciseId = exerciseId)
        database.givenPlannedExercise(routineDayId = database.givenRoutineDay(routineId = pushId, dayName = "Push B", orderIndex = 1), exerciseId = exerciseId)
        database.givenPlannedExercise(routineDayId = database.givenRoutineDay(routineId = pullId, dayName = "Pull"), exerciseId = exerciseId)

        assertEquals(
            DeleteExerciseResult.Blocked(listOf("Pull Day", "Push Day")),
            exerciseRepository.deleteExercise(exerciseId),
        )
    }

    @Test
    fun `an exercise no routine plans is deleted, and history keeps its snapshot`() = runTest {
        val exerciseId = exerciseRepository.findOrCreate("Deltoid Raise").id
        val sessionId = database.givenSession(date = TestData.date())
        val logId = database.givenExerciseLog(sessionId = sessionId, exerciseId = exerciseId, exerciseNameSnapshot = "Deltoid Raise")
        database.givenSet(workoutExerciseLogId = logId, setNumber = 1, weight = 12.5, reps = 12)

        assertEquals(DeleteExerciseResult.Deleted, exerciseRepository.deleteExercise(exerciseId))

        assertNull(exerciseRepository.findByName("Deltoid Raise"))
        val log = database.workoutHistoryDao().getAllSessions().single().exercises.single()
        assertNull("history is detached, not deleted", log.log.exerciseId)
        assertEquals("Deltoid Raise", log.log.exerciseNameSnapshot)
        assertEquals(1, log.sets.size)
    }

    @Test
    fun `deleting an exercise that does not exist is not an error`() = runTest {
        assertEquals(DeleteExerciseResult.Deleted, exerciseRepository.deleteExercise(4242))
    }

    @Test
    fun `an exercise created by hand is found by the repository`() = runTest {
        database.givenExercise(name = "Deadlift", currentWorkingWeight = 140.0)

        val found = exerciseRepository.findByName("deadlift")

        assertEquals("Deadlift", found?.name)
        assertEquals(found?.id, exerciseRepository.findOrCreate("DEADLIFT").id)
    }
}
