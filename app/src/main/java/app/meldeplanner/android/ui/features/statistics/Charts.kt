package app.meldeplanner.android.ui.features.statistics

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Compose Canvas counterparts of the Swift Charts used on iOS (BarMark, LineMark +
// PointMark). Axes follow Swift Charts' defaults: value labels on the trailing side
// with grid lines, category labels below. Material 3 Expressive look: pill-shaped
// bars on a tonal track with a soft accent gradient, quiet dashed grid lines, and
// bars/lines that grow in with the theme's spring (instant with Remove animations).

/** Rounded tick values from 0 to at least [maxValue], like Swift Charts' automatic axis. */
internal fun niceTicks(maxValue: Int, desired: Int = 3): List<Int> {
    val top = max(maxValue, 1)
    val raw = top.toDouble() / desired
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= raw }.coerceAtLeast(1.0).toInt()
    val last = (ceil(top.toDouble() / step) * step).toInt()
    return (0..last step step).toList()
}

private class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

private fun DrawScope.yAxis(ticks: List<Int>, measurer: TextMeasurer, style: TextStyle, grid: Color, show: Boolean, xLabelHeight: Float): Plot {
    val labelWidth = if (show) ticks.maxOf { measurer.measure(it.toString(), style).size.width } + 8.dp.toPx() else 0f
    val plot = Plot(0f, 8.dp.toPx(), size.width - labelWidth, size.height - xLabelHeight)
    if (show) {
        val top = ticks.last().coerceAtLeast(1)
        val dashes = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        for (t in ticks) {
            val y = plot.bottom - plot.height * t / top
            // Solid baseline, quiet dashed lines above it.
            drawLine(
                grid, Offset(plot.left, y), Offset(plot.right, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (t == 0) null else dashes,
            )
            val layout = measurer.measure(t.toString(), style)
            drawText(layout, topLeft = Offset(plot.right + 8.dp.toPx(), y - layout.size.height / 2f))
        }
    }
    return plot
}

/**
 * One animated value per index that springs from its previous value (0 at first)
 * to the new one, slightly staggered so bars grow in one after another.
 */
@Composable
private fun rememberAnimatedValues(values: List<Int>): List<Animatable<Float, *>> {
    val reduceMotion = rememberReduceMotion()
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val animated = remember(values.size) { List(values.size) { Animatable(0f) } }
    LaunchedEffect(values, animated) {
        animated.forEachIndexed { i, a ->
            val target = values.getOrElse(i) { 0 }.toFloat()
            if (reduceMotion) {
                a.snapTo(target)
            } else {
                launch {
                    kotlinx.coroutines.delay(i * 30L)
                    a.animateTo(target, spec)
                }
            }
        }
    }
    return animated
}

/** Bars per category with labels below (iOS: `Chart { BarMark }`). [highlight] emphasizes one label (today). */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    barColor: (Int) -> Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showYAxis: Boolean = true,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp = 4.dp,
    highlight: Int? = null,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val highlightStyle = style.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val animated = rememberAnimatedValues(values)
    val description = contentDescription + ": " + labels.indices.joinToString(", ") { "${labels[it]} ${values.getOrElse(it) { 0 }}" }
    Canvas(modifier.semantics { this.contentDescription = description }) {
        if (labels.isEmpty()) return@Canvas
        val labelHeight = measurer.measure("Mo", style).size.height + 6.dp.toPx()
        val ticks = niceTicks(values.maxOrNull() ?: 0)
        val plot = yAxis(ticks, measurer, style, grid, showYAxis, labelHeight)
        val top = ticks.last().coerceAtLeast(1).toFloat()
        val slot = plot.width / labels.size
        val barWidth = min(slot * 0.58f, 40.dp.toPx())
        labels.forEachIndexed { i, label ->
            val value = animated.getOrNull(i)?.value ?: 0f
            val cx = plot.left + slot * (i + 0.5f)
            val left = cx - barWidth / 2
            val round = barWidth / 2
            // Tonal track behind every bar, full height and fully round.
            drawRoundRect(
                track,
                topLeft = Offset(left, plot.top),
                size = androidx.compose.ui.geometry.Size(barWidth, plot.height),
                cornerRadius = CornerRadius(round, round),
            )
            val h = (plot.height * value / top).coerceIn(0f, plot.height + 8.dp.toPx())
            if (h > 0.5f) {
                // Fully round top, gently rounded foot on the baseline.
                val r = min(round, h)
                val foot = min(4.dp.toPx(), h - r).coerceAtLeast(0f)
                val bar = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = plot.bottom - h, right = left + barWidth, bottom = plot.bottom,
                            topLeftCornerRadius = CornerRadius(r, r),
                            topRightCornerRadius = CornerRadius(r, r),
                            bottomRightCornerRadius = CornerRadius(foot, foot),
                            bottomLeftCornerRadius = CornerRadius(foot, foot),
                        ),
                    )
                }
                val color = barColor(i)
                drawPath(
                    bar,
                    Brush.verticalGradient(
                        listOf(color, color.copy(alpha = color.alpha * 0.62f)),
                        startY = plot.bottom - h,
                        endY = plot.bottom,
                    ),
                )
            }
            val layout = measurer.measure(label, if (i == highlight) highlightStyle else style)
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, plot.bottom + 6.dp.toPx()))
        }
    }
}

