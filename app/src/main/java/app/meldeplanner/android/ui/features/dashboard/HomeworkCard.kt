package app.meldeplanner.android.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.CardSize
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.model.homeworkStreak
import app.meldeplanner.android.model.upcomingHomework
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.tasks.TaskEditorScreen
import app.meldeplanner.android.ui.features.tasks.TaskListScreen
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.theme.StateColors
import java.time.Instant

/** Homework due soon, check-off in place, and the homework streak. Port of HomeworkCard.swift. */
@Composable
fun HomeworkCard(now: Instant, size: CardSize = CardSize.medium) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val upcoming = model.upcomingHomework(now, limit = if (size == CardSize.large) 8 else 3)
    val streak = model.homeworkStreak(now)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val openAll = { navigator.push { TaskListScreen() } }
    Card(title = L("Hausaufgaben"), symbol = "book") {
        if (size == CardSize.small) {
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.Bottom) {
                NumberText(upcoming.size, MaterialTheme.typography.headlineLarge)
                Text(L("bald fällig"), style = MaterialTheme.typography.bodySmall, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
            }
            CardLink(L("Alle"), onClick = openAll)
        } else if (upcoming.isEmpty()) {
            Text(L("Für heute und morgen ist alles erledigt."), color = secondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                upcoming.forEach { task -> key(task.id) { CardTaskRow(task, now) } }
            }
        }
        if (size != CardSize.small) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val tint = if (streak > 0) StateColors.orange() else secondary
                Icon(sfSymbol("flame"), null, Modifier.size(16.dp), tint = tint)
                Spacer(Modifier.size(DS.spacingXS))
                Text(
                    L("%lld Tage pünktlich in Folge", streak),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint,
                    modifier = Modifier.weight(1f),
                )
                CardLink(L("Alle"), onClick = openAll)
            }
            CardButton(L("Hausaufgabe eintragen"), "plus") {
                navigator.push { TaskEditorScreen(task = null, kind = TaskKind.homework, onDismiss = { navigator.pop() }) }
            }
        }
    }
}
