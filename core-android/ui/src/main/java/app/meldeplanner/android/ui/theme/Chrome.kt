package app.meldeplanner.android.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource

/**
 * Translucent chrome, the Android counterpart of iOS bar materials: app bars, the
 * navigation bar and floating controls blur the content scrolling behind them.
 * Screen content registers with [meldoBlurSource]; bars draw with [meldoChrome].
 */
val LocalChromeHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Space the bottom chrome (navigation bar, floating Melden button) covers, so the
 * last row of a scrolling screen can scroll clear of it.
 */
val LocalBottomChrome = staticCompositionLocalOf { 0.dp }

/** Marks scrolling content as what the chrome blurs. */
@Composable
fun Modifier.meldoBlurSource(): Modifier {
    val state = LocalChromeHaze.current ?: return this
    return this.hazeSource(state)
}

/** Frosted-glass background for bars and floating controls. */
@Composable
fun Modifier.meldoChrome(): Modifier {
    val state = LocalChromeHaze.current ?: return this
    return this.hazeBlur(
        input = HazeInput.Backdrop(state),
        style = HazeMaterials.thin(MaterialTheme.colorScheme.surfaceContainer),
    )
}

/**
 * Hairline edge of cards and floating chrome: brighter at the top, like light
 * catching the edge of a material, so surfaces separate without a heavy shadow.
 */
@Composable
fun meldoHairline(): BorderStroke {
    val dark = LocalDarkTheme.current
    val top = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val bottom = if (dark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
    return BorderStroke(0.75.dp, Brush.verticalGradient(listOf(top, bottom)))
}

/** Extra room under the last row on screens with the floating Melden button. */
val MeldenButtonClearance: Dp = 104.dp
