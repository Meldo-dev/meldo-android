@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.dashboard

import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.TimeZone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.TaskPriority
import app.meldeplanner.android.data.TaskRecurrence
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.model.toggleDone
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.rememberHaptic
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.theme.LocalDarkTheme
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.color
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/** Dates formatted from an ICU skeleton, like Swift's `Date.FormatStyle` (`.weekday(.wide).day().month()`). */
internal object DateText {
    fun skeleton(instant: Instant, skeleton: String, calendar: MeldoCalendar): String {
        val format = DateFormat.getInstanceForSkeleton(skeleton, Localization.locale)
        format.timeZone = TimeZone.getTimeZone(calendar.zone.id)
        return format.format(Date.from(instant))
    }

    /** "12 Min., 3 Sek." until [target], like `Text(date, style: .relative)`. */
    fun relative(from: Instant, target: Instant): String {
        val seconds = kotlin.math.abs(ChronoUnit.SECONDS.between(from, target))
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val format = MeasureFormat.getInstance(Localization.locale, MeasureFormat.FormatWidth.SHORT)
        val parts = when {
            h > 0 -> listOf(Measure(h, MeasureUnit.HOUR), Measure(m, MeasureUnit.MINUTE))
            m > 0 -> listOf(Measure(m, MeasureUnit.MINUTE), Measure(s, MeasureUnit.SECOND))
            else -> listOf(Measure(s, MeasureUnit.SECOND))
        }
        return format.formatMeasures(*parts.toTypedArray())
    }

    /** Whole days from the day of [from] to the day of [to]. */
    fun daysBetween(from: Instant, to: Instant, calendar: MeldoCalendar): Long =
        ChronoUnit.DAYS.between(calendar.localDate(from), calendar.localDate(to))
}

/** The current time, refreshed every minute (iOS: `TimelineView(.everyMinute)`) or every [periodMillis]. */
@Composable
internal fun rememberTickingNow(model: AppModel, periodMillis: Long = 60_000): Instant {
    var now by remember { mutableStateOf(model.currentDate) }
    LaunchedEffect(periodMillis) {
        while (true) {
            val ms = System.currentTimeMillis()
            delay(periodMillis - ms % periodMillis + 5)
            now = model.currentDate
        }
    }
    return now
}

/** The colors WebUntis uses (port of BlockStyle and the `Color.untis*` palette in TimetableView.swift). */
internal class CardBlockStyle(val fill: Color, val stripe: Color, val border: Color?, val accent: Color) {
    companion object {
        val untisHoliday = Color(red = 0.42f, green = 0.52f, blue = 0.77f)
        private val untisChange = Color(red = 0.0f, green = 0.50f, blue = 0.38f)
        private val untisCancelled = Color(red = 0.90f, green = 0.21f, blue = 0.37f)

        @Composable
        fun of(kind: TimetableBlock.Kind): CardBlockStyle {
            val dark = LocalDarkTheme.current
            val lesson = if (dark) Color(0.30f, 0.35f, 0.40f) else Color(0.86f, 0.89f, 0.92f)
            val examFill = if (dark) Color(0.43f, 0.18f, 0.05f) else Color(1.00f, 0.87f, 0.74f)
            val exam = if (dark) Color(0.98f, 0.70f, 0.10f) else Color(0.80f, 0.50f, 0.00f)
            val stripe = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            return when (kind) {
                TimetableBlock.Kind.regular -> CardBlockStyle(lesson, stripe, null, StateColors.gray())
                TimetableBlock.Kind.roomChange, TimetableBlock.Kind.substitution ->
                    CardBlockStyle(lesson, stripe, null, untisChange)
                TimetableBlock.Kind.exam -> CardBlockStyle(examFill, exam, exam, exam)
                TimetableBlock.Kind.cancelled -> CardBlockStyle(lesson.copy(alpha = 0.6f), untisCancelled, untisCancelled, untisCancelled)
            }
        }

        fun label(kind: TimetableBlock.Kind): String? = when (kind) {
            TimetableBlock.Kind.regular -> null
            TimetableBlock.Kind.roomChange -> L("Raumänderung")
            TimetableBlock.Kind.exam -> L("Prüfung")
            TimetableBlock.Kind.cancelled -> L("Entfall")
            TimetableBlock.Kind.substitution -> L("Änderung")
        }
    }
}

/** Due date as a short word ("heute", "morgen", weekday or date). Port of DueText. */
internal object CardDueText {
    fun label(due: Instant, today: Instant, calendar: MeldoCalendar): String =
        when (val days = DateText.daysBetween(today, due, calendar)) {
            0L -> L("heute")
            1L -> L("morgen")
            -1L -> L("gestern")
            in 2L..6L -> DateText.skeleton(due, "EEEE", calendar)
            else -> DateText.skeleton(due, "dMMM", calendar)
        }
}

/**
 * One task with a check circle, as in the task list. Checking off gives haptic
 * feedback. On the start screen the row only checks off (iOS passes an empty `onOpen`).
 */
@Composable
internal fun CardTaskRow(task: SchoolTask, today: Instant) {
    val model = LocalAppModel.current
    val haptic = rememberHaptic()
    val subject = model.subject(task.subjectID)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
    ) {
        IconButton(
            onClick = {
                haptic(HapticFeedbackType.Confirm)
                model.toggleDone(task.id)
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                sfSymbol(if (task.isDone) "checkmark.circle.fill" else "circle"),
                if (task.isDone) L("Als offen markieren") else L("Als erledigt markieren"),
                Modifier.size(26.dp),
                tint = if (task.isDone) StateColors.green() else subject?.color?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.CenterVertically) {
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                if (subject != null) {
                    Text(subject.name, style = MaterialTheme.typography.bodyMedium, color = subject.color.color)
                }
                task.dueDate?.let { due ->
                    val overdue = !task.isDone && due < model.calendar.startOfDay(today)
                    Text(
                        CardDueText.label(due, today, model.calendar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) StateColors.red() else secondary,
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
        if (task.priority == TaskPriority.high && !task.isDone) {
            Icon(sfSymbol("exclamationmark.circle.fill"), L("Wichtig"), tint = StateColors.orange())
        }
    }
}

/** A text link styled like a SwiftUI `NavigationLink` inside a card. */
@Composable
internal fun CardLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = text },
    )
}
