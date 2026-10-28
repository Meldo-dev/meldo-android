@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.TeamsClient
import app.meldeplanner.android.data.TeamsConfig
import app.meldeplanner.android.data.TeamsFailure
import app.meldeplanner.android.model.connectTeams
import app.meldeplanner.android.model.disconnectTeams
import app.meldeplanner.android.model.refreshTeams
import app.meldeplanner.android.model.teamsLink
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.untis.TeamsAuthSession
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.rows.ConnectActionRow
import app.meldeplanner.android.ui.rows.ConnectIconLabel
import app.meldeplanner.android.ui.rows.ConnectLabeledRow
import app.meldeplanner.android.ui.rows.ConnectList
import app.meldeplanner.android.ui.rows.ConnectNote
import app.meldeplanner.android.ui.rows.ConnectSection
import app.meldeplanner.android.ui.rows.connectRelativeTime
import app.meldeplanner.android.ui.rows.meldoAnimateItem
import app.meldeplanner.android.ui.theme.StateColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

/**
 * Connect Microsoft Teams: sign in on Microsoft's page, then assignments appear in
 * Aufgaben. Port of meldo-ios/MeldePlanner/Features/Untis/TeamsConnectView.swift.
 */
@Composable
fun TeamsConnectScreen() {
    val model = LocalAppModel.current
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Coming back to Meldo without the redirect means the student closed Microsoft's page.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && TeamsAuthSession.isWaiting) {
                uiScope.launch {
                    // The redirect activity hands over the code just before Meldo resumes.
                    delay(600)
                    if (TeamsAuthSession.isWaiting) TeamsAuthSession.cancel()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun signIn() {
        val auth = TeamsClient.authorization()
        model.scope.launch {
            working = true
            message = null
            try {
                val callback = TeamsAuthSession.authenticate(context, auth.url, auth.state)
                val code = TeamsClient.code(callback, auth.state)
                val added = model.connectTeams(code, auth.verifier)
                message = L("Verbunden: %lld Aufgaben übernommen.", added)
            } catch (e: TeamsFailure.Cancelled) {
                // The student closed Microsoft's page.
                message = null
            } catch (e: TeamsFailure) {
                message = teamsMessage(e)
            } catch (e: android.content.ActivityNotFoundException) {
                // No browser to show Microsoft's page.
                message = teamsMessage(TeamsFailure.Network)
            } finally {
                working = false
            }
        }
    }

    fun refresh() {
        model.scope.launch {
            working = true
            try {
                val added = model.refreshTeams()
                message = L("Aktualisiert: %lld neue Aufgaben.", added)
            } catch (e: TeamsFailure) {
                message = teamsMessage(e)
            } finally {
                working = false
            }
        }
    }

    MeldoScreen(L("Microsoft Teams")) { padding ->
        ConnectList(padding) {
            item {
                ConnectSection {
                    Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                        ConnectIconLabel(L("Aufgaben aus Microsoft Teams"), "person.3", headline = true)
                        Text(
                            L("Meldo übernimmt die Aufgaben deiner Teams-Kurse als Hausaufgaben. Du meldest dich direkt bei Microsoft an; dein Passwort sieht Meldo nie."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val link = model.teamsLink
            if (!TeamsConfig.isConfigured) {
                item {
                    ConnectSection {
                        Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                            ConnectNote(L("Noch nicht eingerichtet"), "wrench.and.screwdriver", StateColors.orange(), inset = false)
                            Text(
                                L("Für Teams braucht Meldo eine App-Registrierung bei Microsoft (Azure). Sobald sie eingetragen ist, kannst du dich hier anmelden. Manche Schulen müssen Meldo außerdem freigeben."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (link != null) {
                item {
                    ConnectSection(footer = L("Neue Aufgaben kommen automatisch dazu, wenn du die App öffnest (höchstens alle drei Stunden).")) {
                        if (link.account.isNotEmpty()) ConnectLabeledRow(L("Konto"), link.account)
                        link.lastSync?.let { last -> ConnectLabeledRow(L("Zuletzt aktualisiert"), connectRelativeTime(last)) }
                    }
                }
                item {
                    ConnectSection {
                        ConnectActionRow(
                            L("Jetzt aktualisieren"), symbol = "arrow.triangle.2.circlepath",
                            working = working, enabled = !working, onClick = ::refresh,
                        )
                        ConnectActionRow(L("Teams trennen"), symbol = "trash", destructive = true, enabled = !working, onClick = { model.disconnectTeams() })
                    }
                }
            } else {
                item(key = "sign-in") {
                    Button(
                        onClick = ::signIn,
                        enabled = !working,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingL).heightIn(min = 56.dp),
                    ) {
                        if (working) {
                            LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(sfSymbol("person.badge.key.fill"), null, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.size(DS.spacingS))
                        Text(L("Mit Microsoft anmelden"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            message?.let { text ->
                item(key = "message") {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = meldoAnimateItem().padding(horizontal = DS.spacingXL),
                    )
                }
            }
        }
    }
}

private fun teamsMessage(failure: TeamsFailure): String = when (failure) {
    TeamsFailure.NotConfigured -> L("Teams ist in dieser Version noch nicht eingerichtet.")
    TeamsFailure.Cancelled -> L("Anmeldung abgebrochen.")
    is TeamsFailure.Denied ->
        if (failure.detail.isEmpty()) {
            L("Microsoft hat die Anfrage abgelehnt. Eventuell muss deine Schule Meldo erst freigeben.")
        } else {
            L("Microsoft hat die Anfrage abgelehnt: %@", failure.detail)
        }
    TeamsFailure.Expired -> L("Deine Microsoft-Anmeldung ist abgelaufen. Melde dich erneut an.")
    TeamsFailure.Network -> L("Keine Verbindung zu Microsoft. Prüfe dein Internet.")
}
