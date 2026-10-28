@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.meldeplanner.android.data.PlayAuthorization
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.BackupKeyProblem
import app.meldeplanner.android.data.DriveProblem
import app.meldeplanner.android.data.GoogleClient
import app.meldeplanner.android.data.GoogleConfig
import app.meldeplanner.android.data.GoogleFailure
import app.meldeplanner.android.data.TeamsFailure
import app.meldeplanner.android.model.DriveState
import app.meldeplanner.android.model.connectGoogle
import app.meldeplanner.android.model.deleteDriveBackup
import app.meldeplanner.android.model.disconnectGoogle
import app.meldeplanner.android.model.driveHasBackup
import app.meldeplanner.android.model.syncWithGoogleDrive
import app.meldeplanner.android.model.unlockDrive
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.untis.ConnectActionRow
import app.meldeplanner.android.ui.features.untis.ConnectIconLabel
import app.meldeplanner.android.ui.features.untis.ConnectLabeledRow
import app.meldeplanner.android.ui.features.untis.ConnectList
import app.meldeplanner.android.ui.features.untis.ConnectNote
import app.meldeplanner.android.ui.features.untis.ConnectSection
import app.meldeplanner.android.ui.features.untis.TeamsAuthSession
import app.meldeplanner.android.ui.features.untis.connectRelativeTime
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import app.meldeplanner.android.ui.theme.StateColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Google Drive sync and backup: sign in, set or enter the backup password, then
 * Meldo keeps the encrypted backup current on its own. Port of
 * meldo-ios/MeldePlanner/Features/More/GoogleDriveView.swift.
 */
