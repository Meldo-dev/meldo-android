package app.meldeplanner.android.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.CardSize
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.model.gradeSummary
import app.meldeplanner.android.model.gradeText
import app.meldeplanner.android.model.overallGradeAverage
import app.meldeplanner.android.model.tasks
import app.meldeplanner.android.model.timetableBlocks
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.WeekdayText
import app.meldeplanner.android.ui.charts.BarChart
import app.meldeplanner.android.ui.navigation.LocalNavigator
import java.time.Instant
import app.meldeplanner.android.ui.rows.rememberTickingNow
import app.meldeplanner.android.ui.rows.DateText
import app.meldeplanner.android.ui.rows.CardTaskRow
import app.meldeplanner.android.ui.rows.CardLink
import app.meldeplanner.android.ui.rows.digits
import app.meldeplanner.android.ui.rows.NumberText
import app.meldeplanner.android.ui.rows.BlockRow
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.rememberLink

// Port of meldo-ios/MeldePlanner/Features/Dashboard/MoreCards.swift.

/** Big number (`.font(.system(size:, weight: .bold, design: .rounded))`): Expressive display styles. */
@Composable
private fun bigNumber(size: CardSize): TextStyle =
    if (size == CardSize.small) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium

/** The lesson running now or coming next, with a live countdown. */
@Composable
internal fun NextLessonCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val minute = calendar.minuteOfDay(now)
    val today = calendar.startOfDay(now)
    val block = model.timetableBlocks(now).firstOrNull { it.endMinute > minute && it.kind != app.meldeplanner.android.model.TimetableBlock.Kind.cancelled }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Card(title = L("Nächste Stunde"), symbol = "clock") {
        if (block != null) {
            // The countdown ticks every second like `Text(date, style: .relative)`.
            val second = rememberTickingNow(model, periodMillis = 1_000)
            val running = block.startMinute <= calendar.minuteOfDay(second)
            val target = calendar.at(today, if (running) block.endMinute else block.startMinute)
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                Text(
                    block.subjectName,
                    style = if (size == CardSize.small) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                    Text(if (running) L("endet in") else L("beginnt in"), style = MaterialTheme.typography.bodyLarge, color = secondary)
                    Text(DateText.relative(second, target), style = MaterialTheme.typography.bodyLarge.digits, color = MaterialTheme.colorScheme.primary)
                }
                if (size != CardSize.small) BlockRow(block, isCurrent = running)
            }
        } else {
            val next = model.nextLesson(now)
            if (next != null) {
                val name = model.subject(next)?.name ?: L("Unbekanntes Fach")
                val day = calendar.instant(next.date)
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    DateText.skeleton(day, "EEEE", calendar) + ", " + TimeText.clock(next.startMinute.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                )
            } else {
                Text(L("Keine weiteren Stunden."), color = secondary)
            }
        }
    }
}

/** Meldungen per school day this week as a small bar chart. */
@Composable
internal fun WeekChartMiniCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    val perDay = (model.weekSummary(now)?.perDay ?: emptyList()).take(5).map { it.toInt() }
    val today = model.calendar.mondayIndex(now)
    val primary = MaterialTheme.colorScheme.primary
    Card(title = L("Wochenverlauf"), symbol = "chart.bar.xaxis") {
        BarChart(
            values = perDay,
            labels = perDay.indices.map { WeekdayText.short(it) },
            barColor = { i -> if (i == today) primary else primary.copy(alpha = 0.38f) },
            showYAxis = size != CardSize.small,
            contentDescription = L("Meldungen pro Tag"),
            highlight = today,
            modifier = Modifier.fillMaxWidth().height(if (size == CardSize.large) 160.dp else 90.dp),
        )
    }
}

/** How long until the next holidays, or how long they still last. */
@Composable
internal fun TodosCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val open = model.tasks.filter { it.kind == TaskKind.todo && !it.isDone }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Card(title = L("To-dos"), symbol = "checklist") {
        when {
            open.isEmpty() -> Text(L("Nichts zu tun"), color = secondary)
            size == CardSize.small -> Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.Bottom) {
                NumberText(open.size, MaterialTheme.typography.headlineLarge)
                Text(L("offen"), style = MaterialTheme.typography.bodySmall, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                open.take(if (size == CardSize.large) 8 else 3).forEach { task -> key(task.id) { CardTaskRow(task, now) } }
            }
        }
        if (size != CardSize.small) {
            rememberLink(Link.NewTask(TaskKind.todo))?.let { CardButton(L("To-do hinzufügen"), "plus", it) }
        }
    }
}

/** Estimated average from the student's own grades (Notenrechner). Never mixed with Meldungen. */
@Composable
internal fun GradesCard(size: CardSize) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Card(title = L("Notenschnitt"), symbol = "graduationcap") {
        val overall = model.overallGradeAverage
        if (overall != null) {
            Text(model.gradeText(overall), style = bigNumber(size).digits)
            Text(L("geschätzt"), style = MaterialTheme.typography.bodySmall, color = secondary)
            if (size != CardSize.small) {
                val graded = model.subjects.mapNotNull { s -> model.gradeSummary(s.id)?.exact?.let { s to it } }
                graded.take(if (size == CardSize.large) 8 else 3).forEach { (subject, average) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subject.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(model.gradeText(average), style = MaterialTheme.typography.bodyMedium.digits, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Text(
                L("Trag deine Noten ein, dann rechnet Meldo deinen Schnitt aus."),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
        }
        rememberLink(Link.Page("grades"))?.let { CardLink(L("Notenrechner"), onClick = it) }
    }
}
