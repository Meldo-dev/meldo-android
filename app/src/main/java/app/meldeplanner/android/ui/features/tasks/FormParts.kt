@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.tasks

import app.meldeplanner.android.ui.components.meldoMenuGroupColor
import app.meldeplanner.android.ui.theme.meldoHairline
import app.meldeplanner.android.ui.theme.FrostedWindow
import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import app.meldeplanner.android.ui.theme.MeldoDatePickerDialog
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.toPath
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.MeldoSegmented
import app.meldeplanner.android.ui.components.meldoMenuItemColors
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Form building blocks shared by the Aufgaben, Dokumente and Kalender ports: the
// Material counterparts of SwiftUI's inset grouped `Form`, `Picker`, `Toggle` and
// `DatePicker`.

/** Dates in the app language from an ICU skeleton, like `Date.formatted(.dateTime…)`. */
object DayFormat {
    fun format(instant: Instant, skeleton: String, calendar: MeldoCalendar): String {
        val format = DateFormat.getInstanceForSkeleton(skeleton, Localization.locale)
        format.timeZone = TimeZone.getTimeZone(calendar.zone.id)
        return format.format(java.util.Date.from(instant))
    }
}

/** The model's clock, refreshed every minute (SwiftUI `TimelineView(.everyMinute)`). */
@Composable
internal fun rememberMinuteNow(model: AppModel): Instant {
    var now by remember { mutableStateOf(model.currentDate) }
    LaunchedEffect(model) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = model.currentDate
        }
    }
    return now
}

/** Shape of row [index] of [count] in a grouped section: large outer corners, small inner ones. */
internal fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val outer = DS.rowRadius
    val inner = DS.innerRadius
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
        index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
        else -> RoundedCornerShape(inner)
    }
}

/** An inset grouped section with an optional header and footer. */
@Composable
internal fun FormSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = DS.spacingL, bottom = DS.spacingS),
            )
        }
        Surface(shape = DS.cardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DS.spacingL, end = DS.spacingL, top = DS.spacingS),
            )
        }
    }
}

@Composable
internal fun FormDivider() = HorizontalDivider(
    Modifier.padding(start = DS.spacingL),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/**
 * Every value on the right of a form row ("23. Okt. 2026", "Nie", "Normal") uses the
 * same style as its label, in the accent color because it opens a picker.
 */
internal val formValueStyle: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge

@Composable
private fun FormValue(text: String, modifier: Modifier = Modifier) =
    Text(text, style = formValueStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1, modifier = modifier)

/** `Toggle` in a form. */
@Composable
internal fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = rowColors,
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
}

/** A `Picker` row: the label and the current value; tapping opens the options. */
@Composable
internal fun <T> PickerRow(
    title: String,
    selection: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    icon: ((T) -> (@Composable () -> Unit)?)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = { FormValue(label(selection)) },
            colors = rowColors,
            modifier = Modifier.clickable(role = Role.DropdownList) { open = true },
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end = DS.spacingS)) {
            OptionsMenu(open, { open = false }, selection, options, label, onSelect, icon)
        }
    }
}

/**
 * The options of a form picker as an Expressive menu (iOS: the `Picker` menu): one
 * rounded group, the chosen option highlighted with a check mark.
 */