/** A smooth line with points (iOS: `LineMark` with `.monotone` interpolation and `PointMark`). */
@Composable
fun LineChart(
    values: List<Int>,
    labels: List<String>,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val pointFill = MaterialTheme.colorScheme.surfaceContainer
    val reduceMotion = rememberReduceMotion()
    // The line draws in from the left with the theme's effects spring.
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val revealSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(values) {
        if (reduceMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, revealSpec)
        }
    }
    val description = contentDescription + ": " + labels.indices.joinToString(", ") { "${labels[it]} ${values.getOrElse(it) { 0 }}" }
    Canvas(modifier.semantics { this.contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = measurer.measure("1.", style).size.height + 6.dp.toPx()
        val ticks = niceTicks(values.max())
        val plot = yAxis(ticks, measurer, style, grid, true, labelHeight)
        val top = ticks.last().coerceAtLeast(1).toFloat()
        val slot = plot.width / values.size
        val points = values.mapIndexed { i, v -> Offset(plot.left + slot * (i + 0.5f), plot.bottom - plot.height * v / top) }
        val line = monotonePath(points)
        val revealX = plot.left + (plot.right - plot.left) * reveal.value
        clipRect(left = 0f, top = 0f, right = revealX, bottom = size.height) {
            // Soft tonal area under the line.
            if (points.size > 1) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, plot.bottom)
                    lineTo(points.first().x, plot.bottom)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0f)), startY = plot.top, endY = plot.bottom),
                )
            }
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach {
                drawCircle(color, 5.dp.toPx(), it)
                drawCircle(pointFill, 2.5.dp.toPx(), it)
            }
        }
        // Thin out labels that would collide, like Swift Charts does.
        val widest = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        val every = max(1, ceil((widest + 8.dp.toPx()) / slot).toInt())
        labels.forEachIndexed { i, label ->
            if ((labels.size - 1 - i) % every != 0) return@forEachIndexed
            val layout = measurer.measure(label, style)
            val x = (points[i].x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, plot.bottom + 6.dp.toPx()))
        }
    }
}

/** Monotone cubic interpolation (Fritsch–Carlson): no overshoot between points. */
private fun monotonePath(p: List<Offset>): Path {
    val path = Path()
    if (p.isEmpty()) return path
    path.moveTo(p[0].x, p[0].y)
    if (p.size == 1) return path
    val n = p.size
    val dx = FloatArray(n - 1) { p[it + 1].x - p[it].x }
    val slope = FloatArray(n - 1) { (p[it + 1].y - p[it].y) / dx[it] }
    val tangent = FloatArray(n)
    tangent[0] = slope[0]
    tangent[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) {
        tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else {
            val w1 = 2 * dx[i] + dx[i - 1]
            val w2 = dx[i] + 2 * dx[i - 1]
            (w1 + w2) / (w1 / slope[i - 1] + w2 / slope[i])
        }
    }
    for (i in 0 until n - 1) {
        val h = dx[i] / 3f
        path.cubicTo(
            p[i].x + h, p[i].y + tangent[i] * h,
            p[i + 1].x - h, p[i + 1].y - tangent[i + 1] * h,
            p[i + 1].x, p[i + 1].y,
        )
    }
    return path
}
