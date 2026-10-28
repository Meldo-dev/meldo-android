@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.surfaces.Notifications
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.Mascot
import app.meldeplanner.android.ui.components.MascotMood
import app.meldeplanner.android.ui.theme.StateColors

/**
 * One-time suggestion on the start screen to turn on "Hast du dich gemeldet?"
 * after each lesson. Disappears once answered either way. Port of CheckInPromptCard.swift.
 */
@Composable
fun CheckInPromptCard() {
    val model = LocalAppModel.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val requestPermission = Notifications.rememberPermissionRequester { allowed ->
        denied = !allowed
        if (allowed) model.setAskAfterLesson(true)
    }
    val p = model.preferences
    if (p.askAfterLesson || p.checkInPromptDismissed || model.lessons.isEmpty()) return
    Card(title = L("Nie mehr vergessen"), symbol = "bell.badge") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingM)) {
            Mascot(MascotMood.wave, 56.dp)
            Text(
                L("Soll ich dich nach jeder Stunde fragen, ob du dich gemeldet hast? Du antwortest direkt in der Mitteilung."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (denied) {
            Text(
                L("Mitteilungen sind aus. Erlaube sie in den Einstellungen, damit Erinnerungen ankommen."),
                style = MaterialTheme.typography.bodySmall,
                color = StateColors.orange(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
            Button(onClick = requestPermission, shapes = ButtonDefaults.shapes()) { Text(L("Ja, einschalten")) }
            FilledTonalButton(onClick = { model.dismissCheckInPrompt() }, shapes = ButtonDefaults.shapes()) { Text(L("Später")) }
        }
    }
}
