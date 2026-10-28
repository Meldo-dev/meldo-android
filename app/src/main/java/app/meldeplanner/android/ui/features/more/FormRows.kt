@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.more

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.MeldoSegmented
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import java.time.Instant
import java.time.ZoneId

// Grouped-list building blocks for the Mehr, Einstellungen, Noten, Status, WebUntis
// and Abwesenheiten screens: the Material 3 Expressive counterpart of SwiftUI's inset
// grouped `Form` / `List`. Each row is its own tonal segment, 2 dp apart; the first
// and last segment of a section are rounder, like an iOS inset group.

/** Corner of the segments inside a section; the section's outer corners are [DS.rowRadius]. */
private val InnerRadius = DS.innerRadius

/** Space between two segments of a section. */
private val SegmentGap = 2.dp

/** Clip for one row, so its ripple follows the segment's inner corners. */
private val rowClip = RoundedCornerShape(InnerRadius)

/**
 * Shape of row [index] of [count] in a grouped section built from lazy items
 * (see [formItems]): rounder outside corners on the first and last row.
 */
fun formRowShape(index: Int, count: Int): Shape {
    val outer = DS.rowRadius
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(outer, outer, InnerRadius, InnerRadius)
        index == count - 1 -> RoundedCornerShape(InnerRadius, InnerRadius, outer, outer)
        else -> RoundedCornerShape(InnerRadius)
    }
}

/** Section header text, also for sections built from lazy items. */
@Composable
fun FormHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = DS.spacingL, top = DS.spacingL, bottom = DS.spacingS),
    )
}

/** Section footer text, also for sections built from lazy items. */
@Composable
fun FormFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = DS.spacingL, end = DS.spacingL, top = DS.spacingS),
    )
}

/**
 * A section: optional header, each child a tonal segment, optional footer. Every
 * direct child of [content] becomes one segment; wrap a label and its control in
 * one Column to keep them together.
 */
@Composable
fun FormSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = DS.spacingL)) {
        if (header != null) FormHeader(header) else Spacer(Modifier.size(DS.spacingM))
        SegmentedGroup(color, content)
        if (footer != null) FormFooter(footer)
    }
}

/** Vertical position and height of each segment, written in layout and read in draw. */
@Stable
private class Segments {
    var spans by mutableStateOf(emptyList<Pair<Int, Int>>())

    /** The last value written, compared without subscribing the layout to [spans]. */
    var written: List<Pair<Int, Int>> = emptyList()
}

/** Lays its children out top to bottom and draws a tonal segment behind each. */
@Composable
private fun SegmentedGroup(color: Color, content: @Composable () -> Unit) {
    val segments = remember { Segments() }
    val reduceMotion = rememberReduceMotion()
    Layout(
        content = content,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (reduceMotion) Modifier else Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()))
            .clip(RoundedCornerShape(DS.rowRadius))
            .drawBehind {
                val radius = CornerRadius(InnerRadius.toPx())
                segments.spans.forEach { (top, height) ->
                    drawRoundRect(color, Offset(0f, top.toFloat()), Size(size.width, height.toFloat()), radius)
                }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val child = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(child) }.filter { it.height > 0 }
        val gap = SegmentGap.roundToPx()
        val spans = ArrayList<Pair<Int, Int>>(placeables.size)
        var y = 0
        placeables.forEachIndexed { index, placeable ->
            if (index > 0) y += gap
            spans += y to placeable.height
            y += placeable.height
        }
        if (segments.written != spans) {
            segments.written = spans
            segments.spans = spans
        }
        layout(width, y) {
            placeables.forEachIndexed { index, placeable -> placeable.place(0, spans[index].first) }
        }
    }
}

/**
 * A grouped section of lazy rows (for long or changing lists): a header item, one
 * animated item per entry on its own segment, and a footer item. [key] must be stable.
 */
fun <T> LazyListScope.formItems(
    sectionKey: String,
    items: List<T>,
    key: (T) -> Any,
    header: String? = null,
    footer: String? = null,
    row: @Composable LazyItemScope.(item: T, shape: Shape) -> Unit,
) {
    item(key = "$sectionKey-header") {
        Box(meldoAnimateItem().padding(horizontal = DS.spacingL)) {
            if (header != null) FormHeader(header) else Spacer(Modifier.size(DS.spacingM))
        }
    }
    // A repeated key crashes LazyColumn; data from WebUntis can repeat IDs, so then the index joins in.
    val keys = items.map(key)
    val unique = keys.toSet().size == keys.size
    itemsIndexed(items, key = { index, _ -> if (unique) keys[index] else "$sectionKey-$index-${keys[index]}" }) { index, item ->
        val shape = formRowShape(index, items.size)
        Box(
            Modifier
                .then(meldoAnimateItem())
                .padding(horizontal = DS.spacingL)
                .padding(top = if (index > 0) SegmentGap else 0.dp),
        ) { row(item, shape) }
    }
    if (footer != null) {
        item(key = "$sectionKey-footer") {
            Box(meldoAnimateItem().padding(horizontal = DS.spacingL)) { FormFooter(footer) }
        }
    }
}

