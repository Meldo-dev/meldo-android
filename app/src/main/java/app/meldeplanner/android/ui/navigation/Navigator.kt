package app.meldeplanner.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import app.meldeplanner.android.ui.components.rememberReduceMotion
import kotlin.math.sign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.PaddingValues
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.theme.LocalBottomChrome
import app.meldeplanner.android.ui.theme.MeldenButtonClearance
import app.meldeplanner.android.ui.theme.meldoBlurSource
import app.meldeplanner.android.ui.theme.meldoChrome
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding

/**
 * A tab's navigation stack, the counterpart of SwiftUI's `NavigationStack` +
 * `NavigationLink`: screens push the next screen with [push] and the system back
 * gesture pops it.
 */
class Navigator {
    private val stack = mutableStateListOf<@Composable () -> Unit>()

    val depth: Int get() = stack.size

    fun push(screen: @Composable () -> Unit) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun popToRoot() = stack.clear()

    /**
     * Shows the top of the stack. Screens below it stay composed (hidden), so their
     * state — typed text, scroll position, running work — survives a push, like
     * SwiftUI's NavigationStack.
     */
    @Composable
    fun Content(root: @Composable () -> Unit) {
        BackHandler(enabled = stack.isNotEmpty()) { pop() }
        val screens = listOf(root) + stack
        val motion = rememberStackMotion(screens.size)
        Box {
            screens.forEachIndexed { index, screen ->
                key(index) {
                    val top = index == screens.lastIndex
                    // Hidden screens keep their full-size layout but are not placed: not drawn,
                    // not touchable, not read by TalkBack. Keeping the size means lists don't
                    // re-run their item animations from a collapsed layout on return.
                    Box(if (top) Modifier.fillMaxSize().then(motion) else Modifier.fillMaxSize().unplaced().clearAndSetSemantics {}) { screen() }
                }
            }
        }
    }
}

/**
 * Shared-axis motion for the screen that just became the top: a pushed screen slides
 * in from the end and fades in; on pop the revealed screen eases back from the start.
 * Uses the theme's spatial spring and does nothing with Remove animations.
 */
@Composable
private fun rememberStackMotion(depth: Int): Modifier {
    val reduceMotion = rememberReduceMotion()
    val previous = remember { intArrayOf(depth) }
    val direction = remember(depth) { (depth - previous[0]).sign.also { previous[0] = depth } }
    // A new Animatable per change starts at 0 in the same frame the new top is composed.
    val progress = remember(depth) { Animatable(if (direction == 0 || reduceMotion) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(progress) { progress.animateTo(1f, spec) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return Modifier.graphicsLayer {
        val p = progress.value
        translationX = (1f - p) * direction * size.width * 0.12f * (if (rtl) -1f else 1f)
        alpha = if (direction > 0) p.coerceIn(0f, 1f) else 0.6f + 0.4f * p.coerceIn(0f, 1f)
    }
}

/** Measures normally but places nothing. */
private fun Modifier.unplaced(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {}
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/**
 * Standard screen: a large flexible top app bar (iOS large title) that collapses on
 * scroll, a back arrow on pushed screens, optional actions and a floating action
 * button. The bars are translucent and blur the content scrolling behind them
 * (iOS bar materials), so [content] gets the padding to apply as its scrolling
 * container's content padding — content then scrolls under both bars.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeldoScreen(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val back = onBack ?: if (navigator.depth > 0) navigator::pop else null
    val scrollBehavior = if (largeTitle) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    } else {
        TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    }
    val navigationIcon: @Composable () -> Unit = {
        if (back != null) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück")) }
        }
    }
    val bottomChrome = LocalBottomChrome.current
    val extraBottom = if (navigator.depth == 0 && LocalMeldenClearance.current) {
        MeldenButtonClearance
    } else {
        0.dp
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Transparent bar over frosted glass: the content shows through, blurred.
            val colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            )
            // Frosted glass once content scrolls under the bar; at rest the bar is part of the page.
            val scrolled = scrollBehavior.state.overlappedFraction > 0f || scrollBehavior.state.collapsedFraction > 0.5f
            Box(if (scrolled) Modifier.meldoChrome() else Modifier) {
                if (largeTitle) {
                    // Medium, not large: the iOS large title is about this tall, and the large bar left an empty band.
                    androidx.compose.material3.MediumFlexibleTopAppBar(
                        title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        navigationIcon = navigationIcon, actions = actions,
                        scrollBehavior = scrollBehavior, colors = colors,
                    )
                } else {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        navigationIcon = navigationIcon, actions = actions,
                        scrollBehavior = scrollBehavior, colors = colors,
                    )
                }
            }
        },
        floatingActionButton = { Box(Modifier.padding(bottom = bottomChrome)) { floatingActionButton() } },
        snackbarHost = { snackbarHostState?.let { Box(Modifier.padding(bottom = bottomChrome)) { SnackbarHost(it) } } },
        containerColor = MaterialTheme.colorScheme.surface,
        // The bottom chrome (navigation bar) lies outside this scaffold; see LocalBottomChrome.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(
            androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal,
        ),
    ) { inner ->
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val padding = PaddingValues(
            start = inner.calculateStartPadding(layoutDirection),
            top = inner.calculateTopPadding(),
            end = inner.calculateEndPadding(layoutDirection),
            bottom = inner.calculateBottomPadding() + bottomChrome + extraBottom,
        )
        Box(Modifier.fillMaxSize().meldoBlurSource()) {
            AmbientGlow()
            content(padding)
        }
    }
}

/**
 * A soft glow of the accent behind the title: gives the page a light source, and the
 * translucent bars something to blur, instead of flat gray under gray glass.
 */
@Composable
private fun AmbientGlow() {
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = if (app.meldeplanner.android.ui.theme.LocalDarkTheme.current) 0.13f else 0.10f)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val radius = size.width * 1.1f
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(glow, androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, 0f),
                    radius = radius,
                ),
            )
        },
    )
}

/** Start and Stundenplan: the floating Melden button covers the bottom of the root screen. */
val LocalMeldenClearance = androidx.compose.runtime.compositionLocalOf { false }
