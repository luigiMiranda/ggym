package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ggym.app.data.room.entity.PlannedExerciseEntity

@Dao
internal interface PlannedExerciseDao {

    @Insert
    suspend fun insert(plannedExercise: PlannedExerciseEntity): Long

    @Update
    suspend fun update(plannedExercise: PlannedExerciseEntity)

    @Query("UPDATE planned_exercise SET orderIndex = :orderIndex WHERE id = :plannedExerciseId AND routineDayId = :routineDayId")
    suspend fun updateOrderIndex(routineDayId: Long, plannedExerciseId: Long, orderIndex: Int)

    /**
     * Moves one slot's planned weight, and nothing else about it. The completion path uses this:
     * reading the whole routine tree to hand `@Update` a full row would be both slower and a
     * read-modify-write race against the editor.
     */
    @Query("UPDATE planned_exercise SET weightKg = :weightKg WHERE id = :plannedExerciseId")
    suspend fun updateWeight(plannedExerciseId: Long, weightKg: Double)

    /** Appends a slot at the end of its day. */
    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM planned_exercise WHERE routineDayId = :routineDayId")
    suspend fun nextOrderIndex(routineDayId: Long): Int

    @Query("DELETE FROM planned_exercise WHERE id = :plannedExerciseId")
    suspend fun deleteById(plannedExerciseId: Long)

    @Query("DELETE FROM planned_exercise")
    suspend fun deleteAll()
}