@Composable
internal fun <T> OptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selection: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    icon: ((T) -> (@Composable () -> Unit)?)? = null,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1), containerColor = meldoMenuGroupColor(), shadowElevation = 0.dp, border = meldoHairline()) {
            FrostedWindow()
options.forEachIndexed { index, option ->
                val leading = icon?.invoke(option)
                val chosen = option == selection
                SelectableDropdownMenuItem(
                    selected = chosen,
                    onClick = {
                        onDismiss()
                        onSelect(option)
                    },
                    text = { Text(label(option)) },
                    shapes = MenuDefaults.itemShape(index, options.size),
                    leadingIcon = leading,
                    selectedLeadingIcon = leading,
                    colors = meldoMenuItemColors(),
                    trailingContent = if (chosen) {
                        { Icon(sfSymbol("checkmark"), null, Modifier.size(MenuDefaults.LeadingIconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** `Picker(.segmented)`: Expressive connected toggle buttons ([MeldoSegmented]). */
@Composable
internal fun <T> SegmentedPicker(
    selection: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    MeldoSegmented(options = options, selected = selection, label = label, onSelect = onSelect, modifier = modifier)
}

/** A button row inside a form (iOS: `Button` with a `Label`). */
@Composable
internal fun ActionRow(title: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = color) },
        leadingContent = icon?.let { { Icon(it, null, tint = color, modifier = Modifier.size(22.dp)) } },
        colors = rowColors,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** `DatePicker(displayedComponents: .date)`: [value] is a local day (midnight). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRow(title: String, value: Instant, calendar: MeldoCalendar, onChange: (Instant) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { FormValue(DayFormat.format(value, "yMMMd", calendar)) },
        colors = rowColors,
        modifier = Modifier.clickable(role = Role.Button) { open = true },
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMillis(calendar.localDate(value)))
        MeldoDatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(calendar.instant(localDate(it))) }
                    open = false
                }) { Text(L("Fertig")) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(L("Abbrechen")) } },
        ) { DatePicker(state) }
    }
}

/** `DatePicker` with date and time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeRow(title: String, value: Instant, calendar: MeldoCalendar, onChange: (Instant) -> Unit) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                FormValue(
                    DayFormat.format(value, "yMMMd", calendar),
                    Modifier
                        .clip(DS.rowShape)
                        .clickable(role = Role.Button) { pickingDate = true }
                        .padding(horizontal = DS.spacingS, vertical = DS.spacingXS),
                )
                FormValue(
                    DayFormat.format(value, "jm", calendar),
                    Modifier
                        .clip(DS.rowShape)
                        .clickable(role = Role.Button) { pickingTime = true }
                        .padding(horizontal = DS.spacingS, vertical = DS.spacingXS),
                )
            }
        },
        colors = rowColors,
    )
    val minute = calendar.minuteOfDay(value)
    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMillis(calendar.localDate(value)))
        MeldoDatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(calendar.at(calendar.instant(localDate(it)), minute)) }
                    pickingDate = false
                }) { Text(L("Fertig")) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(L("Abbrechen")) } },
        ) { DatePicker(state) }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = app.meldeplanner.android.core.Localization.is24Hour(androidx.compose.ui.platform.LocalContext.current))
        MeldoAlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(calendar.at(calendar.startOfDay(value), state.hour * 60 + state.minute))
                    pickingTime = false
                }) { Text(L("Fertig")) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(L("Abbrechen")) } },
            text = { TimePicker(state) },
        )
    }
}

/** Material's date picker works in UTC midnight milliseconds. */
private fun utcMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun localDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

// region Expressive selection shapes

/**
 * A shape between two Material shapes: [progress] 0 is the [Morph]'s start, 1 its end.
 * Selected swatches, symbols, days and check circles morph with the theme's spatial motion.
 */
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** A remembered [Morph] from [start] to [end] (both normalized Material shapes). */
@Composable
internal fun rememberMorph(start: RoundedPolygon, end: RoundedPolygon): Morph = remember(start, end) { Morph(start, end) }

/** 0 -> 1 as [selected] turns on, with the theme's spatial spring; instant with Remove animations. */
@Composable
internal fun rememberSelectionProgress(selected: Boolean, label: String = "selection"): Float {
    val reduceMotion = rememberReduceMotion()
    val spec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val value by animateFloatAsState(if (selected) 1f else 0f, spec, label = label)
    return value
}

/** Material shapes used for selection across the planner. */
internal object SelectionShapes {
    val circle: RoundedPolygon get() = MaterialShapes.Circle
    val square: RoundedPolygon get() = MaterialShapes.Square
    val cookie: RoundedPolygon get() = MaterialShapes.Cookie9Sided
    val cookie4: RoundedPolygon get() = MaterialShapes.Cookie4Sided
}

// endregion
