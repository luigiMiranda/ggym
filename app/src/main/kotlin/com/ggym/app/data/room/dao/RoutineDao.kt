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
