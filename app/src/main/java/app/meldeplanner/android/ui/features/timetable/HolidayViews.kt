package app.meldeplanner.android.ui.features.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.Holiday
import app.meldeplanner.android.model.HolidayText
import app.meldeplanner.android.model.holiday
import app.meldeplanner.android.ui.components.Badge
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.tasks.groupShape
import app.meldeplanner.android.ui.features.tasks.rowAnimation
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.MeldoColors

private val tabular = TextStyle(fontFeatureSettings = "tnum")

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

/**
 * Mehr → Ferien: the school's current and upcoming holidays. Past ones are left out,
 * so the list doesn't grow with every school year WebUntis keeps. Port of HolidayListView.
 */
@Composable
fun HolidayListScreen() {
    val model = LocalAppModel.current
    val today = model.calendar.startOfDay(model.currentDate)
    MeldoScreen(title = L("Ferien")) { padding ->
        if (model.data.holidays.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyStateCard(
                    symbol = "sun.horizon",
                    title = L("Keine Ferien eingetragen"),
                    text = L("Verbinde WebUntis, dann erscheinen hier die Ferien deiner Schule. In den Ferien fragt Meldo nicht nach Meldungen."),
                )
            }
            return@MeldoScreen
        }
        val holidays = model.data.holidays
            .filter { model.calendar.startOfDay(it.lastDay) >= today }
            .sortedBy { it.firstDay }
        if (holidays.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyStateCard(
                    symbol = "sun.horizon",
                    title = L("Keine kommenden Ferien"),
                    text = L("Die nächsten Ferien erscheinen hier, sobald deine Schule sie in WebUntis einträgt."),
                )
            }
            return@MeldoScreen
        }
        val current = model.holiday(today)?.id
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = DS.spacingL, end = DS.spacingL,
                top = padding.calculateTopPadding() + DS.spacingS,
                bottom = padding.calculateBottomPadding() + DS.spacingXL,
            ),
            // Expressive grouped rows: separate tiles, rounder at the ends of the group.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(holidays, key = { _, h -> h.id }) { index, holiday ->
                val shape = groupShape(index, holidays.size)
                Column(rowAnimation().background(MaterialTheme.colorScheme.surfaceContainer, shape)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DS.spacingL, vertical = DS.spacingM)
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(holiday.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                HolidayText.range(holiday, model.calendar),
                                style = MaterialTheme.typography.bodyMedium.merge(tabular),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (current == holiday.id) Badge(L("Jetzt"), MeldoColors.orange)
                    }
                }
            }
            item {
                Text(
                    L("In den Ferien fallen alle Stunden aus und Meldo fragt nicht nach Meldungen."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingS),
                )
            }
        }
    }
}
