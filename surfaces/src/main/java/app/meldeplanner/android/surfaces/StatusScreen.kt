package app.meldeplanner.android.surfaces

import android.app.NotificationManager
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Notifications
import app.meldeplanner.android.data.LocalStore
import app.meldeplanner.android.ui.navigation.LocalFeatures
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.rows.ActionRow
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.StateBadge
import app.meldeplanner.android.ui.rows.ValueRow
import app.meldeplanner.android.ui.rows.formatDate
import app.meldeplanner.android.ui.theme.StateColors
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.melde_ffi.coreVersion

/**
 * Shows whether widgets, Live Updates and notifications can work on this install,
 * with one-tap tests, so "why doesn't X show up?" is answered without guessing.
 * Port of StatusView.swift with the Android equivalents of each check.
 */
@Composable
fun StatusScreen() {
    val model = LocalAppModel.current
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    // Re-read the system state whenever the screen comes back (e.g. from the system settings).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val state = remember(refresh, model.data) {
        val file = LocalStore.standard(context).file
        StatusState(
            widgets = Surfaces.placedWidgetCount(context),
            notifications = Notifications.hasPermission(context),
            exactAlarms = Notifications.canScheduleExactAlarms(context),
            promoted = Notifications.canPostPromoted(context),
            dataPath = file.absolutePath,
            dataSize = if (file.exists()) file.length() else null,
        )
    }

    val enableCheckIns = Notifications.rememberPermissionRequester { allowed ->
        model.setAskAfterLesson(allowed)
        if (!allowed) message = L("Mitteilungen sind aus. Erlaube sie in den Einstellungen, damit Erinnerungen ankommen.")
        refresh++
    }
    val sendTest = Notifications.rememberPermissionRequester { allowed ->
        refresh++
        if (!allowed) {
            message = L("Mitteilungen sind aus. Erlaube sie in den Einstellungen, damit Erinnerungen ankommen.")
            return@rememberPermissionRequester
        }
        val app = context.applicationContext
        model.scope.launch {
            delay(5_000)
            Notifications.ensureChannels(app)
            app.getSystemService(NotificationManager::class.java)
                ?.notify("status-test".hashCode(), LessonCheckIns.testNotification(app, "status-test"))
        }
        message = L("Sperre jetzt dein Handy oder wechsle zum Startbildschirm.")
    }

    // Results of the buttons (tile, preview, test notification) as a snackbar, where the student is looking.
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        message = null
    }
    MeldoScreen(L("Status"), snackbarHostState = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "widgets") {
                FormSection(header = L("Widgets")) {
                    StatusRow(
                        L("Widgets und Melden-Knopf"),
                        ok = state.widgets > 0,
                        detail = if (state.widgets > 0) {
                            L("Widgets auf dem Startbildschirm: %lld. Sie lesen dieselben Daten wie die App.", state.widgets)
                        } else {
                            L("Noch kein Widget platziert. Halte den Startbildschirm gedrückt und wähle Widgets → Meldo.")
                        },
                    )
                    // The Quick Settings tile, like adding the control to the iOS Control Center.
                    ActionRow(L("Melden-Kachel hinzufügen"), symbol = "plus") {
                        Surfaces.requestAddTile(context) { added ->
                            message = if (added) L("Die Melden-Kachel ist in den Schnelleinstellungen.")
                            else L("Wische zweimal von oben nach unten und tippe auf den Stift, um die Kachel selbst hinzuzufügen.")
                        }
                    }
                }
            }
            item(key = "live") {
                FormSection(header = L("Live-Update")) {
                    val liveOk = state.notifications && state.promoted && model.preferences.liveActivities
                    StatusRow(
                        L("Live-Updates"),
                        ok = liveOk,
                        detail = when {
                            liveOk -> L("Erscheinen während einer Stunde, wenn du Meldo öffnest oder meldest.")
                            !model.preferences.liveActivities -> L("In den Meldo-Einstellungen unter Mitteilungen ausgeschaltet.")
                            else -> L("In den Android-Einstellungen für Meldo ausgeschaltet.")
                        },
                    )
                    ActionRow(L("Vorschau anzeigen"), symbol = "sparkles") {
                        message = when (val result = Surfaces.previewLiveLesson(context, model.data, model.calendar, model.currentDate)) {
                            LivePreviewResult.shown -> L("Schau in die Statusleiste oder auf den Sperrbildschirm.")
                            LivePreviewResult.disabledInSettings -> L("Live-Updates sind in den Android-Einstellungen aus.")
                            LivePreviewResult.noLessons -> L("Lege zuerst Stunden im Stundenplan an.")
                            is LivePreviewResult.failed -> L("Konnte nicht starten: %@", result.message)
                        }
                    }
                    if (!state.promoted) {
                        ActionRow(L("Android-Einstellungen öffnen"), symbol = "gearshape") { Notifications.openNotificationSettings(context) }
                    }
                }
            }
            item(key = "notifications") {
                FormSection(header = L("Mitteilungen")) {
                    StatusRow(
                        L("Mitteilungen"),
                        ok = state.notifications,
                        detail = if (state.notifications) {
                            L("Erlaubt.")
                        } else {
                            L("Aus. Erlaube sie in den Android-Einstellungen unter Apps → Meldo → Benachrichtigungen.")
                        },
                    )
                    StatusRow(
                        L("Pünktliche Erinnerungen"),
                        ok = state.exactAlarms,
                        detail = if (state.exactAlarms) {
                            L("Nachfragen und Erinnerungen kommen auf die Minute.")
                        } else {
                            L("Ohne „Wecker und Erinnerungen“ kommen Nachfragen bis zu zehn Minuten später.")
                        },
                    )
                    if (!state.exactAlarms) {
                        ActionRow(L("Wecker und Erinnerungen erlauben"), symbol = "clock") { Notifications.openExactAlarmSettings(context) }
                    }
                    if (!state.notifications) {
                        ActionRow(L("Android-Einstellungen öffnen"), symbol = "gearshape") { Notifications.openNotificationSettings(context) }
                    }
                    if (!model.preferences.askAfterLesson) {
                        ActionRow(L("Nach jeder Stunde nachfragen einschalten"), symbol = "bell") { enableCheckIns() }
                    }
                    ActionRow(L("Test-Mitteilung in 5 Sekunden"), symbol = "bell.badge") { sendTest() }
                }
            }
            item(key = "connections") {
                // Only in builds with Teams, once it is switched on.
                if (LocalFeatures.current["teams"]?.isAvailable(model) == true) FormSection(header = L("Verbindungen")) {
                    val teams = model.data.teams
                    StatusRow(
                        L("Microsoft Teams"),
                        ok = teams != null,
                        detail = if (teams != null) {
                            listOfNotNull(
                                teams.account,
                                teams.lastSync?.let { L("Zuletzt: %@", syncText(it, model.calendar.zone)) },
                            ).joinToString("\n")
                        } else {
                            L("Nicht verbunden.")
                        },
                    )
                }
            }
            item(key = "data") {
                FormSection(header = L("Daten")) {
                    StatusRow(
                        L("Datendatei"),
                        ok = model.storageProblem == null,
                        detail = listOfNotNull(
                            state.dataPath,
                            state.dataSize?.let { Formatter.formatShortFileSize(context, it) },
                            model.storageProblem,
                        ).joinToString("\n"),
                    )
                    ValueRow(L("Rust-Kern"), remember { runCatching { coreVersion() }.getOrDefault(L("Unbekannt")) })
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

private data class StatusState(
    val widgets: Int,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val promoted: Boolean,
    val dataPath: String,
    val dataSize: Long?,
)

private fun syncText(instant: Instant, zone: java.time.ZoneId): String = formatDate(instant, "dMMMyHHmm", zone)

@Composable
private fun StatusRow(title: String, ok: Boolean, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DS.spacingL, vertical = DS.spacingM).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.spacingL),
    ) {
        StateBadge(
            if (ok) "checkmark" else "exclamationmark.triangle.fill",
            if (ok) StateColors.green() else StateColors.orange(),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
