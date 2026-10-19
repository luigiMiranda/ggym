package com.ggym.app.domain.stats

import com.ggym.app.domain.model.LoggedSetPoint
import java.time.LocalDate

/** One dot of the progression chart: the heaviest completed set of an exercise on a day. */
data class ProgressionPoint(
    val date: LocalDate,
    val maxWeightKg: Double,
)

/** The least-squares line drawn across the progression chart when it has enough points. */
data class ProgressionTrend(
    val from: ProgressionPoint,
    val to: ProgressionPoint,
)

/**
 * "Is this exercise going up?"
 *
 * One point per session date, carrying the **maximum completed weight** of that day — not the last
 * set, not the slot's target. Sessions on the same date collapse into one point with the higher
 * value: the chart's x axis is a date, and two dots at one x would be a line that doubles back.
 *
 * The trend is real arithmetic over the user's own points, and only appears from **three** points:
 * two points always fit a line perfectly, so a slope drawn from them would say more than the data
 * does.
 */
data class ExerciseProgression(
    val points: List<ProgressionPoint> = emptyList(),
    val trend: ProgressionTrend? = null,
) {
    val isEmpty: Boolean get() = points.isEmpty()

    companion object {
        val EMPTY = ExerciseProgression()

        fun from(sets: List<LoggedSetPoint>): ExerciseProgression {
            val points = sets
                .groupBy { it.date }
                .map { (date, daySets) -> ProgressionPoint(date, daySets.maxOf { it.weightKg }) }
                .sortedBy { it.date }

            return ExerciseProgression(points = points, trend = trendOf(points))
        }

        /**
         * The least-squares line through the points, at the first and last dates.
         *
         * `x` is the epoch day, so the slope is kg per day. A vertical fit (every point on one
         * date) cannot happen here — the points are grouped by date — but a zero denominator is
         * guarded anyway so a degenerate input produces a flat line rather than NaN.
         */
        private fun trendOf(points: List<ProgressionPoint>): ProgressionTrend? {
            if (points.size < MIN_POINTS_FOR_TREND) return null

            val xs = points.map { it.date.toEpochDay().toDouble() }
            val ys = points.map { it.maxWeightKg }
            val meanX = xs.average()
            val meanY = ys.average()

            var covariance = 0.0
            var variance = 0.0
            for (index in points.indices) {
                val dx = xs[index] - meanX
                covariance += dx * (ys[index] - meanY)
                variance += dx * dx
            }

            val slope = if (variance == 0.0) 0.0 else covariance / variance
            val intercept = meanY - slope * meanX

            val first = points.first()
            val last = points.last()
            return ProgressionTrend(
                from = ProgressionPoint(first.date, intercept + slope * first.date.toEpochDay()),
                to = ProgressionPoint(last.date, intercept + slope * last.date.toEpochDay()),
            )
        }

        private const val MIN_POINTS_FOR_TREND = 3
    }
}
