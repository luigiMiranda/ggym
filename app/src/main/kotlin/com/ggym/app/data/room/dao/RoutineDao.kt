package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.data.room.entity.RoutineDayEntity
import com.ggym.app.data.room.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

/** A routine with its days, each carrying its planned exercises: one emission, the whole tree. */
internal data class RoutineWithDays(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineDayEntity::class, parentColumn = "id", entityColumn = "routineId")
    val days: List<RoutineDayWithPlannedExercises>,
)

/** A day with its planned exercises. */
internal data class RoutineDayWithPlannedExercises(
    @Embedded val day: RoutineDayEntity,
    @Relation(entity = PlannedExerciseEntity::class, parentColumn = "id", entityColumn = "routineDayId")
    val plannedExercises: List<PlannedExerciseEntity>,
)

/**
 * One placement of an exercise in a routine day, flattened for the exercise library: which
 * exercise, which routine and day, and the planned numbers. A `@Relation` tree would load every
 * routine's every slot to answer "where is this exercise used?".
 */
internal data class ExercisePlanRow(
    val exerciseId: Long,
    val routineId: Long,
    val routineName: String,
    val routineDayId: Long,
    val dayName: String,
    val targetSets: Int,
    val targetReps: String,
)

@Dao
internal interface RoutineDao {

    /**
     * Every routine with its whole tree, oldest first — creation order is the order the user
     * thinks in, and it does not move when a routine is renamed.
     *
     * `@Relation` does not define the order of the children, so the repository sorts days and
     * planned exercises by `orderIndex` when it maps the result.
     */
    @Transaction
    @Query("SELECT * FROM routine ORDER BY createdAt ASC, id ASC")
    fun observeRoutinesWithDays(): Flow<List<RoutineWithDays>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :routineId")
    fun observeRoutineWithDays(routineId: Long): Flow<RoutineWithDays?>

    /**
     * Every exercise placement in every routine, ordered by routine creation and then day order,
     * which is the order the user thinks in. One emission for the whole library.
     */
    @Query(
        """
        SELECT planned_exercise.exerciseId AS exerciseId,
               routine.id AS routineId,
               routine.name AS routineName,
               routine_day.id AS routineDayId,
               routine_day.dayName AS dayName,
               planned_exercise.targetSets AS targetSets,
               planned_exercise.targetReps AS targetReps
        FROM planned_exercise
        JOIN routine_day ON routine_day.id = planned_exercise.routineDayId
        JOIN routine ON routine.id = routine_day.routineId
        ORDER BY routine.createdAt ASC, routine.id ASC, routine_day.orderIndex ASC, routine_day.id ASC
        """,
    )
    fun observeExercisePlans(): Flow<List<ExercisePlanRow>>

    /** One-shot read for the backup exporter. */
    @Transaction
    @Query("SELECT * FROM routine ORDER BY id ASC")
    suspend fun getAllRoutinesWithDays(): List<RoutineWithDays>

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Query("UPDATE routine SET name = :name WHERE id = :routineId")
    suspend fun rename(routineId: Long, name: String)

    @Query("DELETE FROM routine WHERE id = :routineId")
    suspend fun deleteById(routineId: Long)

    @Query("DELETE FROM routine")
    suspend fun deleteAll()
}
