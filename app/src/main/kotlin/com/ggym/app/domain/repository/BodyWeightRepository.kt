package com.ggym.app.domain.repository

import com.ggym.app.domain.model.BodyWeightLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Reads and writes body-weight measurements. */
interface BodyWeightRepository {

    /** Measurements in the range, both ends included, oldest first. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<BodyWeightLog>>

    /** The most recent measurement, or `null` before the first one is logged. */
    suspend fun latest(): BodyWeightLog?

    /** Logs a measurement for [date] (local midnight — body weight is a daily value, not a timestamp). */
    suspend fun add(date: LocalDate, weightKg: Double): BodyWeightWriteResult

    /** Corrects an existing entry, for example a typo carried over from the scale. */
    suspend fun update(entry: BodyWeightLog): BodyWeightWriteResult

    suspend fun delete(entryId: Long)
}

/**
 * Result of writing a body weight.
 *
 * The accepted range is a product rule (see [com.ggym.app.domain.model.BodyWeightLimits]), so a
 * rejected value is an ordinary outcome the screen explains, not an exception.
 */
sealed interface BodyWeightWriteResult {

    data class Saved(val entryId: Long) : BodyWeightWriteResult

    /** [weightKg] fell outside the accepted human range, or was not a finite number. */
    data class InvalidWeight(val weightKg: Double) : BodyWeightWriteResult
}
