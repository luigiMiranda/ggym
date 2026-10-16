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

    /** Appends a slot at the end of its day. */
    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM planned_exercise WHERE routineDayId = :routineDayId")
    suspend fun nextOrderIndex(routineDayId: Long): Int

    @Query("DELETE FROM planned_exercise WHERE id = :plannedExerciseId")
    suspend fun deleteById(plannedExerciseId: Long)

    @Query("DELETE FROM planned_exercise")
    suspend fun deleteAll()
}
