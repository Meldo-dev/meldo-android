package app.meldeplanner.android.ui.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.TaskPriority
import app.meldeplanner.android.data.TaskRecurrence
import app.meldeplanner.android.model.toggleDone
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.form.DayFormat
import app.meldeplanner.android.ui.form.MorphShape
import app.meldeplanner.android.ui.form.SelectionShapes
import app.meldeplanner.android.ui.form.rememberMorph
import app.meldeplanner.android.ui.form.rememberSelectionProgress
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.color
import java.time.Instant
import java.time.temporal.ChronoUnit
import uniffi.melde_ffi.FfiTaskGroup
import androidx.compose.material3.TextFieldDefaults
import app.meldeplanner.android.ui.form.PickerRow

/**
 * Rows slide between sections and fade in and out with the theme's motion (Expressive
 * spatial spring). No movement with Remove animations.
 */
@Composable
fun LazyItemScope.rowAnimation(): Modifier {
    if (rememberReduceMotion()) return Modifier
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec<IntOffset>(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

/** `.swipeActions { Button(role: .destructive) }`: swipe from the trailing edge deletes. */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, shape).padding(horizontal = DS.spacingL),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(sfSymbol("trash"), L("Löschen"), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/** `ContentUnavailableView`: icon, title, text and an optional button. */
@Composable
fun EmptyState(symbol: String, title: String, text: String? = null, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = DS.spacingXL * 2, horizontal = DS.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
    ) {
        Icon(sfSymbol(symbol), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.size(DS.spacingS))
            Button(onClick = action.second, shapes = ButtonDefaults.shapes()) { Text(action.first) }
        }
    }
}

/** One task with a check circle. Checking off gives haptic feedback. Used by Start, Stundenplan and Kalender too. */
@Composable
fun TaskRow(task: SchoolTask, today: Instant, onOpen: () -> Unit) {
    val model = LocalAppModel.current
    val haptics = LocalHapticFeedback.current
    val subject = model.subject(task.subjectID)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingM)) {
        IconButton(
            onClick = {
                if (model.preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                model.toggleDone(task.id)
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(44.dp).semantics {
                contentDescription = if (task.isDone) L("Als offen markieren") else L("Als erledigt markieren")
            },
        ) {
            CheckCircle(task.isDone, subject?.color?.color ?: secondary)
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isDone) secondary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            )
            val due = task.dueDate
            if (subject != null || due != null || task.recurrence != TaskRecurrence.never || (task.reminderAt != null && !task.isDone)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.CenterVertically) {
                    val style = MaterialTheme.typography.bodyMedium
                    if (subject != null) Text(subject.name, style = style, color = subject.color.color, maxLines = 1)
                    if (due != null) {
                        val overdue = !task.isDone && due < model.calendar.startOfDay(today)
                        Text(
                            DueText.label(due, today, model.calendar),
                            style = style,
                            color = if (overdue) StateColors.red() else secondary,
                            maxLines = 1,
                        )
                    }
                    if (task.recurrence != TaskRecurrence.never) {
                        Icon(sfSymbol("repeat"), L("Wiederholt sich"), Modifier.size(16.dp), tint = secondary)
                    }
                    if (task.reminderAt != null && !task.isDone) {
                        Icon(sfSymbol("bell"), L("Erinnerung"), Modifier.size(16.dp), tint = secondary)
                    }
                }
            }
        }
        if (task.priority == TaskPriority.high && !task.isDone) {
            Icon(sfSymbol("exclamationmark.circle.fill"), L("Wichtig"), tint = StateColors.orange())
        }
    }
}

/**
 * The check circle: the ring fills green and morphs into a soft cookie shape with
 * the check mark, on the theme's spatial spring (instant with Remove animations).
 */
@Composable
private fun CheckCircle(done: Boolean, ring: Color) {
    val progress = rememberSelectionProgress(done, "check")
    val morph = rememberMorph(SelectionShapes.circle, SelectionShapes.cookie)
    val fill = StateColors.green()
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(23.dp)
                .graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) }
                .border(2.dp, ring, CircleShape),
        )
        if (progress > 0.01f) {
            Box(
                Modifier
                    .size(27.dp)
                    .graphicsLayer {
                        val scale = (0.4f + 0.6f * progress).coerceAtLeast(0f)
                        scaleX = scale
                        scaleY = scale
                        alpha = progress.coerceIn(0f, 1f)
                    }
                    .background(fill, MorphShape(morph, progress)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sfSymbol("checkmark"), null, Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

/** Section titles of the task groups. */
object TaskText {
    fun title(group: FfiTaskGroup): String = when (group) {
        FfiTaskGroup.OVERDUE -> L("Überfällig")
        FfiTaskGroup.TODAY -> L("Heute")
        FfiTaskGroup.TOMORROW -> L("Morgen")
        FfiTaskGroup.THIS_WEEK -> L("Diese Woche")
        FfiTaskGroup.LATER -> L("Später")
        FfiTaskGroup.NO_DATE -> L("Ohne Datum")
        FfiTaskGroup.COMPLETED -> L("Erledigt")
    }
}

/** "heute", "morgen", "gestern", a weekday this week, or "12. Okt.". */
object DueText {
    fun label(due: Instant, today: Instant, calendar: MeldoCalendar): String {
        val days = ChronoUnit.DAYS.between(calendar.localDate(today), calendar.localDate(due))
        return when (days) {
            0L -> L("heute")
            1L -> L("morgen")
            -1L -> L("gestern")
            in 2L..6L -> DayFormat.format(due, "EEEE", calendar)
            else -> DayFormat.format(due, "dMMM", calendar)
        }
    }
}

/** `Picker(L("Fach"))` with "Kein Fach" and every subject with its symbol. */
@Composable
fun SubjectPickerRow(selection: String?, onSelect: (String?) -> Unit) {
    val model = LocalAppModel.current
    val subjects = model.subjects
    PickerRow(
        L("Fach"),
        selection,
        listOf<String?>(null) + subjects.map { it.id },
        label = { id -> if (id == null) L("Kein Fach") else model.subject(id)?.name ?: L("Kein Fach") },
        onSelect = onSelect,
        icon = { id ->
            model.subject(id)?.let { subject ->
                { Icon(sfSymbol(subject.symbolName), null, Modifier.size(20.dp), tint = subject.color.color) }
            }
        },
    )
}

@Composable
fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
