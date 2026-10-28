@file:OptIn(ExperimentalMaterial3Api::class)

package app.meldeplanner.android.ui.theme

import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.meldeplanner.android.ui.components.rememberReduceMotion

/*
 * Frosted overlays: menus, sheets and dialogs live in their own windows, where the
 * in-window blur of the bars (Haze) can't reach. They use Android's cross-window
 * blur instead: while one is open, everything behind it is blurred and its panel is
 * slightly translucent, like iOS menus and sheets. When the system turns window blur
 * off (battery saver, the developer option, some devices), panels fall back to solid.
 */

/** Whether the system currently allows cross-window blur; follows changes while shown. */
@Composable
fun rememberWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    // Cross-window blur exists from Android 12; before that panels are solid.
    val manager = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) context.getSystemService(WindowManager::class.java) else null
    }
    var enabled by remember {
        mutableStateOf(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && manager?.isCrossWindowBlurEnabled == true)
    }
    DisposableEffect(manager) {
        if (manager == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return@DisposableEffect onDispose {}
        val listener = java.util.function.Consumer<Boolean> { enabled = it }
        manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        onDispose { manager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

/**
 * Blurs everything behind the window this composable is in (a popup, sheet or
 * dialog). The blur materializes with the theme's motion instead of snapping on.
 * Call it once inside the overlay's content.
 */
@Composable
fun FrostedWindow(radius: Dp = FrostedBlur) {
    val view = LocalView.current
    val blur = rememberWindowBlurEnabled()
    val reduceMotion = rememberReduceMotion()
    val target = with(LocalDensity.current) { radius.toPx() }
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(blur) { if (blur) progress.animateTo(1f, spec) }
    val value = if (blur) (progress.value * target).toInt() else 0
    DisposableEffect(view, value) {
        applyBlurBehind(view, value)
        onDispose {}
    }
}

private fun applyBlurBehind(view: View, radius: Int) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    // A dialog or sheet has a Window; keep its attributes in sync so later updates keep the blur.
    var parent: Any? = view
    while (parent is View) {
        if (parent is DialogWindowProvider) {
            val window = parent.window
            if (radius > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
            return
        }
        parent = parent.parent
    }
    // A popup (menus) is a bare window: change its layout params in place, which the
    // popup also reuses when it moves.
    val root = view.rootView
    val params = root.layoutParams as? WindowManager.LayoutParams ?: return
    params.flags = if (radius > 0) {
        params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    } else {
        params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
    }
    params.blurBehindRadius = radius
    if (root.isAttachedToWindow) root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
}

/** Blur radius behind overlays. */
val FrostedBlur: Dp = 24.dp

/**
 * Panel color for an overlay: translucent over the blurred screen, solid when blur
 * is off, so text keeps its contrast either way.
 */
@Composable
fun frosted(base: Color, alpha: Float = 0.72f): Color =
    if (rememberWindowBlurEnabled()) base.copy(alpha = alpha) else base

/** Lighter dim behind sheets and dialogs when the screen behind is blurred anyway. */
@Composable
fun frostedScrim(): Color =
    if (rememberWindowBlurEnabled()) Color.Black.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.32f)

/** A bottom sheet with a frosted panel over the blurred screen. */
@Composable
fun MeldoSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = frosted(MaterialTheme.colorScheme.surfaceContainerLow, alpha = 0.78f),
        // Explicit: contentColorFor() can't match a translucent container and would fall back to black.
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = frostedScrim(),
    ) {
        FrostedWindow()
        content()
    }
}

/** An alert dialog with a frosted panel over the blurred screen. */
@Composable
fun MeldoAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            FrostedWindow()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        containerColor = frosted(MaterialTheme.colorScheme.surfaceContainerHigh, alpha = 0.8f),
        properties = properties,
    )
}

/** A date picker dialog with a frosted panel over the blurred screen. */
@Composable
fun MeldoDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        colors = DatePickerDefaults.colors(containerColor = frosted(MaterialTheme.colorScheme.surfaceContainerHigh, alpha = 0.8f)),
    ) {
        FrostedWindow()
        content()
    }
}
