package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ggym.app.data.room.entity.BodyWeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface BodyWeightDao {

    /** Half-open millisecond range, like the history range; the repository converts the dates. */
    @Query(
        """
        SELECT * FROM body_weight_log
        WHERE dateTimestamp >= :fromInclusive AND dateTimestamp < :toExclusive
        ORDER BY dateTimestamp ASC, id ASC
        """,
    )
    fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<BodyWeightLogEntity>>

    /** One-shot read for the backup exporter, ordered by id so the file is stable. */
    @Query("SELECT * FROM body_weight_log ORDER BY id ASC")
    suspend fun getAll(): List<BodyWeightLogEntity>

    @Query("SELECT * FROM body_weight_log ORDER BY dateTimestamp DESC, id DESC LIMIT 1")
    suspend fun latest(): BodyWeightLogEntity?

    @Query("SELECT * FROM body_weight_log WHERE id = :entryId")
    suspend fun findById(entryId: Long): BodyWeightLogEntity?

    @Insert
    suspend fun insert(entry: BodyWeightLogEntity): Long

    @Update
    suspend fun update(entry: BodyWeightLogEntity)

    @Query("DELETE FROM body_weight_log WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    @Query("DELETE FROM body_weight_log")
    suspend fun deleteAll()
}
