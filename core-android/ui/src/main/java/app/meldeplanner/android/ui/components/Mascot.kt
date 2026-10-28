package app.meldeplanner.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.ui.R

/** Meldo's moods: wave (waves when shown and tapped), cheer (goal reached), sleep (still, greyed out). */
enum class MascotMood { wave, cheer, sleep }

private val greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Meldo, the raised hand from the app logo, in orange, as a friendly presence on Start, in the
 * check-in prompt and in empty states, like on iOS. Purely decorative: hidden from
 * TalkBack, hidden entirely when the student turns it off, and the wave respects
 * Reduce Motion.
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

    val sleeping = mood == MascotMood.sleep
    Image(
        painter = painterResource(R.drawable.meldo_hand),
        contentDescription = null,
        colorFilter = if (sleeping) greyscale else null,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = angle.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .alpha(if (sleeping) 0.6f else 1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!reduceMotion) waves += 1
            }
            .clearAndSetSemantics {},
    )
}
