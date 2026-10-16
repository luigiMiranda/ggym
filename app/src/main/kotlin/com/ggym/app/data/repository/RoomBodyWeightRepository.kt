package com.ggym.app.data.repository

import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.entity.BodyWeightLogEntity
import com.ggym.app.data.room.mappers.BodyWeightMapper
import com.ggym.app.data.room.mappers.dayRangeMillis
import com.ggym.app.data.room.mappers.toLocalMidnightMillis
import com.ggym.app.domain.model.BodyWeightLimits
import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.repository.BodyWeightRepository
import com.ggym.app.domain.repository.BodyWeightWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Body-weight measurements, on top of Room.
 *
 * This is the one repository that validates its input, because a body weight has a documented
 * range (see [BodyWeightLimits]) and the write is where a value from a text field enters the app.
 * Rejecting is a value the caller has to look at, not an exception to catch.
 */
internal class RoomBodyWeightRepository(
    private val database: GgymDatabase,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : BodyWeightRepository {

    private val dao = database.bodyWeightDao()

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<BodyWeightLog>> {
        val range = dayRangeMillis(from, to, zoneId)
        return dao.observeRange(range.fromInclusive, range.toExclusive)
            .map { entities -> entities.map { BodyWeightMapper.toDomain(it, zoneId) } }
    }

    override suspend fun latest(): BodyWeightLog? =
        dao.latest()?.let { BodyWeightMapper.toDomain(it, zoneId) }

    override suspend fun add(date: LocalDate, weightKg: Double): BodyWeightWriteResult {
        if (!BodyWeightLimits.isValid(weightKg)) return BodyWeightWriteResult.InvalidWeight(weightKg)

        val entryId = dao.insert(
            BodyWeightLogEntity(
                dateTimestamp = date.toLocalMidnightMillis(zoneId),
                weightKg = weightKg,
            ),
        )
        return BodyWeightWriteResult.Saved(entryId)
    }

    override suspend fun update(entry: BodyWeightLog): BodyWeightWriteResult {
        if (!BodyWeightLimits.isValid(entry.weightKg)) return BodyWeightWriteResult.InvalidWeight(entry.weightKg)

        // An id that no longer exists updates nothing, which is the right outcome: the screens
        // render from the observable flow, so a deleted entry is not on screen to be edited.
        dao.update(BodyWeightMapper.toEntity(entry, zoneId))
        return BodyWeightWriteResult.Saved(entry.id)
    }

    override suspend fun delete(entryId: Long) {
        dao.deleteById(entryId)
    }
}
