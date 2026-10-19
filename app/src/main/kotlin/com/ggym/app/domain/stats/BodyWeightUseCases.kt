package com.ggym.app.domain.stats

import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.repository.BodyWeightRepository
import com.ggym.app.domain.repository.BodyWeightWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The only writer of a body weight, and the rule that makes the log readable: **one measurement
 * per day**.
 *
 * The schema has no unique index on the date, and adding one would be a migration for a rule the
 * app can own. Two rows on one day would put two points at one x on the chart and make the
 * min/avg/max depend on which row a query happened to return first, so the rule lives here — the
 * single write path every screen goes through — rather than in each form.
 *
 * The range check stays in the repository (see `BodyWeightLimits`): this class decides *which* row
 * a value belongs to, not whether the value is a believable human weight.
 */
class BodyWeightUseCases(
    private val weights: BodyWeightRepository,
) {

    /** The measurements in the chart's window, oldest first. */
    fun observe(range: BodyWeightRange, today: LocalDate): Flow<List<BodyWeightLog>> =
        weights.observeRange(range.from(today), today)

    /** Every measurement up to today, newest first — the order a log is read in. */
    fun observeAll(today: LocalDate): Flow<List<BodyWeightLog>> =
        weights.observeRange(BodyWeightRange.ALL.from(today), today)
            .map { entries -> entries.asReversed() }

    /**
     * Logs [weightKg] on [date].
     *
     * A day that already has an entry is **updated**, not duplicated: the second weigh-in of the
     * day is the one the user means, and a log with one row per day is the only one a chart can
     * draw without cheating.
     */
    suspend fun log(date: LocalDate, weightKg: Double): BodyWeightWriteResult {
        val existing = weights.observeRange(date, date).first().lastOrNull()
        return if (existing == null) {
            weights.add(date, weightKg)
        } else {
            weights.update(existing.copy(weightKg = weightKg))
        }
    }

    /**
     * Corrects an entry, including its date.
     *
     * Moving a measurement onto a day that already has one merges instead of violating the
     * one-per-day rule: the target row takes the new value and the edited row disappears. The
     * alternative — refusing the edit — would leave the user with two rows and no way to fix the
     * typo that put one of them there.
     */
    suspend fun update(entry: BodyWeightLog): BodyWeightWriteResult {
        val overlap = weights.observeRange(entry.date, entry.date).first()
            .firstOrNull { it.id != entry.id }

        if (overlap == null) return weights.update(entry)

        val result = weights.update(overlap.copy(weightKg = entry.weightKg))
        if (result is BodyWeightWriteResult.Saved) weights.delete(entry.id)
        return result
    }

    suspend fun delete(entryId: Long) {
        weights.delete(entryId)
    }
}
