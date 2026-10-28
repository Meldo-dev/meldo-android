package app.meldeplanner.android.ui.features.timetable

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.ui.theme.LocalDarkTheme
import app.meldeplanner.android.ui.theme.StateColors
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * WebUntis' colors (TimetableView.swift `extension Color`). Content colors, not
 * theme colors: they stay the same with Material You, only light/dark differ.
 */
object UntisColors {
    /** Lesson slate, adapting to light and dark mode. */
    val lesson: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0.30f, 0.35f, 0.40f) else Color(0.86f, 0.89f, 0.92f)

    /** Exam blocks: brown-orange in dark mode, light apricot in light mode. */
    val examFill: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0.43f, 0.18f, 0.05f) else Color(1.00f, 0.87f, 0.74f)

    /** WebUntis' change color. */
    val change = Color(0.0f, 0.50f, 0.38f)

    /** Exam frame: yellow in dark mode, deeper amber in light mode so it shows on the apricot fill. */
    val exam: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0.98f, 0.70f, 0.10f) else Color(0.80f, 0.50f, 0.00f)

    val cancelled = Color(0.90f, 0.21f, 0.37f)

    /** The blue WebUntis uses for holidays. */
    val holiday = Color(0.42f, 0.52f, 0.77f)
}

/**
 * Colors per change, shared with lists and the legend (port of `BlockStyle`).
 * Like WebUntis: every lesson the same slate, exams brown-orange with a yellow
 * frame, cancelled with a red frame; a change marks the changed value (room,
 * teacher) green. `subjectColor` is kept for callers, as on iOS.
 */
data class BlockStyle(
    val fill: Color,
    val stripe: Color,
    val border: Color?,
    /** The change color for lists and previews (green for changes, gray for regular lessons). */
    val accent: Color,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        @Composable
        fun of(kind: TimetableBlock.Kind, subjectColor: Color = Color.Gray): BlockStyle {
            val primary = MaterialTheme.colorScheme.onSurface
            return when (kind) {
                TimetableBlock.Kind.regular ->
                    BlockStyle(UntisColors.lesson, primary.copy(alpha = 0.55f), null, StateColors.gray())
                TimetableBlock.Kind.roomChange ->
                    BlockStyle(UntisColors.lesson, primary.copy(alpha = 0.55f), null, UntisColors.change)
                // Vertretung or extra lesson: the green bar marks the whole block, like the Untis app.
                TimetableBlock.Kind.substitution ->
                    BlockStyle(UntisColors.lesson, UntisColors.change, null, UntisColors.change)
                TimetableBlock.Kind.exam ->
                    BlockStyle(UntisColors.examFill, UntisColors.exam, UntisColors.exam, UntisColors.exam)
                TimetableBlock.Kind.cancelled ->
                    BlockStyle(UntisColors.lesson.copy(alpha = 0.6f), UntisColors.cancelled, UntisColors.cancelled, UntisColors.cancelled)
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

/** Date texts of the timetable in the app language (the iOS `.formatted(.dateTime…)` calls). */
internal object TimetableDates {
    private fun format(instant: Instant, calendar: MeldoCalendar, pattern: String): String =
        calendar.localDate(instant).format(DateTimeFormatter.ofPattern(pattern, Localization.locale))

    /** `.month(.abbreviated)`: "Okt." */
    fun monthShort(instant: Instant, calendar: MeldoCalendar) = format(instant, calendar, "LLL")

    /** `.weekday(.abbreviated)` / `.weekday(.wide)`. */
    fun weekday(instant: Instant, calendar: MeldoCalendar, wide: Boolean) =
        format(instant, calendar, if (wide) "EEEE" else "EEE")

    fun dayNumber(instant: Instant, calendar: MeldoCalendar) = format(instant, calendar, "d")

    /** `.weekday(.wide).day().month(.wide)`: "Montag, 19. Oktober". */
    fun weekdayDayMonth(instant: Instant, calendar: MeldoCalendar) = format(
        instant, calendar,
        when (Localization.language.code) {
            "en" -> "EEEE, MMMM d"
            else -> "EEEE, d. MMMM"
        },
    )

    /** `.day().month(.wide)`: "19. Oktober". */
    fun dayMonth(instant: Instant, calendar: MeldoCalendar) = format(
        instant, calendar,
        when (Localization.language.code) {
            "en" -> "MMMM d"
            else -> "d. MMMM"
        },
    )
}

/**
 * One line of text that shrinks to [minScale] before it truncates
 * (SwiftUI `.lineLimit(1).minimumScaleFactor(0.7)`).
 */
@Composable
internal fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.7f,
    maxLines: Int = 1,
    strikethrough: Boolean = false,
) {
    val base = LocalTextStyle.current.merge(style)
    val size = base.fontSize
    BasicText(
        text,
        modifier = modifier,
        style = base.copy(
            color = color,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (size.isSp) TextAutoSize.StepBased(minFontSize = size * minScale, maxFontSize = size) else null,
    )
}
