package com.ggym.app.domain.stats

import com.ggym.app.domain.model.BodyWeightLog
import java.time.LocalDate

/**
 * The numbers under the body-weight chart: the points in the window and their min/average/max.
 *
 * The statistics are computed over **every** point in the window, never over the (downsampled) line
 * that is drawn: a reduction is a drawing decision, and a min/max that moves with it would be a
 * number the user cannot check against their log.
 */
data class BodyWeightStats(
    val points: List<BodyWeightLog> = emptyList(),
    val minKg: Double? = null,
    val averageKg: Double? = null,
    val maxKg: Double? = null,
) {
    val count: Int get() = points.size

    companion object {
        val EMPTY = BodyWeightStats()

        fun from(entries: List<BodyWeightLog>, today: LocalDate, range: BodyWeightRange): BodyWeightStats {
            val points = entries
                .filter { range.contains(it.date, today) }
                .sortedBy { it.date }

            if (points.isEmpty()) return EMPTY

            val weights = points.map { it.weightKg }
            return BodyWeightStats(
                points = points,
                minKg = weights.min(),
                averageKg = weights.average(),
                maxKg = weights.max(),
            )
        }
    }
}
