package app.meldeplanner.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Meldo's moods, the artwork in meldo-ios/Design/meldo-*.svg. */
enum class MascotMood { wave, cheer, sleep }

/**
 * Meldo, the raised-hand mascot, drawn from the same shapes as the
 * iOS artwork. Purely decorative: hidden from TalkBack, hidden entirely when the
 * student turns the mascot off, and the wave respects Reduce Motion.
 */
@Composable
fun Mascot(mood: MascotMood, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val model = LocalAppModel.current
    if (!model.preferences.showMascot) return
    val reduceMotion = rememberReduceMotion()
    var waves by remember { mutableIntStateOf(0) }
    val angle = remember { Animatable(0f) }
    // The theme's fast spatial spring (Expressive: springy, a little overshoot).
    val waveSpec = androidx.compose.material3.MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    LaunchedEffect(Unit) { if (!reduceMotion) waves += 1 }
    LaunchedEffect(waves) {
        if (waves == 0 || mood == MascotMood.sleep) return@LaunchedEffect
        // Same phases as the iOS phaseAnimator: 0 → -9° → 7° → -4° → 0.
        for (target in listOf(-9f, 7f, -4f, 0f)) {
            angle.animateTo(target, waveSpec)
        }
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = angle.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!reduceMotion) waves += 1
            }
            .clearAndSetSemantics {},
    ) {
        // viewBox 0 0 190 210, scaled to fit and centered.
        val s = minOf(this.size.width / 190f, this.size.height / 210f)
        val dx = (this.size.width - 190f * s) / 2f
        val dy = (this.size.height - 210f * s) / 2f
        translate(dx, dy) {
            scale(s, s, pivot = Offset.Zero) { drawMeldo(mood) }
        }
    }
}

private val Orange = Color(0xFFFF9A4D)
private val Blush = Color(0xFFFF6F61)
private val Ink = Color(0xFF14152A)
private val Yellow = Color(0xFFFFD447)
private val Flame = Color(0xFFFF6F3D)
private val Lilac = Color(0xFF8D90FF)

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val wavePaths by lazy {
    listOf(path("M80 158 q16 14 32 0"), path("M160 18 l10 -10 M168 34 l14 -4 M150 6 l2 -6"))
}
private val cheerPaths by lazy {
    listOf(
        path("M70 138 q8 -10 16 0 M106 138 q8 -10 16 0"),
        path("M78 154 q18 22 36 0 z"),
        path("M30 22 l6 14 l14 6 l-14 6 l-6 14 l-6 -14 l-14 -6 l14 -6 z"),
        path("M166 6 l4 10 l10 4 l-10 4 l-4 10 l-4 -10 l-10 -4 l10 -4 z"),
        path("M168 60 c10 -10 4 -24 -2 -30 c0 12 -12 14 -10 26 c2 8 8 8 12 4 z"),
    )
}
private val sleepPaths by lazy {
    listOf(
        path("M70 140 q8 6 16 0 M106 140 q8 6 16 0"),
        path("M88 160 h16"),
        path("M150 20 h16 l-16 18 h16 M170 0 h10 l-10 11 h10"),
    )
}

private fun DrawScope.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(Orange, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.drawMeldo(mood: MascotMood) {
    // The hand: palm, four fingers and the thumb.
    roundRect(38f, 96f, 114f, 92f, 44f)
    roundRect(44f, 34f, 24f, 86f, 12f)
    roundRect(72f, 14f, 24f, 104f, 12f)
    roundRect(100f, 22f, 24f, 98f, 12f)
    roundRect(128f, 42f, 22f, 80f, 11f)
    rotate(-38f, pivot = Offset(30f, 128f)) { roundRect(6f, 116f, 48f, 24f, 12f) }
    // Cheeks.
    drawCircle(Blush.copy(alpha = 0.5f), 7f, Offset(64f, 156f))
    drawCircle(Blush.copy(alpha = 0.5f), 7f, Offset(128f, 156f))

    val line5 = Stroke(5f, cap = StrokeCap.Round)
    when (mood) {
        MascotMood.wave -> {
            drawCircle(Ink, 8f, Offset(78f, 138f))
            drawCircle(Ink, 8f, Offset(114f, 138f))
            drawCircle(Color.White, 2.6f, Offset(81f, 135f))
            drawCircle(Color.White, 2.6f, Offset(117f, 135f))
            drawPath(wavePaths[0], Ink, style = line5)
            drawPath(wavePaths[1], Yellow, style = line5)
        }
        MascotMood.cheer -> {
            drawPath(cheerPaths[0], Ink, style = line5)
            drawPath(cheerPaths[1], Ink)
            drawPath(cheerPaths[2], Yellow)
            drawPath(cheerPaths[3], Yellow)
            drawPath(cheerPaths[4], Flame)
        }
        MascotMood.sleep -> {
            drawPath(sleepPaths[0], Ink, style = line5)
            drawPath(sleepPaths[1], Ink, style = line5)
            drawPath(sleepPaths[2], Lilac, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
