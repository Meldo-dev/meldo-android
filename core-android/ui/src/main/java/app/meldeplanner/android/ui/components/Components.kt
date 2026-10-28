package app.meldeplanner.android.ui.components

import app.meldeplanner.android.ui.theme.meldoHairline
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.color
import uniffi.melde_ffi.FfiOccurrence
import java.time.DayOfWeek
import java.time.format.TextStyle

/** The app model for every screen, like `@Environment(AppModel.self)` on iOS. */
val LocalAppModel = staticCompositionLocalOf<AppModel> { error("AppModel not provided") }

/** Spacing and shape tokens. One scale for the whole app keeps rhythm consistent. */
object DS {
    val spacingXS = 4.dp
    val spacingS = 8.dp
    val spacingM = 12.dp
    val spacingL = 16.dp
    val spacingXL = 24.dp
    // Rounder than baseline Material, close to iOS 26's continuous corners.
    val cardRadius = 32.dp
    val rowRadius = 26.dp
    /** Inner corners between rows of one group. */
    val innerRadius = 8.dp
    val cardShape = RoundedCornerShape(cardRadius)
    val rowShape = RoundedCornerShape(rowRadius)

    /** Curated symbols for subjects (SF Symbol names; see [sfSymbol]). */
    val subjectSymbols = listOf(
        "book.closed", "function", "x.squareroot", "globe.europe.africa", "atom",
        "flask", "leaf", "pawprint", "paintpalette", "music.note", "figure.run",
        "theatermasks", "building.columns", "text.book.closed", "character.book.closed",
        "laptopcomputer", "chart.line.uptrend.xyaxis", "scalemass", "cross.case", "hands.and.sparkles",
    )
}

/** Content card on a tonal surface (iOS: opaque grouped card). */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    title: String? = null,
    symbol: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DS.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = meldoHairline(),
    ) {
        Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
            if (title != null) CardHeader(title, symbol)
            content()
        }
    }
}

@Composable
fun CardHeader(title: String, symbol: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
        if (symbol != null) {
            Icon(sfSymbol(symbol), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Circular progress for goals. */
@Composable
fun ProgressRing(
    progress: Double,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 10.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateFloatAsState(progress.coerceIn(0.0, 1.0).toFloat(), spring(), label = "ring")
    Canvas(
        modifier.semantics { contentDescription = "${(progress.coerceIn(0.0, 1.0) * 100).toInt()} %" },
    ) {
        val stroke = lineWidth.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(tint.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(tint, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Colored circle with the subject's symbol. */
@Composable
fun SubjectIcon(subject: Subject?, size: Dp = 36.dp) {
    val color = subject?.color?.color ?: StateColors.gray()
    Box(
        Modifier
            .size(size)
            .background(color.copy(alpha = 0.16f), CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(subject?.symbolName ?: "questionmark"), null, Modifier.size(size * 0.5f), tint = color)
    }
}

/** One timetable occurrence as a row. */
@Composable
fun LessonRow(occurrence: FfiOccurrence, subject: Subject?, isCurrent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
    ) {
        SubjectIcon(subject)
        Column(Modifier.weight(1f)) {
            Text(
                subject?.name ?: L("Unbekanntes Fach"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (occurrence.cancelled) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                Text(
                    TimeText.range(occurrence.startMinute.toInt(), occurrence.endMinute.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                occurrence.room?.let {
                    Text("· $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when {
            occurrence.cancelled -> Badge(L("Entfällt"), StateColors.red())
            occurrence.substituted -> Badge(L("Vertretung"), StateColors.orange())
            isCurrent -> Badge(L("Jetzt"), subject?.color?.color ?: MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = DS.spacingS, vertical = DS.spacingXS),
    )
}

object TimeText {
    fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun range(start: Int, end: Int): String = "${clock(start)}–${clock(end)}"
}

/** Weekday names in the app language, Monday first (index 0 = Monday). */
object WeekdayText {
    fun short(index: Int): String =
        DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT_STANDALONE, Localization.locale)

    fun full(index: Int): String =
        DayOfWeek.of(index + 1).getDisplayName(TextStyle.FULL_STANDALONE, Localization.locale)
}

/** A row in a list section (iOS: `List` row on an inset grouped list). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = DS.spacingL, top = DS.spacingL, bottom = DS.spacingS),
    )
}

@Composable
fun HSpacer(width: Dp) = Spacer(Modifier.width(width))
