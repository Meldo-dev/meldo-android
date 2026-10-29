package app.meldeplanner.android.ui.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.UntisExamRecord
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.ui.components.Badge
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.theme.StateColors
import java.time.Instant
import androidx.compose.foundation.layout.PaddingValues
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.UntisAbsenceRecord
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.model.AppModel

/** One lesson in a list, colored like the timetable. Also used by Kalender. */
@Composable
fun BlockRow(
    block: TimetableBlock,
    isCurrent: Boolean = false,
    compact: Boolean = false,
    showInfo: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val style = CardBlockStyle.of(block.kind)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val details = buildList {
        add(TimeText.range(block.startMinute, block.endMinute))
        block.room?.let { add(it) }
        if (!compact && block.teacher.isNotEmpty()) add(block.teacher)
    }.joinToString(" · ")
    // The accent bar is exactly as tall as the text next to it (intrinsic height).
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 38.dp)
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(style.stripe, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                block.subjectName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (block.kind == TimetableBlock.Kind.cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(details, style = MaterialTheme.typography.bodySmall.digits, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = block.info
            if (showInfo && info != null) {
                Text(info, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val label = CardBlockStyle.label(block.kind)
        if (isCurrent) {
            Badge(L("Jetzt"), MaterialTheme.colorScheme.primary)
        } else if (label != null && !compact) {
            Badge(label, style.accent)
        }
    }
}

// endregion

// region Week preview

/** An exam from WebUntis with a countdown badge. Also used by Kalender and Dokumente. */
@Composable
fun UntisExamRow(exam: UntisExamRecord, now: Instant, compact: Boolean = false, modifier: Modifier = Modifier) {
    val model = LocalAppModel.current
    val days = DateText.daysBetween(model.calendar.startOfDay(now), exam.date, model.calendar)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    // Compact: the row is as tall as its text (intrinsic height), and the accent bar
    // fills exactly that, instead of stretching to the card's height.
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(StateColors.yellow(), CircleShape))
        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                if (exam.name.isEmpty()) exam.subject else "${exam.subject} · ${exam.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(DateText.skeleton(exam.date, "EEEdMMM", model.calendar), style = MaterialTheme.typography.bodySmall, color = secondary)
            if (!compact && exam.rooms.isNotEmpty()) {
                Text(exam.rooms.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = secondary)
            }
        }
        Badge(
            when (days) {
                0L -> L("heute")
                1L -> L("morgen")
                else -> L("in %lld Tagen", days)
            },
            StateColors.orange(),
        )
    }
}

/**
 * One absence; a tap or long press offers the Entschuldigung, like the iOS context menu.
 * [contentPadding] is the room around the content: none sideways by default, so the
 * row lines up with LessonRow inside a Card (Kalender); grouped lists pass their row inset.
 */
@Composable
fun AbsenceRow(
    absence: UntisAbsenceRecord,
    calendar: MeldoCalendar,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = DS.spacingXS),
) {
    val color = if (absence.excused) StateColors.green() else StateColors.red()
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            // Same 36 dp leading badge as SubjectIcon in LessonRow, so texts line up.
            StateBadge(if (absence.excused) "checkmark" else "exclamationmark.circle", color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(absenceDates(absence, calendar), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                Text(TimeText.range(absence.startMinute, absence.endMinute), style = MaterialTheme.typography.bodyMedium, color = secondary)
                LocalAppModel.current.lateMinutes(absence)?.let { late ->
                    Text(L("Verspätung · %lld Min.", late), style = MaterialTheme.typography.labelMedium, color = secondary)
                }
                if (absence.reason.isNotEmpty()) Text(absence.reason, style = MaterialTheme.typography.bodyMedium, color = secondary)
                if (absence.text.isNotEmpty()) Text(absence.text, style = MaterialTheme.typography.bodyMedium, color = secondary)
                Text(
                    if (absence.excused) L("entschuldigt") else L("nicht entschuldigt"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
        }
    }
}

/** "Di., 29. Sept." or a range, in the app language. */
private fun absenceDates(absence: UntisAbsenceRecord, calendar: MeldoCalendar): String {
    val skeleton = "EEEdMMM"
    val first = formatDate(absence.firstDay, skeleton, calendar.zone)
    if (calendar.isSameDay(absence.firstDay, absence.lastDay)) return first
    return "$first–${formatDate(absence.lastDay, skeleton, calendar.zone)}"
}

/**
 * Minutes late, when the absence only covers the start of the day's first lesson
 * (WebUntis records a late arrival as a short absence). Port of iOS `lateMinutes`.
 */
fun AppModel.lateMinutes(absence: UntisAbsenceRecord): Int? {
    if (!calendar.isSameDay(absence.firstDay, absence.lastDay)) return null
    val periods = data.untisTimetable?.periods ?: return null
    val first = periods.filter { !it.cancelled && calendar.isSameDay(it.date, absence.firstDay) }.minByOrNull { it.startMinute } ?: return null
    if (absence.startMinute != first.startMinute || absence.endMinute <= absence.startMinute || absence.endMinute >= first.endMinute) return null
    return absence.endMinute - absence.startMinute
}
