package com.ggym.app.domain.stats

import kotlin.math.roundToInt

/**
 * A line's points, reduced to what a phone screen can actually show.
 *
 * A year of daily weigh-ins is ~365 points on ~300dp; the line would be denser than the screen and
 * the shape would be lost in it. The reduction keeps the points that carry meaning — the first,
 * the last, the minimum and the maximum — and fills in between at an even stride. **The statistics
 * are computed before this runs**, so the reduction is a drawing decision and never a number the
 * user reads.
 *
 * Order is preserved, and the result is never longer than [maxPoints].
 */
fun <T> downsample(points: List<T>, maxPoints: Int, value: (T) -> Double): List<T> {
    if (maxPoints <= 0) return emptyList()
    if (points.size <= maxPoints) return points

    val lastIndex = points.lastIndex

    if (maxPoints == 1) return listOf(points.first())

    if (maxPoints < 4) {
        // Too little room for the extremes and the shape: keep the endpoints and spread the rest.
        val kept = (0 until maxPoints)
            .map { slot -> (lastIndex * slot.toDouble() / (maxPoints - 1)).roundToInt() }
            .distinct()
        return kept.map { points[it] }
    }

    val required = linkedSetOf(
        0,
        lastIndex,
        points.indices.minBy { value(points[it]) },
        points.indices.maxBy { value(points[it]) },
    )

    val slots = maxPoints - required.size
    if (slots > 0) {
        val stride = lastIndex.toDouble() / (slots + 1)
        for (slot in 1..slots) {
            required += (stride * slot).roundToInt().coerceIn(0, lastIndex)
        }
    }

    return required.sorted().map { points[it] }
}
