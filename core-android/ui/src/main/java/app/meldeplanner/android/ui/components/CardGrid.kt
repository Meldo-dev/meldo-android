@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.CardSize
import app.meldeplanner.android.data.CardSlot
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Port of meldo-ios/MeldePlanner/DesignSystem/CardGrid.swift.

/** Columns a card spans: two columns on phones, four on wide screens. */
fun CardSize.span(columns: Int): Int = when (this) {
    CardSize.small -> 1
    CardSize.medium -> if (columns >= 4) 2 else columns
    CardSize.large -> columns
}

val CardSize.title: String
    get() = when (this) {
        CardSize.small -> L("Klein")
        CardSize.medium -> L("Mittel")
        CardSize.large -> L("Groß")
    }

/** Reduce Motion: the system's "Remove animations" (animator duration scale 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Haptic feedback that respects the student's haptics setting. */
@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    val model = LocalAppModel.current
    val enabled by rememberUpdatedState(model.preferences.haptics)
    return remember(haptics) { { type -> if (enabled) haptics.performHapticFeedback(type) } }
}

/**
 * Cards the student arranges like widgets: in edit mode ([CustomizeButton], or a
 * long press on a card) cards wiggle, can be dragged to a new place (the others
 * move aside live), resized by dragging the corner handle, and hidden. Hidden
 * cards wait in a tray below to be added back.
 *
 * Layout like tiles on a console home screen: a card that doesn't fit starts a
 * new row, the last card of a row stretches to close the gap, and cards in a
 * row share the tallest card's height. Two columns on phones, four from 600 dp.
 */
