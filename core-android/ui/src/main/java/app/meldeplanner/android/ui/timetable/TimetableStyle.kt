package app.meldeplanner.android.ui.timetable

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.model.holiday
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.sfSymbol
import androidx.compose.ui.draw.alpha
import app.meldeplanner.android.data.Holiday
import app.meldeplanner.android.model.HolidayText
import app.meldeplanner.android.ui.components.LocalAppModel

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
object TimetableDates {
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
fun FitText(
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

/** iOS regular horizontal size class. */
@Composable
fun isRegularWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** `ContentUnavailableView` on a material card, centered over the content. */
@Composable
fun EmptyStateCard(
    symbol: String,
    title: String,
    text: String?,
    action: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(DS.spacingXL), contentAlignment = Alignment.Center) {
        Surface(
            shape = DS.cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(DS.spacingXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DS.spacingS),
            ) {
                Icon(sfSymbol(symbol), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (text != null) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (action != null) {
                    Spacer(Modifier.height(DS.spacingXS))
                    action()
                }
            }
        }
    }
}

/** What the timetable colors mean (Untis colors). In the timetable's menu and the tour. */
@Composable
fun TimetableLegend(modifier: Modifier = Modifier, vertical: Boolean = false) {
    if (vertical) {
        Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
            LegendItem(UntisColors.exam, L("Prüfung"), large = true)
            LegendItem(UntisColors.change, L("Änderung"), large = true)
            LegendItem(UntisColors.cancelled, L("Entfall"), large = true)
            LegendItem(UntisColors.holiday, L("Ferien"), large = true)
        }
        return
    }
    androidx.compose.foundation.layout.FlowRow(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
        verticalArrangement = Arrangement.spacedBy(DS.spacingXS),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(UntisColors.exam, L("Prüfung"))
        LegendItem(UntisColors.change, L("Änderung"))
        LegendItem(UntisColors.cancelled, L("Entfall"))
        LegendItem(UntisColors.holiday, L("Ferien"))
    }
}

@Composable
private fun LegendItem(color: Color, text: String, large: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (large) DS.spacingS else DS.spacingXS), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (large) 14.dp else 12.dp).background(color, RoundedCornerShape(4.dp)))
        FitText(
            text,
            style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            minScale = 0.8f,
        )
    }
}

/** Banner for the timetable and calendar: "Herbstferien · 19.10.–01.11.". */
@Composable
fun HolidayBanner(holiday: Holiday, modifier: Modifier = Modifier) {
    val model = LocalAppModel.current
    Row(
        modifier
            .fillMaxWidth()
            // Blue like WebUntis, so holidays look the same everywhere.
            .background(UntisColors.holiday, RoundedCornerShape(DS.rowRadius))
            .padding(DS.spacingM)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
    ) {
        Icon(sfSymbol("lock.fill"), null, tint = Color.White)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(holiday.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                HolidayText.range(holiday, model.calendar),
                style = MaterialTheme.typography.bodyMedium.merge(tabular),
                color = Color.White,
                modifier = Modifier.alpha(0.85f),
            )
        }
    }
}

private val tabular = TextStyle(fontFeatureSettings = "tnum")