/** One row of [formItems] on its segment. */
@Composable
fun FormItemSurface(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) { content() }
}

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Leading icon on a rounded tonal tile in the accent, the same on every row. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 36.dp,
) {
    Box(
        Modifier.size(size).background(background, RoundedCornerShape(size * 0.34f)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

/** A status badge: a tonal circle with a symbol (check, warning, open). */
@Composable
fun StateBadge(symbol: String, color: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).background(color.copy(alpha = 0.16f), CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(symbol), null, Modifier.size(size * 0.55f), tint = color)
    }
}

/** A tappable row (iOS: `NavigationLink` or plain `Button` row). */
@Composable
fun NavRow(
    title: String,
    icon: ImageVector? = null,
    supporting: String? = null,
    trailing: String? = null,
    /** Highlights [trailing] (e.g. orange "Passwort fehlt") with a dot in front of it. */
    trailingAccent: Color? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = icon?.let { { IconTile(it) } },
        trailingContent = trailing?.let {
            {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    if (trailingAccent != null) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(8.dp).background(trailingAccent, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                    Text(
                        it, style = MaterialTheme.typography.bodyLarge,
                        color = trailingAccent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (trailingAccent != null) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                    )
                }
            }
        },
        colors = rowColors,
        modifier = Modifier.clip(rowClip).clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * A button row (iOS: `Button` in a `Form`): accent text, red when [destructive],
 * an Expressive loading indicator while [working].
 */
@Composable
fun ActionRow(
    title: String,
    symbol: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    working: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        destructive -> scheme.error
        else -> scheme.primary
    }
    ListItem(
        headlineContent = { Text(title, color = color, fontWeight = FontWeight.Medium) },
        leadingContent = symbol?.let {
            {
                IconTile(
                    sfSymbol(it),
                    tint = if (destructive) scheme.onErrorContainer else scheme.onPrimaryContainer,
                    background = if (destructive) scheme.errorContainer else scheme.primaryContainer,
                )
            }
        },
        trailingContent = if (working) {
            { LoadingIndicator(Modifier.size(28.dp)) }
        } else {
            null
        },
        colors = rowColors,
        modifier = Modifier.clip(rowClip).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** Label and value (iOS: `LabeledContent`). */
@Composable
fun ValueRow(title: String, value: String, supporting: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = rowColors,
    )
}

/** A switch row (iOS: `Toggle`). The whole row toggles. */
@Composable
fun SwitchRow(title: String, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
        colors = rowColors,
        modifier = Modifier.clip(rowClip).clickable(role = Role.Switch) { onChange(!checked) },
    )
}

/** Minus/plus buttons with the current value (iOS: `Stepper` with `LabeledContent`). */
@Composable
fun StepperRow(
    title: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                if (value.isNotEmpty()) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalIconButton(
                    onClick = onDecrease, enabled = canDecrease,
                    shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp),
                ) { Icon(Icons.Rounded.Remove, "−") }
                FilledTonalIconButton(
                    onClick = onIncrease, enabled = canIncrease,
                    shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp),
                ) { Icon(Icons.Rounded.Add, "+") }
            }
        },
        colors = rowColors,
    )
}

/** A single choice with an optional label above it, as one segment (iOS: segmented `Picker`). */
@Composable
fun <T> ChoiceRow(title: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = DS.spacingL, vertical = DS.spacingM),
        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge)
        Segmented(options, selected, onSelect)
    }
}

/** Plain secondary text inside a section. */
@Composable
fun NoteRow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingL, vertical = DS.spacingM),
    )
}

/**
 * A date in the app language from an ICU skeleton ("dMMMy" → "12. Okt. 2026"),
 * like SwiftUI's `Date.FormatStyle` with the chosen fields.
 */
fun formatDate(instant: Instant, skeleton: String, zone: ZoneId, locale: java.util.Locale = Localization.locale): String {
    val format = android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale)
    format.timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
    return format.format(java.util.Date.from(instant))
}

/** Title with cancel and confirm actions (iOS: sheet navigation bar). Confirm is the filled primary action. */
@Composable
fun SheetBar(title: String, onCancel: (() -> Unit)?, confirm: String, onConfirm: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DS.spacingS, vertical = DS.spacingXS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (onCancel != null) TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text(confirm, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Segmented picker (iOS: `Picker` with `.segmented`), as Expressive connected toggle buttons. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    MeldoSegmented(
        options = options.map { it.first },
        selected = selected,
        label = { value -> options.first { it.first == value }.second },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * MeldoScreen's padding (bars) plus the screen's own margins, for a scroller's
 * content padding, so content still scrolls under the translucent bars.
 */
@Composable
fun PaddingValues.extend(horizontal: Dp = 0.dp, top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(direction) + horizontal,
        bottom = calculateBottomPadding() + bottom,
    )
}

/** `animateItem()` for list rows, without movement when animations are off (Remove animations). */
@Composable
fun LazyItemScope.meldoAnimateItem(): Modifier =
    if (rememberReduceMotion()) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem()
    }
