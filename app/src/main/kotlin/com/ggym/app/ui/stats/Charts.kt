package com.ggym.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggym.app.domain.stats.MonthAttendance
import com.ggym.app.domain.stats.downsample
import com.ggym.app.ui.common.DateLabels
import com.ggym.app.ui.common.activeLocale

/**
 * The app's three charts, drawn by hand on a `Canvas`.
 *
 * They are deliberately not a charting library: a bar series and two line series do not justify a
 * second styling language, a second version to police and — in some libraries — a network-capable
 * dependency in an app that has no `INTERNET` permission. The arithmetic that decides *what* is
 * drawn lives in `domain/stats` and is tested on the JVM; this file only maps numbers to pixels.
 *
 * Every chart is decorative to accessibility: the screen puts the same numbers in a text summary
 * (and in the log), which is what a screen reader can actually use.
 */

/** A point of a line chart: [x] is an epoch day, [y] is kilograms. */
internal data class ChartPoint(
    val x: Long,
    val y: Double,
)

/** The dashed least-squares line drawn across the progression chart. */
internal data class ChartTrend(
    val from: ChartPoint,
    val to: ChartPoint,
)

/** More points than this cannot be told apart on a phone's width. */
private const val MAX_POINTS = 240

/** The twelve-month attendance chart: one bar per month, the current one in the accent colour. */
@Composable
internal fun AttendanceChart(
    counts: List<MonthAttendance>,
    modifier: Modifier = Modifier,
) {
    val locale = activeLocale()
    val accent = MaterialTheme.colorScheme.primary
    val other = accent.copy(alpha = 0.32f)
    val empty = accent.copy(alpha = 0.16f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clearAndSetSemantics {},
        ) {
            if (counts.isEmpty()) return@Canvas

            val max = counts.maxOf { it.workoutDays }.coerceAtLeast(1)
            val slot = size.width / counts.size
            val barWidth = slot * 0.52f
            val barCorner = CornerRadius(barWidth / 2f)
            val zeroHeight = 3.dp.toPx()

            counts.forEachIndexed { index, month ->
                val left = slot * index + (slot - barWidth) / 2f
                if (month.workoutDays > 0) {
                    val barHeight = (month.workoutDays.toFloat() / max) * size.height
                    drawRoundRect(
                        color = if (index == counts.lastIndex) accent else other,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = barCorner,
                    )
                } else {
                    // A month with no training is a bar of zero height, drawn so the gap is
                    // visible rather than looking like a missing month.
                    drawRoundRect(
                        color = empty,
                        topLeft = Offset(left, size.height - zeroHeight),
                        size = Size(barWidth, zeroHeight),
                        cornerRadius = CornerRadius(zeroHeight / 2f),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            counts.forEach { month ->
                Text(
                    text = DateLabels.monthShort(month.month, locale),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A line through [points], with optional dots and a dashed trend.
 *
 * The points are downsampled for the path only (first, last, min, max and an even stride in
 * between): the statistics above the chart are computed from the complete list, so the reduction
 * never moves a number the user reads.
 */
@Composable
internal fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    trend: ChartTrend? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trendColor = MaterialTheme.colorScheme.onSurfaceVariant
    val strokeWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val dotRadius = with(LocalDensity.current) { 3.5.dp.toPx() }
    val dash = remember {
        PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 3f, strokeWidth * 2.5f))
    }

    val drawn = remember(points) { downsample(points, MAX_POINTS) { it.y } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .clearAndSetSemantics {},
    ) {
        if (drawn.isEmpty()) return@Canvas

        val allY = drawn.map { it.y } + listOfNotNull(trend?.from?.y, trend?.to?.y)
        val yMin = allY.min()
        val yMax = allY.max()
        val yPad = when {
            yMax == yMin -> if (yMin == 0.0) 1.0 else kotlin.math.abs(yMin) * 0.1
            else -> (yMax - yMin) * 0.12
        }
        val low = yMin - yPad
        val high = yMax + yPad

        val xMin = drawn.minOf { it.x }
        val xMax = drawn.maxOf { it.x }

        fun xOf(x: Long): Float =
            if (xMax == xMin) size.width / 2f
            else ((x - xMin).toFloat() / (xMax - xMin).toFloat()) * size.width

        fun yOf(y: Double): Float =
            size.height - (((y - low) / (high - low)).toFloat() * size.height)

        val path = Path()
        drawn.forEachIndexed { index, point ->
            val x = xOf(point.x)
            val y = yOf(point.y)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        drawn.forEach { point ->
            drawCircle(
                color = lineColor,
                radius = dotRadius,
                center = Offset(xOf(point.x), yOf(point.y)),
            )
        }

        trend?.let { line ->
            drawLine(
                color = trendColor,
                start = Offset(xOf(line.from.x), yOf(line.from.y)),
                end = Offset(xOf(line.to.x), yOf(line.to.y)),
                strokeWidth = strokeWidth * 0.66f,
                cap = StrokeCap.Round,
                pathEffect = dash,
            )
        }
    }
}
