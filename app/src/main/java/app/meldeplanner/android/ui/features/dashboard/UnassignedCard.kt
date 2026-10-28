@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.dashboard

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.features.participation.SubjectPickerSheet
import java.time.Instant

/**
 * Meldungen logged from a widget, the Quick Settings tile or a notification
 * outside a lesson have no subject yet; one tap assigns them all. Port of UnassignedCard.swift.
 */
@Composable
fun UnassignedCard(now: Instant) {
    val model = LocalAppModel.current
    var assigning by remember { mutableStateOf(false) }
    val unassigned = model.unassignedEntries(now)
    if (unassigned.isEmpty()) return
    Card(title = L("Ohne Fach"), symbol = "questionmark.circle") {
        Text(if (unassigned.size == 1) L("1 Meldung von heute hat noch kein Fach.") else L("%lld Meldungen von heute haben noch kein Fach.", unassigned.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { assigning = true }, shapes = ButtonDefaults.shapes()) { Text(L("Fach zuordnen")) }
    }
    if (assigning) {
        SubjectPickerSheet(onDismiss = { assigning = false }) { subjectID ->
            for (entry in unassigned) model.reassignParticipation(entry.id, subjectID)
        }
    }
}
