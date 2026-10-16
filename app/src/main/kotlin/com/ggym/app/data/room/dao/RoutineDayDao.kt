package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ggym.app.data.room.entity.RoutineDayEntity

@Dao
internal interface RoutineDayDao {

    @Insert
    suspend fun insert(day: RoutineDayEntity): Long

    @Query("UPDATE routine_day SET dayName = :dayName WHERE id = :routineDayId")
    suspend fun rename(routineDayId: Long, dayName: String)

    /**
     * Moves one day. The `routineId` guard makes a stale drag harmless: an id that belongs to
     * another routine simply updates nothing instead of reordering the wrong list.
     */
    @Query("UPDATE routine_day SET orderIndex = :orderIndex WHERE id = :routineDayId AND routineId = :routineId")
    suspend fun updateOrderIndex(routineId: Long, routineDayId: Long, orderIndex: Int)

    /** Appends a day at the end of its routine. */
    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM routine_day WHERE routineId = :routineId")
    suspend fun nextOrderIndex(routineId: Long): Int

    @Query("DELETE FROM routine_day WHERE id = :routineDayId")
    suspend fun deleteById(routineDayId: Long)

    @Query("DELETE FROM routine_day")
    suspend fun deleteAll()
}