@Composable
fun GoogleDriveScreen() {
    val model = LocalAppModel.current
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    // Whether this Google account already has a Meldo backup; null while checking.
    var hasBackup by remember { mutableStateOf<Boolean?>(null) }
    // The check failed (offline, or a damaged backup); `checks` restarts it.
    var checkFailed by remember { mutableStateOf(false) }
    var checks by remember { mutableIntStateOf(0) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val state = model.driveState
    // A wrong, too short or mismatched password: red text under the button, a shake and an
    // error haptic (iOS: the wrong-passcode shake), so it can't go unnoticed.
    var passwordError by remember { mutableStateOf<String?>(null) }
    var rejections by remember { mutableIntStateOf(0) }
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    val reduceMotion = app.meldeplanner.android.ui.components.rememberReduceMotion()
    val haptic = app.meldeplanner.android.ui.components.rememberHaptic()
    LaunchedEffect(rejections) {
        if (rejections == 0) return@LaunchedEffect
        haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
        if (reduceMotion) return@LaunchedEffect
        shake.snapTo(0f)
        for (x in listOf(-12f, 10f, -8f, 6f, -3f, 0f)) {
            shake.animateTo(x, androidx.compose.animation.core.tween(60, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    fun reject(text: String) {
        passwordError = text
        rejections += 1
    }

    LaunchedEffect(state, checks) {
        // What was checked belongs to the old state.
        if (state != DriveState.needsPassword) {
            hasBackup = null
            checkFailed = false
            return@LaunchedEffect
        }
        if (hasBackup != null) return@LaunchedEffect
        checkFailed = false
        try {
            hasBackup = model.driveHasBackup()
        } catch (e: BackupKeyProblem.Damaged) {
            checkFailed = true
            message = L("Das Backup in Google Drive ist beschädigt.")
        } catch (e: GoogleFailure) {
            checkFailed = true
            message = googleMessage(e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            checkFailed = true
            message = googleMessage(GoogleFailure.Network)
        }
    }

    fun connect(access: String?) {
        model.scope.launch {
            try {
                model.connectGoogle(access ?: throw GoogleFailure.Denied(""))
                hasBackup = null
            } catch (e: GoogleFailure) {
                message = googleMessage(e)
            } finally {
                working = false
            }
        }
    }

    // Google's consent screen (Google Play services); coming back without OK means cancelled.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            connect(PlayAuthorization.result(context, result.data)?.accessToken)
        } else {
            working = false
        }
    }

    fun signIn() {
        model.scope.launch {
            working = true
            message = null
            try {
                val result = PlayAuthorization.authorize(context)
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    connect(result.accessToken)
                }
            } catch (e: GoogleFailure) {
                message = googleMessage(e)
                working = false
            }
        }
    }

    fun unlock(creating: Boolean) {
        if (creating && password != repeated) {
            reject(L("Die Passwörter stimmen nicht überein."))
            return
        }
        model.scope.launch {
            working = true
            message = null
            passwordError = null
            try {
                model.unlockDrive(password, creating)
                password = ""
                repeated = ""
            } catch (e: BackupKeyProblem) {
                when (e) {
                    BackupKeyProblem.WrongPassword -> reject(L("Falsches Passwort."))
                    BackupKeyProblem.TooShort -> reject(L("Das Passwort braucht mindestens 8 Zeichen."))
                    BackupKeyProblem.Damaged -> message = L("Das Backup in Google Drive ist beschädigt.")
                    BackupKeyProblem.NotSaved -> message = L("Der Schlüssel konnte auf diesem Gerät nicht gespeichert werden. Versuche es noch einmal.")
                }
            } catch (e: DriveProblem.KeyChanged) {
                // The backup appeared or vanished meanwhile: check again, then ask again.
                message = L("Das Backup wurde auf einem anderen Gerät gelöscht oder neu angelegt. Gib das Backup-Passwort ein.")
                hasBackup = null
                checks += 1
            } catch (e: GoogleFailure) {
                message = googleMessage(e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = googleMessage(GoogleFailure.Network)
            } finally {
                working = false
            }
        }
    }

    fun deleteBackup() {
        model.scope.launch {
            working = true
            message = null
            try {
                model.deleteDriveBackup()
                hasBackup = null
                checkFailed = false
            } catch (e: GoogleFailure) {
                message = googleMessage(e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = googleMessage(GoogleFailure.Network)
            } finally {
                working = false
            }
        }
    }

    fun disconnect() {
        model.disconnectGoogle()
        password = ""
        repeated = ""
        hasBackup = null
        message = null
    }

    MeldoScreen(L("Google Drive")) { padding ->
        ConnectList(padding) {
            if (GoogleConfig.isConfigured && state == DriveState.needsPassword) {
                item(key = "password-missing") { PasswordMissingCard() }
            }
            item {
                ConnectSection {
                    Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                        ConnectIconLabel(L("Backup in Google Drive"), "externaldrive", headline = true)
                        Text(
                            L("Sichert Fächer, Stundenplan, Meldungen, Noten, Aufgaben und Dokumente in deinem Google Drive und gleicht sie zwischen deinen Geräten ab. Meldo sieht nur seinen eigenen versteckten Ordner, nicht deine anderen Dateien."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!GoogleConfig.isConfigured) {
                item {
                    ConnectSection {
                        ConnectNote(L("In dieser Version noch nicht verfügbar."), "externaldrive.badge.xmark", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val account = model.driveAccount
                if (state != DriveState.off && account != null) {
                    item { ConnectSection { ConnectLabeledRow(L("Konto"), account) } }
                }
                when (state) {
                    DriveState.off -> {
                        item(key = "warning") { BackupWarning() }
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
                                    Icon(sfSymbol("person.crop.circle.badge.plus"), null, Modifier.size(20.dp))
                                }
                                Spacer(Modifier.size(DS.spacingS))
                                Text(L("Mit Google anmelden"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    DriveState.needsPassword -> {
                        model.driveProblem?.let { problem ->
                            item { ConnectSection { ConnectNote(problem, "exclamationmark.triangle", StateColors.orange()) } }
                        }
                        item(key = "password") {
                            val known = hasBackup
                            ConnectSection(
                                footer = if (known == true) {
                                    L("Gib das Backup-Passwort ein, das du auf deinem anderen Gerät gewählt hast. Passwort vergessen? Dann lösche das Backup und lege ein neues an.")
                                } else {
                                    L("Dein Backup wird mit diesem Passwort auf dem Gerät verschlüsselt, bevor es zu Google geht. Google kann es nicht lesen. Ohne das Passwort lässt es sich nicht wiederherstellen. Mindestens 8 Zeichen.")
                                },
                            ) {
                                when {
                                    known != null -> Column(
                                        Modifier.offset { androidx.compose.ui.unit.IntOffset(shake.value.dp.roundToPx(), 0) },
                                    ) {
                                        Column(Modifier.padding(DS.spacingM), verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                                            PasswordField(L("Backup-Passwort"), password, newPassword = !known, isError = passwordError != null) {
                                                password = it
                                                passwordError = null
                                            }
                                            if (!known) {
                                                PasswordField(L("Passwort wiederholen"), repeated, newPassword = true, isError = passwordError != null) {
                                                    repeated = it
                                                    passwordError = null
                                                }
                                            }
                                        }
                                        ConnectActionRow(
                                            if (known) L("Backup öffnen") else L("Backup erstellen"),
                                            working = working,
                                            enabled = !working && password.isNotEmpty(),
                                            onClick = { unlock(creating = !known) },
                                        )
                                        passwordError?.let { error ->
                                            Row(
                                                Modifier
                                                    .padding(start = DS.spacingL, end = DS.spacingL, bottom = DS.spacingM)
                                                    .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive },
                                                horizontalArrangement = Arrangement.spacedBy(DS.spacingS),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(sfSymbol("exclamationmark.triangle.fill"), null, Modifier.size(18.dp), tint = StateColors.red())
                                                Text(error, color = StateColors.red(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    checkFailed -> ConnectActionRow(L("Erneut versuchen")) {
                                        message = null
                                        checks += 1
                                    }
                                    else -> Row(
                                        Modifier.fillMaxWidth().padding(DS.spacingL),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(L("Wird geprüft …"), Modifier.weight(1f))
                                        LoadingIndicator(Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                        item {
                            ConnectSection {
                                // Also the way out of a damaged backup.
                                if (hasBackup == true || checkFailed) {
                                    ConnectActionRow(L("Backup in Google Drive löschen"), destructive = true, enabled = !working) { confirmingDelete = true }
                                }
                                ConnectActionRow(L("Google Drive trennen"), destructive = true, enabled = !working) { disconnect() }
                            }
                        }
                    }
                    DriveState.on -> {
                        item {
                            ConnectSection(
                                footer = listOfNotNull(
                                    model.driveProblem,
                                    L("Meldo sichert nach jeder Änderung und beim Öffnen. Passwörter für WebUntis und Teams bleiben auf jedem Gerät."),
                                ).joinToString("\n\n"),
                            ) {
                                val last = model.lastDriveSync
                                ConnectLabeledRow(
                                    L("Zuletzt gesichert"),
                                    when {
                                        model.isSyncingWithDrive -> L("Wird gesichert …")
                                        last != null -> connectRelativeTime(last)
                                        else -> L("Noch nie")
                                    },
                                )
                                ConnectActionRow(
                                    L("Jetzt sichern"), symbol = "arrow.triangle.2.circlepath",
                                    working = model.isSyncingWithDrive, enabled = !model.isSyncingWithDrive,
                                ) { model.scope.launch { model.syncWithGoogleDrive() } }
                            }
                        }
                        item {
                            ConnectSection {
                                val busy = model.isSyncingWithDrive || working
                                ConnectActionRow(L("Google Drive trennen"), destructive = true, enabled = !busy) { disconnect() }
                                ConnectActionRow(L("Backup in Google Drive löschen"), destructive = true, enabled = !busy) { confirmingDelete = true }
                            }
                        }
                    }
                }
            }
            message?.let { text ->
                item(key = "message") {
                    Text(text, color = StateColors.red(), modifier = meldoAnimateItem().padding(horizontal = DS.spacingXL))
                }
            }
        }
    }

    if (confirmingDelete) {
        MeldoAlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(L("Backup in Google Drive löschen?")) },
            text = { Text(L("Das Backup verschwindet für alle deine Geräte. Die Daten auf diesem Gerät bleiben.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    deleteBackup()
                }) { Text(L("Backup löschen"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(L("Abbrechen")) } },
        )
    }
}

/** Before sign-in, in a color that stands out: everything goes to Drive, encrypted. */
@Composable
private fun BackupWarning() {
    val orange = StateColors.orange()
    FormSection(color = orange.copy(alpha = 0.18f)) {
        Row(
            Modifier.fillMaxWidth().padding(DS.spacingL).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(sfSymbol("exclamationmark.triangle.fill"), null, Modifier.size(28.dp), tint = orange)
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                Text(L("Alle Daten werden gesichert"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    L("Meldo lädt alles in dein Google Drive: Fächer, Stundenplan, Meldungen, Noten, Aufgaben, Abwesenheiten und Dokumente. Vorher wird alles auf deinem Gerät mit deinem Backup-Passwort verschlüsselt, sodass Google nichts lesen kann. Passwörter und Anmeldungen bleiben auf dem Gerät."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, newPassword: Boolean, isError: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        shape = DS.rowShape,
        modifier = Modifier.fillMaxWidth().semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
    )
}

private fun googleMessage(failure: GoogleFailure): String = when (failure) {
    GoogleFailure.NotConfigured -> L("In dieser Version noch nicht verfügbar.")
    GoogleFailure.Cancelled -> L("Anmeldung abgebrochen.")
    is GoogleFailure.Denied -> L("Google hat die Anfrage abgelehnt.")
    GoogleFailure.Expired -> L("Deine Google-Anmeldung ist abgelaufen. Melde dich erneut an.")
    GoogleFailure.Network -> L("Keine Verbindung zu Google. Prüfe dein Internet.")
}

/** Signed in, but no backup password yet: the step students miss, so it heads the screen in orange. */
@Composable
private fun PasswordMissingCard() {
    val orange = StateColors.orange()
    androidx.compose.material3.Surface(
        shape = DS.rowShape,
        color = orange.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, orange.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingL),
    ) {
        Row(
            Modifier.padding(DS.spacingL),
            horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(sfSymbol("key"), null, Modifier.size(28.dp), tint = orange)
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                Text(
                    L("Fast fertig: Gib dein Backup-Passwort ein"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    L("Ohne Passwort sichert Meldo nichts. Hast du auf einem anderen Gerät schon ein Backup, nimm dasselbe Passwort."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
