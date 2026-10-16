package com.ggym.app.testing

import com.ggym.app.data.repository.RoomBodyWeightRepository
import com.ggym.app.data.repository.RoomExerciseRepository
import com.ggym.app.data.repository.RoomHistoryRepository
import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.domain.repository.BodyWeightRepository
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository

/**
 * The data layer with its real dependencies: a database, the four repositories the app builds from
 * it, and a clock the test owns.
 *
 * The repositories are created on first use rather than in a `@Before` method, so they are always
 * built against the database instance the test is actually running on. Dates are read and written
 * in [TestData.zone], which keeps every assertion independent of the machine.
 */
abstract class RepositoryTest : RoomTest() {

    /** Mutable so a test can make one write happen after another; nothing reads the wall clock. */
    internal var clock: Long = 1_750_000_000_000

    internal val exerciseRepository: ExerciseRepository by lazy { RoomExerciseRepository(database) }

    internal val routineRepository: RoutineRepository by lazy { RoomRoutineRepository(database, now = { clock }) }

    internal val historyRepository: WorkoutHistoryRepository by lazy { RoomHistoryRepository(database, TestData.zone) }

    internal val bodyWeightRepository: BodyWeightRepository by lazy { RoomBodyWeightRepository(database, TestData.zone) }
}
