@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.semantics.semantics
import app.meldeplanner.android.ui.rows.NumberText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.StatsCard
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.CustomizableCardGrid
import app.meldeplanner.android.ui.components.CustomizeButton
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.SubjectIcon
import app.meldeplanner.android.ui.components.WeekdayText
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.MeldoScreen
import uniffi.melde_ffi.FfiWeekSummary
import java.time.Instant
import app.meldeplanner.android.ui.charts.LineChart
import app.meldeplanner.android.ui.charts.BarChart

// Port of meldo-ios/MeldePlanner/Features/Statistics/StatisticsView.swift.

/**
 * Participation statistics from real entries only; empty data shows an empty
 * state, never a sample chart. Meldungen are activity counts, not grades.
 */
@Composable
fun StatisticsScreen() {
    val model = LocalAppModel.current
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val hasEntries = model.data.participation.isNotEmpty()
    val referenceDate = model.calendar.addWeeks(model.currentDate, weekOffset.toLong())

    MeldoScreen(
        title = L("Statistik"),
        actions = { if (hasEntries) CustomizeButton(editing) { editing = it } },
    ) { padding ->
        // Bar padding inside the scroll: cards scroll under the translucent bars.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(DS.spacingL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingL),
        ) {
            val summary = if (hasEntries) model.weekSummary(referenceDate) else null
            if (!hasEntries) {
                EmptyState()
            } else if (summary != null) {
                CustomizableCardGrid(
                    slots = model.preferences.statsLayout,
                    editing = editing,
                    onEditingChange = { editing = it },
                    title = ::statsCardTitle,
                    symbol = ::statsCardSymbol,
                    allKinds = StatsCard.entries,
                    save = { model.setStatsLayout(it) },
                ) { card, _ ->
                    when (card) {
                        StatsCard.perDay -> WeekChartCard(summary, weekOffset) { weekOffset = it }
                        StatsCard.bySubject -> SubjectBreakdownCard(summary)
                        StatsCard.trend -> TrendCard(referenceDate)
                    }
                }
            }
        }
    }
}

fun statsCardTitle(card: StatsCard): String = when (card) {
    StatsCard.perDay -> L("Meldungen pro Tag")
    StatsCard.bySubject -> L("Nach Fach")
    StatsCard.trend -> L("Letzte 8 Wochen")
}

fun statsCardSymbol(card: StatsCard): String = when (card) {
    StatsCard.perDay -> "chart.bar"
    StatsCard.bySubject -> "books.vertical"
    StatsCard.trend -> "chart.line.uptrend.xyaxis"
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = DS.spacingXL * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
    ) {
        Icon(sfSymbol("chart.bar"), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(L("Noch keine Meldungen"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            L("Tippe im Unterricht auf „Melden“. Hier siehst du dann deine Entwicklung."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DS.spacingXL),
        )
    }
}

@Composable
private fun WeekChartCard(summary: FfiWeekSummary, weekOffset: Int, setWeekOffset: (Int) -> Unit) {
    val weekTitle = when (weekOffset) {
        0 -> L("Diese Woche")
        -1 -> L("Letzte Woche")
        else -> L("Vor %lld Wochen", -weekOffset)
    }
    val days = (0 until 7).map { summary.perDay.getOrNull(it)?.toInt() ?: 0 }
    val primary = MaterialTheme.colorScheme.primary
    val model = LocalAppModel.current
    val today = model.calendar.mondayIndex(model.currentDate)
    Card(title = L("Meldungen pro Tag"), symbol = "chart.bar") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { setWeekOffset(weekOffset - 1) }, shapes = IconButtonDefaults.shapes()) {
                Icon(sfSymbol("chevron.left"), L("Vorherige Woche"))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NumberText(summary.total.toInt(), MaterialTheme.typography.displaySmall)
                Text(weekTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = { setWeekOffset(weekOffset + 1) },
                shapes = IconButtonDefaults.shapes(),
                enabled = weekOffset < 0,
            ) {
                Icon(sfSymbol("chevron.right"), L("Nächste Woche"))
            }
        }
        BarChart(
            values = days,
            labels = (0 until 7).map { WeekdayText.short(it) },
            barColor = { primary },
            contentDescription = L("Meldungen pro Tag"),
            highlight = if (weekOffset == 0) today else null,
            modifier = Modifier.fillMaxWidth().height(190.dp),
        )
    }
}

@Composable
private fun SubjectBreakdownCard(summary: FfiWeekSummary) {
    val model = LocalAppModel.current
    Card(title = L("Nach Fach"), symbol = "books.vertical") {
        if (summary.perSubject.isEmpty()) {
            Text(L("Keine Meldungen in dieser Woche."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        summary.perSubject.forEach { item ->
            val subject = model.subject(item.subjectId)
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
            ) {
                SubjectIcon(subject, 28.dp)
                Text(subject?.name ?: L("Ohne Fach"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    item.count.toString(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(endingAt: Instant) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val weeks = (7 downTo 0).mapNotNull { offset ->
        val summary = model.weekSummary(calendar.addWeeks(endingAt, -offset.toLong())) ?: return@mapNotNull null
        calendar.instant(summary.weekStart) to summary.total.toInt()
    }
    val labels = weeks.map { (start, _) ->
        val format = android.icu.text.DateFormat.getInstanceForSkeleton("dMMM", app.meldeplanner.android.core.Localization.locale)
        format.timeZone = android.icu.util.TimeZone.getTimeZone(calendar.zone.id)
        format.format(java.util.Date.from(start))
    }
    Card(title = L("Letzte 8 Wochen"), symbol = "chart.line.uptrend.xyaxis") {
        LineChart(
            values = weeks.map { it.second },
            labels = labels,
            color = MaterialTheme.colorScheme.primary,
            contentDescription = L("Letzte 8 Wochen"),
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        model.preferences.weeklyGoal?.let { goal ->
            Text(L("Wochenziel: %lld", goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
