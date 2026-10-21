package com.ggym.app.domain.workout

import com.ggym.app.data.repository.RoomExerciseRepository
import com.ggym.app.data.repository.RoomHistoryRepository
import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The milestone gate: a workout that survives everything.
 *
 * Every test here runs against a real in-memory database through the real repositories, because
 * what is being asserted is what the user's phone will hold after the process has died, not what a
 * fake recorded. The numbers in the fixtures are deliberate: the dictionary holds 87.5, the day
 * plans 80.0, and the user completes at 82.5 — a rule that picked the wrong source could still look
 * right whenever two of them agree.
 */
class ActiveWorkoutUseCasesTest : RepositoryTest() {

    private val weights by lazy {
        WorkingWeightUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
        )
    }

    private val useCases by lazy {
        ActiveWorkoutUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
            weights = weights,
            now = { clock },
            zoneId = TestData.zone,
        )
    }

    @Test
    fun `starting a workout freezes the day into a draft`() = runTest {
        val bench = exercise("Bench Press", workingWeight = 87.5)
        val fly = exercise("Cable Fly")
        clock = TestData.timestamp(TestData.date(month = 6, day = 15))
        val dayId = plannedDay(
            Slot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            Slot(exerciseId = fly, sets = 3, reps = "12", restSeconds = 60, weightKg = null),
        )

        val sessionId = started(dayId)

        val draft = draft()
        assertEquals(sessionId, draft.id)
        assertTrue("a workout in progress is a draft", draft.isDraft)
        assertEquals("it belongs to the day it started on", TestData.date(month = 6, day = 15), draft.date)
        assertEquals(clock, draft.startedAtMillis)
        assertEquals(0L, draft.durationSeconds)
        assertEquals("Push Day", draft.routineNameSnapshot)
        assertEquals("Push A", draft.dayNameSnapshot)

        assertEquals(listOf("Bench Press", "Cable Fly"), draft.exercises.map { it.exerciseNameSnapshot })
        assertEquals(listOf(bench, fly), draft.exercises.map { it.exerciseId })
        assertEquals("the rest time rides along, so a resume needs no lookup", listOf(120, 60), draft.exercises.map { it.restSeconds })
        assertEquals(listOf(0, 1), draft.exercises.map { it.orderIndex })
        assertTrue("nothing has been performed yet", draft.exercises.none { it.wasCompleted })

        val benchSets = draft.exercises[0].sets
        assertEquals(4, benchSets.size)
        assertEquals(listOf(1, 2, 3, 4), benchSets.map { it.setNumber })
        assertEquals("the slot's own 80 kg", listOf(80.0, 80.0, 80.0, 80.0), benchSets.map { it.weight })
        assertEquals("8-10 starts at eight", listOf(8, 8, 8, 8), benchSets.map { it.reps })
        assertTrue("every set is a prefill", benchSets.none { it.isCompleted })

        val flySets = draft.exercises[1].sets
        assertEquals(3, flySets.size)
        assertEquals("nothing is known about the fly, so the field starts empty", listOf(0.0, 0.0, 0.0), flySets.map { it.weight })
        assertEquals(listOf(12, 12, 12), flySets.map { it.reps })
    }

    @Test
    fun `a slot with no weight of its own starts from what the exercise last lifted`() = runTest {
        val bench = exercise("Bench Press")
        historyRepository.saveSession(
            TestData.session(
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench,
                        sets = listOf(TestData.setLog(weight = 85.0, reps = 8, isCompleted = true)),
                    ),
                ),
            ),
        )
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "10"))

        started(dayId)

        assertEquals(
            "a performed set is the next best thing to a chosen weight",
            listOf(85.0, 85.0),
            draft().exercises[0].sets.map { it.weight },
        )
    }

    @Test
    fun `a workout that is already running is not started again`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, weightKg = 80.0))

        val first = started(dayId)
        val second = useCases.start(dayId)

        assertEquals("the running workout is named, so the screen can offer to resume it", StartWorkoutResult.AlreadyRunning(first), second)
        assertEquals("one draft, not two", 1, database.countRows("workout_session"))
    }

    @Test
    fun `a day with nothing planned starts nothing`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push A")

        assertEquals(StartWorkoutResult.NothingToStart, useCases.start(dayId))
        assertNull("no draft was written", historyRepository.observeDraft().first())
    }

    @Test
    fun `a day that is gone starts nothing`() = runTest {
        assertEquals(StartWorkoutResult.NothingToStart, useCases.start(9_999))
        assertEquals(0, database.countRows("workout_session"))
    }

    @Test
    fun `the routine can be rearranged and deleted under a running workout`() = runTest {
        val bench = exercise("Bench Press")
        val fly = exercise("Cable Fly")
        val dayId = plannedDay(
            Slot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            Slot(exerciseId = fly, sets = 3, reps = "12", restSeconds = 60),
        )
        started(dayId)

        routineRepository.renameDay(dayId, "Push B")
        routineRepository.deletePlannedExercise(slotsOf(dayId).first().id)
        routineRepository.deleteRoutine(routineRepository.observeRoutines().first().single().id)

        val draft = draft()
        assertEquals("the day's name was frozen when the workout started", "Push A", draft.dayNameSnapshot)
        assertEquals(2, draft.exercises.size)
        assertEquals("the whole plan is in the draft, routine or no routine", 4, draft.exercises[0].sets.size)
        assertEquals(listOf(120, 60), draft.exercises.map { it.restSeconds })
    }

    @Test
    fun `completing a set writes it, moves the working weight and fills the untouched sets`() = runTest {
        val bench = exercise("Bench Press", workingWeight = 70.0)
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120, weightKg = 80.0))
        started(dayId)
        val firstSetId = draft().exercises[0].sets.first().id

        useCases.completeSet(firstSetId, weightKg = 82.5, reps = 9)

        val sets = draft().exercises[0].sets
        assertTrue(sets[0].isCompleted)
        assertEquals(82.5, sets[0].weight, 0.0)
        assertEquals(9, sets[0].reps)
        assertEquals(
            "the sets the user is about to do follow the new weight",
            listOf(82.5, 82.5, 82.5),
            sets.drop(1).map { it.weight },
        )
        assertTrue("but they are still only prefills", sets.drop(1).none { it.isCompleted })
        assertEquals(
            "the dictionary now says what was actually lifted",
            82.5,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertNull("a draft is not history yet", historyRepository.latestCompletedSet(bench))
        assertEquals(
            "the plan stays the plan",
            80.0,
            slotsOf(dayId).single().weightKg!!,
            0.0,
        )
    }

    @Test
    fun `a day with no known weight starts empty and follows the first completed set`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 3, reps = "8-10"))
        started(dayId)

        val sets = draft().exercises[0].sets
        assertTrue("nothing is known, so every set is empty", sets.all { it.weight == 0.0 })

        useCases.completeSet(sets.first().id, weightKg = 85.0, reps = 8)

        assertEquals(
            "one entry, every set behind it",
            listOf(85.0, 85.0),
            draft().exercises[0].sets.drop(1).map { it.weight },
        )
    }

    @Test
    fun `a set the user gave a weight of its own keeps it`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 3, reps = "8-10", weightKg = 80.0))
        started(dayId)
        val sets = draft().exercises[0].sets
        // The screen never writes a prefill (a set's numbers are committed when it is completed,
        // per decision 5), so a differing prefill is a state only a direct write can reach — and
        // the rule covers it all the same: a set carrying something else is not the group default
        // and is left alone.
        historyRepository.updateSet(sets[1].copy(weight = 82.5))

        useCases.completeSet(sets[0].id, weightKg = 85.0, reps = 8)

        assertEquals(listOf(85.0, 82.5, 85.0), draft().exercises[0].sets.map { it.weight })
    }

    @Test
    fun `a bodyweight set moves neither the working weight nor the sets behind it`() = runTest {
        val bench = exercise("Bench Press", workingWeight = 87.5)
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 3, reps = "20", weightKg = 80.0))
        started(dayId)

        useCases.completeSet(draft().exercises[0].sets.first().id, weightKg = 0.0, reps = 20)

        assertEquals(
            "a bodyweight set must not erase a number the user planned with",
            87.5,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertEquals(listOf(0.0, 80.0, 80.0), draft().exercises[0].sets.map { it.weight })
    }

    @Test
    fun `adding a set inherits the last one`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "8-10", weightKg = 80.0))
        started(dayId)
        val logId = draft().exercises[0].id
        useCases.completeSet(draft().exercises[0].sets.first().id, weightKg = 85.0, reps = 10)

        val newSetId = useCases.addSet(logId)

        val sets = draft().exercises[0].sets
        assertEquals(newSetId, sets.last().id)
        assertEquals(3, sets.size)
        assertEquals("it follows the set that was there", 3, sets.last().setNumber)
        assertEquals(85.0, sets.last().weight, 0.0)
        assertEquals("the last set's reps, not the completed one's", 8, sets.last().reps)
        assertFalse(sets.last().isCompleted)
    }

    @Test
    fun `adding a set to an exercise with no sets left starts it empty`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "8-10", weightKg = 80.0))
        started(dayId)
        val logId = draft().exercises[0].id
        draft().exercises[0].sets.forEach { useCases.removeSet(it.id) }
        assertTrue("the user removed every set", draft().exercises[0].sets.isEmpty())

        val newSetId = useCases.addSet(logId)

        val set = draft().exercises[0].sets.single()
        assertEquals(newSetId, set.id)
        assertEquals(1, set.setNumber)
        assertEquals(0.0, set.weight, 0.0)
        assertEquals("there is no number to inherit, so nothing is invented", 0, set.reps)
    }

    @Test
    fun `a set can be removed while it is uncompleted, and the rest keep their numbers`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 4, reps = "8-10", weightKg = 80.0))
        started(dayId)

        val result = useCases.removeSet(draft().exercises[0].sets[1].id)

        assertEquals(RemoveSetResult.Removed, result)
        assertEquals(
            "insertion order survives a removal: the screen shows positions",
            listOf(1, 3, 4),
            draft().exercises[0].sets.map { it.setNumber },
        )
    }

    @Test
    fun `removing a completed set is refused`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "8-10", weightKg = 80.0))
        started(dayId)
        val setId = draft().exercises[0].sets.first().id
        useCases.completeSet(setId, weightKg = 82.5, reps = 8)

        val result = useCases.removeSet(setId)

        assertEquals(RemoveSetResult.RefusedCompleted, result)
        assertTrue("a performed set is not a plan", draft().exercises[0].sets.any { it.id == setId })
    }

    @Test
    fun `removing a set of a workout that has ended changes nothing`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "8-10", weightKg = 80.0))
        val sessionId = started(dayId)
        val setId = draft().exercises[0].sets.first().id
        useCases.discard(sessionId)

        assertEquals(RemoveSetResult.NotFound, useCases.removeSet(setId))
        assertNull("the workout is still gone", historyRepository.observeDraft().first())
    }

    @Test
    fun `un-completing a set clears only the flag, keeping its numbers and the dictionary`() = runTest {
        val bench = exercise("Bench Press", workingWeight = 70.0)
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120, weightKg = 80.0))
        started(dayId)
        val firstSetId = draft().exercises[0].sets.first().id
        useCases.completeSet(firstSetId, weightKg = 82.5, reps = 9)

        val result = useCases.uncompleteSet(firstSetId)

        assertEquals(UncompleteSetResult.Uncompleted, result)
        val sets = draft().exercises[0].sets
        assertFalse("the set is a plan again", sets[0].isCompleted)
        assertEquals("the numbers stay, so re-completing is one tap", 82.5, sets[0].weight, 0.0)
        assertEquals(9, sets[0].reps)
        assertEquals("its place in the list is unchanged", listOf(1, 2, 3, 4), sets.map { it.setNumber })
        assertEquals(
            "the fill is not guessed back: the sets behind it keep the number they were given",
            listOf(82.5, 82.5, 82.5),
            sets.drop(1).map { it.weight },
        )
        assertEquals(
            "the dictionary keeps the load it was told about — nothing remembers the old one",
            82.5,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertEquals("the plan stays the plan", 80.0, slotsOf(dayId).single().weightKg!!, 0.0)
    }

    @Test
    fun `un-completing a set that was never completed is refused`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "8-10", weightKg = 80.0))
        started(dayId)

        val result = useCases.uncompleteSet(draft().exercises[0].sets.first().id)

        assertEquals(UncompleteSetResult.NotCompleted, result)
        assertFalse(draft().exercises[0].sets.first().isCompleted)
    }

    @Test
    fun `un-completing a set of a workout that has ended changes nothing`() = runTest {
        val bench = exercise("Bench Press")
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 2, reps = "8-10", weightKg = 80.0))
        val sessionId = started(dayId)
        val setId = draft().exercises[0].sets.first().id
        useCases.completeSet(setId, weightKg = 82.5, reps = 8)
        useCases.discard(sessionId)

        assertEquals(UncompleteSetResult.NotFound, useCases.uncompleteSet(setId))
        assertNull("the workout is still gone", historyRepository.observeDraft().first())
    }

    @Test
    fun `finishing keeps only what happened`() = runTest {
        val bench = exercise("Bench Press")
        val fly = exercise("Cable Fly")
        // 23:00, so a workout that takes ninety minutes ends on the next day.
        clock = TestData.timestamp(TestData.date(month = 6, day = 15)) + 23 * 60 * 60 * 1000
        val startedAt = clock
        val dayId = plannedDay(
            Slot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            Slot(exerciseId = fly, sets = 3, reps = "12", restSeconds = 60),
        )
        val sessionId = started(dayId)
        val benchSets = draft().exercises[0].sets
        useCases.completeSet(benchSets[0].id, weightKg = 82.5, reps = 9)
        useCases.completeSet(benchSets[1].id, weightKg = 85.0, reps = 8)
        useCases.removeSet(benchSets[3].id)
        clock += 90 * 60 * 1000

        val finishedId = useCases.finish(sessionId)

        assertNotNull(finishedId)
        assertNull("the draft is gone", historyRepository.observeDraft().first())
        val session = historyRepository.observeSession(finishedId!!).first()!!
        assertFalse(session.isDraft)
        assertEquals("a workout that runs past midnight belongs to the day it began", TestData.date(month = 6, day = 15), session.date)
        assertEquals(90 * 60L, session.durationSeconds)
        assertEquals(startedAt, session.startedAtMillis)

        assertEquals(2, session.exercises.size)
        val savedBench = session.exercises[0]
        assertTrue(savedBench.wasCompleted)
        assertEquals("the prefills nobody performed are dropped, the rest renumbered", listOf(1, 2), savedBench.sets.map { it.setNumber })
        assertEquals(listOf(82.5, 85.0), savedBench.sets.map { it.weight })
        assertEquals(listOf(9, 8), savedBench.sets.map { it.reps })
        assertTrue(savedBench.sets.all { it.isCompleted })

        val savedFly = session.exercises[1]
        assertFalse("an exercise nobody touched is saved as skipped", savedFly.wasCompleted)
        assertTrue("with no sets at all", savedFly.sets.isEmpty())

        assertEquals("exactly one workout exists", 1, database.countRows("workout_session"))
        assertEquals("skipped exercises are kept, so the workout's shape is not lost", 2, database.countRows("workout_exercise_log"))
        assertEquals(2, database.countRows("workout_set_log"))
        assertEquals("and the performed weight is what I lifted last", 85.0, historyRepository.latestCompletedSet(bench)!!.weightKg, 0.0)
    }

    @Test
    fun `finishing a workout that is not running writes nothing`() = runTest {
        val finishedId = useCases.finish(9_999)

        assertNull(finishedId)
        assertEquals("a stale button must not invent a workout", 0, database.countRows("workout_session"))
    }

    @Test
    fun `discarding a workout leaves no trace of it in the history`() = runTest {
        val bench = exercise("Bench Press", workingWeight = 87.5)
        val keptId = historyRepository.saveSession(TestData.session(date = TestData.date(month = 5, day = 1)))
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 3, reps = "8-10", weightKg = 80.0))
        val sessionId = started(dayId)
        useCases.completeSet(draft().exercises[0].sets.first().id, weightKg = 82.5, reps = 8)

        useCases.discard(sessionId)

        assertNull(historyRepository.observeDraft().first())
        assertEquals(1, database.countRows("workout_session"))
        assertEquals(0, database.countRows("workout_exercise_log"))
        assertEquals(0, database.countRows("workout_set_log"))
        assertNotNull("the saved workout is untouched", historyRepository.observeSession(keptId).first())
        assertEquals(
            "a set that was performed stays the working weight, discarded workout or not",
            82.5,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
    }

    @Test
    fun `a workout survives a process restart`() = runTest {
        val bench = exercise("Bench Press", workingWeight = 87.5)
        val dayId = plannedDay(Slot(exerciseId = bench, sets = 3, reps = "8-10", restSeconds = 150, weightKg = 80.0))
        val sessionId = started(dayId)
        useCases.completeSet(draft().exercises[0].sets.first().id, weightKg = 85.0, reps = 8)
        useCases.addSet(draft().exercises[0].id)

        // A relaunch: new repositories and new use cases over the same database, with nothing cached
        // anywhere above them.
        val relaunched = relaunched()
        val draft = relaunched.history.observeDraft().first()!!

        assertEquals("the same workout, still running", sessionId, draft.id)
        assertEquals(150, draft.exercises.single().restSeconds)
        assertEquals(listOf(85.0, 85.0, 85.0), draft.exercises.single().sets.drop(1).map { it.weight })
        val completed = draft.exercises.single().sets.first()
        assertTrue("the completed set is still completed", completed.isCompleted)
        assertEquals(85.0, completed.weight, 0.0)
        assertEquals(8, completed.reps)
        assertEquals(
            "and the working weight outlived the process too",
            85.0,
            relaunched.exercises.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertEquals(
            "starting again still refuses a second workout",
            StartWorkoutResult.AlreadyRunning(sessionId),
            relaunched.active.start(dayId),
        )

        clock += 30 * 60 * 1000
        val finishedId = relaunched.active.finish(draft.id)
        assertNotNull(finishedId)
        assertNotNull("the relaunched app can finish what the first one started", relaunched.history.observeSession(finishedId!!).first())
        assertNull(relaunched.history.observeDraft().first())
        assertEquals(1, database.countRows("workout_session"))
    }

    /** A dictionary entry, optionally with a working weight the user has already chosen. */
    private suspend fun exercise(name: String, workingWeight: Double? = null): Long {
        val created = exerciseRepository.findOrCreate(name)
        workingWeight?.let { exerciseRepository.updateCurrentWorkingWeight(created.id, it) }
        return created.id
    }

    /** The day's planned slots, in the order the screen shows them. */
    private suspend fun slotsOf(dayId: Long): List<PlannedExercise> = routineRepository.observeRoutines().first()
        .flatMap { it.days }
        .first { it.id == dayId }
        .plannedExercises

    /** Starts the workout and hands back its id, failing loudly when the start is refused. */
    private suspend fun started(routineDayId: Long): Long =
        (useCases.start(routineDayId) as StartWorkoutResult.Started).sessionId

    private suspend fun draft(): WorkoutSession = historyRepository.observeDraft().first()!!

    /** The app as a relaunch sees it: everything rebuilt over the same database. */
    private fun relaunched(): Relaunch {
        val history = RoomHistoryRepository(database, TestData.zone)
        val exercises = RoomExerciseRepository(database)
        val routines = RoomRoutineRepository(database, now = { clock })
        val weights = WorkingWeightUseCases(routines, exercises, history)
        return Relaunch(
            active = ActiveWorkoutUseCases(routines, exercises, history, weights, { clock }, TestData.zone),
            history = history,
            exercises = exercises,
        )
    }

    private class Relaunch(
        val active: ActiveWorkoutUseCases,
        val history: WorkoutHistoryRepository,
        val exercises: ExerciseRepository,
    )

    /** One planned exercise: the shape of a routine slot, in the order the routine editor writes it. */
    private data class Slot(
        val exerciseId: Long,
        val sets: Int = 4,
        val reps: String = "8-10",
        val restSeconds: Int = 120,
        val weightKg: Double? = null,
    )

    /** A routine "Push Day" with one "Push A" day holding [slots]; returns the day's id. */
    private suspend fun plannedDay(vararg slots: Slot): Long {
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push A")
        slots.forEach { slot ->
            routineRepository.addPlannedExercise(
                routineDayId = dayId,
                exerciseId = slot.exerciseId,
                targetSets = slot.sets,
                targetReps = slot.reps,
                restSeconds = slot.restSeconds,
                weightKg = slot.weightKg,
            )
        }
        return dayId
    }
}