@Composable
fun <K> CustomizableCardGrid(
    slots: List<CardSlot<K>>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    title: (K) -> String,
    symbol: (K) -> String,
    allKinds: List<K>,
    save: (List<CardSlot<K>>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (K, CardSize) -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    // Cards slide to their new place with the theme's (Expressive) spatial spring.
    val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val trayFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val traySlide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacingPx = with(density) { DS.spacingL.roundToPx() }
    val wideThresholdPx = with(density) { 600.dp.roundToPx() }

    /** The order while a card is being dragged; saved when it's dropped. */
    var liveOrder by remember { mutableStateOf<List<CardSlot<K>>?>(null) }
    var dragging by remember { mutableStateOf<K?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    val currentSlots by rememberUpdatedState(slots)
    val currentSave by rememberUpdatedState(save)

    // Laid-out slot rectangles (grid coordinates), for hit testing while dragging.
    val bounds = remember { HashMap<K, IntRect>() }
    val placements = remember { HashMap<K, Animatable<IntOffset, *>>() }
    var hovered by remember { mutableStateOf<K?>(null) }

    val shown = liveOrder ?: slots

    fun finishDrag() {
        val order = liveOrder
        val dropped = dragging
        if (dropped != null) {
            // Fly back from the finger to the card's slot.
            val anim = placements[dropped]
            val from = IntOffset((pointer.x - grabOffset.x).roundToInt(), (pointer.y - grabOffset.y).roundToInt())
            if (anim != null && !reduceMotion) {
                @Suppress("UNCHECKED_CAST")
                val a = anim as Animatable<IntOffset, androidx.compose.animation.core.AnimationVector2D>
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { a.snapTo(from) }
            }
        }
        if (order != null && order != currentSlots) currentSave(order)
        liveOrder = null
        dragging = null
        hovered = null
    }

    LaunchedEffect(editing) { if (!editing) finishDrag() }

    fun resize(kind: K, size: CardSize) {
        val list = currentSlots.map { it.copy() }.toMutableList()
        val i = list.indexOfFirst { it.kind == kind }
        if (i < 0) return
        list[i] = list[i].copy(size = size)
        currentSave(list)
    }

    fun shift(kind: K, delta: Int) {
        val list = currentSlots.toMutableList()
        val i = list.indexOfFirst { it.kind == kind }
        if (i < 0) return
        val target = min(max(i + delta, 0), list.size - 1)
        val tmp = list[i]
        list[i] = list[target]
        list[target] = tmp
        currentSave(list)
    }

    fun hide(kind: K) {
        haptic(HapticFeedbackType.SegmentTick)
        currentSave(currentSlots.filter { it.kind != kind })
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DS.spacingL)) {
        val gridGestures = if (editing) {
            Modifier.pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        val hit = bounds.entries.firstOrNull { (_, r) -> r.contains(IntOffset(start.x.roundToInt(), start.y.roundToInt())) }
                        if (hit != null) {
                            dragging = hit.key
                            liveOrder = currentSlots
                            pointer = start
                            grabOffset = start - Offset(hit.value.left.toFloat(), hit.value.top.toFloat())
                            hovered = hit.key
                            haptic(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        val dragged = dragging ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        pointer += amount
                        val p = IntOffset(pointer.x.roundToInt(), pointer.y.roundToInt())
                        val over = bounds.entries.firstOrNull { (_, r) -> r.contains(p) }?.key
                        if (over != hovered) {
                            hovered = over
                            // Like dropEntered on iOS: entering another card moves the dragged one there.
                            val list = liveOrder?.toMutableList()
                            if (over != null && over != dragged && list != null) {
                                val from = list.indexOfFirst { it.kind == dragged }
                                val to = list.indexOfFirst { it.kind == over }
                                if (from >= 0 && to >= 0) {
                                    val slot = list.removeAt(from)
                                    list.add(to, slot)
                                    liveOrder = list
                                    haptic(HapticFeedbackType.SegmentTick)
                                }
                            }
                        }
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = { finishDrag() },
                )
            }
        } else {
            Modifier
        }

        Layout(
            modifier = gridGestures,
            content = {
                for (slot in shown) {
                    key(slot.kind) {
                        CardCell(
                            slot = slot,
                            editing = editing,
                            isDragged = dragging == slot.kind,
                            reduceMotion = reduceMotion,
                            onLongPress = {
                                haptic(HapticFeedbackType.LongPress)
                                onEditingChange(true)
                            },
                            onHide = { hide(slot.kind) },
                            onResize = { size ->
                                resize(slot.kind, size)
                                haptic(HapticFeedbackType.SegmentTick)
                            },
                            onShift = { delta -> shift(slot.kind, delta) },
                            content = content,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 360 * density.density.roundToInt()
            val columns = if (width >= wideThresholdPx) 4 else 2
            val column = (width - spacingPx * (columns - 1)) / columns.toFloat()
            fun spanWidth(span: Int) = (column * span + spacingPx * (span - 1)).roundToInt()

            // Rows: a card that doesn't fit starts a new one; the last card stretches.
            data class Item(val index: Int, var span: Int)
            val rows = mutableListOf<MutableList<Item>>()
            var row = mutableListOf<Item>()
            var used = 0
            shown.forEachIndexed { i, slot ->
                val span = slot.size.span(columns).coerceIn(1, columns)
                if (used + span > columns && row.isNotEmpty()) {
                    rows.add(row); row = mutableListOf(); used = 0
                }
                row.add(Item(i, span)); used += span
            }
            if (row.isNotEmpty()) rows.add(row)
            for (r in rows) {
                val total = r.sumOf { it.span }
                if (total < columns) r.last().span += columns - total
            }

            val placeables = arrayOfNulls<androidx.compose.ui.layout.Placeable>(measurables.size)
            val positions = arrayOfNulls<IntOffset>(measurables.size)
            var y = 0
            for (r in rows) {
                val rowHeight = r.maxOf { measurables[it.index].maxIntrinsicHeight(spanWidth(it.span)) }
                var x = 0
                for (item in r) {
                    val w = spanWidth(item.span)
                    placeables[item.index] = measurables[item.index].measure(Constraints.fixed(w, rowHeight))
                    positions[item.index] = IntOffset(x, y)
                    x += w + spacingPx
                }
                y += rowHeight + spacingPx
            }
            val height = max(y - spacingPx, 0)

            bounds.clear()
            shown.forEachIndexed { i, slot ->
                val p = positions[i] ?: return@forEachIndexed
                val pl = placeables[i] ?: return@forEachIndexed
                bounds[slot.kind] = IntRect(p, IntSize(pl.width, pl.height))
            }

            layout(width, height) {
                shown.forEachIndexed { i, slot ->
                    val pl = placeables[i] ?: return@forEachIndexed
                    val target = positions[i] ?: return@forEachIndexed
                    if (dragging == slot.kind) {
                        val finger = IntOffset((pointer.x - grabOffset.x).roundToInt(), (pointer.y - grabOffset.y).roundToInt())
                        pl.place(finger, zIndex = 1f)
                        return@forEachIndexed
                    }
                    @Suppress("UNCHECKED_CAST")
                    val anim = placements.getOrPut(slot.kind) {
                        Animatable(target, IntOffset.VectorConverter)
                    } as Animatable<IntOffset, androidx.compose.animation.core.AnimationVector2D>
                    if (anim.targetValue != target) {
                        // Undispatched, so the new target is set before the next layout pass.
                        if (reduceMotion) {
                            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { anim.snapTo(target) }
                        } else {
                            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                anim.animateTo(target, placementSpec)
                            }
                        }
                    }
                    pl.place(if (anim.isRunning || anim.value != target) anim.value else target)
                }
            }
        }

        if (shown.isEmpty() && !editing) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = DS.spacingXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DS.spacingM),
            ) {
                Icon(sfSymbol("square.grid.2x2"), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(L("Alle Karten ausgeblendet"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = { onEditingChange(true) }, shapes = ButtonDefaults.shapes()) { Text(L("Karten hinzufügen")) }
            }
        }

        AnimatedVisibility(
            visible = editing,
            enter = if (reduceMotion) fadeIn(trayFade) else fadeIn(trayFade) + slideInVertically(traySlide) { it / 2 },
            exit = if (reduceMotion) fadeOut(trayFade) else fadeOut(trayFade) + slideOutVertically(traySlide) { it / 2 },
        ) {
            HiddenCardsTray(
                hidden = allKinds.filter { kind -> slots.none { it.kind == kind } },
                title = title,
                symbol = symbol,
                onAdd = { kind ->
                    haptic(HapticFeedbackType.SegmentTick)
                    save(slots + CardSlot(kind, CardSize.medium))
                },
            )
        }
    }
}

@Composable
private fun <K> CardCell(
    slot: CardSlot<K>,
    editing: Boolean,
    isDragged: Boolean,
    reduceMotion: Boolean,
    onLongPress: () -> Unit,
    onHide: () -> Unit,
    onResize: (CardSize) -> Unit,
    onShift: (Int) -> Unit,
    content: @Composable (K, CardSize) -> Unit,
) {
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentLongPress by rememberUpdatedState(onLongPress)

    // The gentle wiggle of cards in edit mode. Always the same structure, so
    // turning it off doesn't rebuild the card (and reset its state).
    // Each swing is one theme spatial spring (Expressive: a little overshoot), so the
    // wiggle feels like the rest of the app's motion; it settles back to 0 when it stops.
    val wiggle = editing && !reduceMotion && !isDragged
    val phase = remember { Animatable(0f) }
    val swing = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(wiggle) {
        if (!wiggle) {
            phase.animateTo(0f, settle)
            return@LaunchedEffect
        }
        // Neighbouring cards start on opposite sides, like iOS jiggle mode.
        var direction = if ((slot.kind.hashCode() and 1) == 0) 1f else -1f
        while (true) {
            phase.animateTo(direction, swing)
            direction = -direction
        }
    }
    // Lifting a card scales it up and raises it with the theme's spring.
    val lift by animateFloatAsState(
        targetValue = if (isDragged) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lift",
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                rotationZ = phase.value * 0.6f
                if (isDragged || lift > 0f) {
                    val scale = 1f + 0.04f * lift
                    scaleX = scale; scaleY = scale
                    alpha = 1f - 0.08f * lift.coerceIn(0f, 1f)
                    shadowElevation = 12.dp.toPx() * lift.coerceIn(0f, 1f)
                    shape = DS.cardShape
                    clip = false
                }
            }
            .then(
                if (!editing) {
                    // Long press anywhere on the card (also over its buttons) switches to edit mode.
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val released = withTimeoutOrNull(longPressTimeout.coerceAtLeast(600L)) {
                                var ended = false
                                while (!ended) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    ended = change == null || !change.pressed ||
                                        (change.position - down.position).getDistance() > touchSlop
                                }
                                true
                            }
                            if (released == null) {
                                currentLongPress()
                                // Swallow the rest of this touch so the card's buttons don't fire.
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        propagateMinConstraints = true,
    ) {
        Box(Modifier.fillMaxSize(), propagateMinConstraints = true) { content(slot.kind, slot.size) }
        if (editing) {
            // Blocks taps on the card's own controls while editing.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
            )
        }
        // The edit controls pop in and out with the theme's motion (iOS shows them the same way).
        val reduceMotion = rememberReduceMotion()
        val chromeFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val chromeScale = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedVisibility(
            visible = editing,
            modifier = Modifier.matchParentSize(),
            enter = if (reduceMotion) fadeIn(chromeFade) else fadeIn(chromeFade) + scaleIn(chromeScale, initialScale = 0.9f),
            exit = fadeOut(chromeFade),
        ) {
            EditChrome(slot, primary, onHide, onResize, onShift, Modifier.fillMaxSize())
        }
    }
}

/** Dashed outline, remove button, size/position menu and corner handle for resizing. */
@Composable
private fun <K> EditChrome(
    slot: CardSlot<K>,
    accent: androidx.compose.ui.graphics.Color,
    onHide: () -> Unit,
    onResize: (CardSize) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier.drawWithContent {
            val stroke = 1.5.dp.toPx()
            val radius = DS.cardRadius.toPx()
            drawRoundRect(
                color = accent.copy(alpha = 0.6f),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
            drawContent()
        },
    ) {
        // Round tonal buttons that morph (round to squircle) while pressed.
        FilledTonalIconButton(
            onClick = onHide,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).size(36.dp),
        ) {
            Icon(sfSymbol("minus"), L("Ausblenden"), Modifier.size(18.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(DS.spacingS)) {
            FilledTonalIconButton(
                onClick = { menu = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp).semantics { contentDescription = L("Größe und Position") },
            ) {
                Icon(sfSymbol("ellipsis"), null, Modifier.size(18.dp))
            }
            MeldoMenu(
                expanded = menu,
                onDismiss = { menu = false },
                groups = listOf(
                    CardSize.entries.map { size ->
                        MeldoMenuItem(title = size.title, checked = size == slot.size) { onResize(size) }
                    },
                    listOf(
                        MeldoMenuItem(title = L("Nach vorne"), symbol = "arrow.up") { onShift(-1) },
                        MeldoMenuItem(title = L("Nach hinten"), symbol = "arrow.down") { onShift(1) },
                    ),
                ),
                labels = listOf(L("Größe"), null),
            )
        }
        ResizeHandle(slot.size, onResize, Modifier.align(Alignment.BottomEnd).padding(DS.spacingXS))
    }
}

/** Drag the corner to make a card bigger or smaller, one size step per ~70 dp. */
@Composable
private fun ResizeHandle(size: CardSize, onResize: (CardSize) -> Unit, modifier: Modifier) {
    val currentSize by rememberUpdatedState(size)
    val currentResize by rememberUpdatedState(onResize)
    val all = CardSize.entries
    // While dragged, the round handle morphs to a squircle and grows a little.
    var active by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val morph by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "handle",
    )
    Surface(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                val scale = 1f + 0.12f * morph
                scaleX = scale; scaleY = scale
            }
            .semantics {
                contentDescription = L("Größe ändern")
                stateDescription = size.title
            }
            .pointerInput(Unit) {
                val stepPx = 70.dp.toPx()
                var applied = 0
                var translation = Offset.Zero
                detectDragGestures(
                    onDragStart = { applied = 0; translation = Offset.Zero; active = true },
                    onDragEnd = { applied = 0; active = false },
                    onDragCancel = { applied = 0; active = false },
                ) { change, amount ->
                    change.consume()
                    translation += amount
                    val steps = ((translation.x + translation.y) / 2 / stepPx).toInt()
                    if (steps == applied) return@detectDragGestures
                    val current = all.indexOf(currentSize).coerceAtLeast(0)
                    val next = (current + steps - applied).coerceIn(0, all.size - 1)
                    applied = steps
                    if (all[next] != currentSize) currentResize(all[next])
                }
            },
        shape = RoundedCornerShape(percent = (50 - 20 * morph).roundToInt().coerceIn(30, 50)),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(sfSymbol("arrow.up.left.and.arrow.down.right"), null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun <K> HiddenCardsTray(
    hidden: List<K>,
    title: (K) -> String,
    symbol: (K) -> String,
    onAdd: (K) -> Unit,
) {
    if (hidden.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
        Text(
            L("Weitere Karten"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = DS.spacingXS),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = max(1, ((maxWidth + DS.spacingS) / (150.dp + DS.spacingS)).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                hidden.chunked(columns).forEach { rowKinds ->
                    Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                        rowKinds.forEach { kind ->
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(DS.rowShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { onAdd(kind) }
                                    .padding(DS.spacingM),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DS.spacingS),
                            ) {
                                Icon(sfSymbol(symbol(kind)), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(title(kind), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Icon(sfSymbol("plus.circle.fill"), null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        repeat(columns - rowKinds.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** The top app bar button that switches a card screen into edit mode. */
@Composable
fun CustomizeButton(editing: Boolean, onEditingChange: (Boolean) -> Unit) {
    TextButton(onClick = { onEditingChange(!editing) }, shapes = ButtonDefaults.shapes()) {
        Icon(sfSymbol(if (editing) "checkmark" else "square.grid.2x2"), null, Modifier.size(18.dp))
        Spacer(Modifier.size(DS.spacingXS))
        Text(if (editing) L("Fertig") else L("Anpassen"))
    }
}
