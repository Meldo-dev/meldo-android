package app.meldeplanner.android.model

import android.content.Context
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.BackupKey
import app.meldeplanner.android.data.BackupKeyProblem
import app.meldeplanner.android.data.DocumentFolder
import app.meldeplanner.android.data.DriveProblem
import app.meldeplanner.android.data.DriveSync
import app.meldeplanner.android.data.GoogleClient
import app.meldeplanner.android.data.PlayAuthorization
import app.meldeplanner.android.data.GoogleDriveFiles
import app.meldeplanner.android.data.GoogleFailure
import app.meldeplanner.android.surfaces.Reminders
import app.meldeplanner.android.surfaces.Surfaces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Base64

// Google Drive sync and backup for this device, port of
// meldo-ios/MeldePlanner/App/AppModel+GoogleDrive.swift and AppModel+Sync.swift.
// The Google sign-in and the unlocked backup key stay in this device's SecureStore
// (Android Keystore); the backup in Drive is sealed with a key only the student's
// backup password opens.

@Suppress("EnumEntryName")
enum class DriveState {
    /** Not signed in to Google. */
    off,

    /** Signed in; the backup password is still needed on this device. */
    needsPassword,
    on,
}

internal const val DRIVE_TOKEN_ACCOUNT = "google-drive"
internal const val DRIVE_KEY_ACCOUNT = "google-drive-key"
private const val DRIVE_PREFS = "meldo_google_drive"
private const val DRIVE_ACCOUNT_KEY = "gdrive.account"
private const val DRIVE_LAST_SYNC_KEY = "gdrive.lastSync"

private fun AppModel.drivePrefs() = context?.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)

/** At launch: what this device remembers of the Google connection. */
/** Signed in to Google, but the backup password isn't entered yet: nothing is backed up. */
val AppModel.drivePasswordMissing: Boolean get() = driveState == DriveState.needsPassword

internal fun AppModel.restoreDriveState() {
    driveState = storedDriveState()
    val prefs = drivePrefs() ?: return
    driveAccount = prefs.getString(DRIVE_ACCOUNT_KEY, null)
    lastDriveSync = prefs.getLong(DRIVE_LAST_SYNC_KEY, 0L).takeIf { it > 0 }?.let(Instant::ofEpochSecond)
}

private fun AppModel.storedDriveState(): DriveState {
    if (secrets().read(DRIVE_TOKEN_ACCOUNT) == null) return DriveState.off
    return if (storedDriveKey() == null) DriveState.needsPassword else DriveState.on
}

/** The unlocked data key and the ID of the key file it came from ("key:id" in the secure store). */
private fun AppModel.storedDriveKey(): Pair<ByteArray, String>? {
    val stored = secrets().read(DRIVE_KEY_ACCOUNT) ?: return null
    val parts = stored.split(":", limit = 2)
    if (parts.size != 2 || parts[1].isEmpty()) return null
    val key = runCatching { Base64.getDecoder().decode(parts[0]) }.getOrNull() ?: return null
    return if (key.size == 32) key to parts[1] else null
}

/** Finishes the Google sign-in with the access token Google Play services granted. */
suspend fun AppModel.connectGoogle(access: String) {
    val tokens = GoogleClient.Tokens(access, "", Instant.now().plusSeconds(50 * 60))
    // A key left from an earlier sign-in never carries over to this account.
    secrets().delete(DRIVE_KEY_ACCOUNT)
    // No refresh token on Android: Google Play services hands out new access tokens; this
    // only marks that the student connected Drive on this device.
    if (!secrets().save("play-services", DRIVE_TOKEN_ACCOUNT)) throw GoogleFailure.Denied("")
    driveAccess = tokens
    driveProblem = null
    driveState = DriveState.needsPassword
    driveAccount = GoogleClient().accountEmail(tokens.access)
    drivePrefs()?.edit()?.putString(DRIVE_ACCOUNT_KEY, driveAccount)?.apply()
}

/** Whether this Google account already has a Meldo backup (the password then opens it; otherwise it creates it). */
suspend fun AppModel.driveHasBackup(): Boolean = DriveSync.keyFile(drive()) != null

/**
 * Opens the existing backup with [password], or creates the backup key with it.
 * [creating] is what the screen showed: a backup that appeared or vanished meanwhile
 * throws [DriveProblem.KeyChanged], so one unconfirmed password never starts a new one.
 * Throws [BackupKeyProblem.WrongPassword] or [BackupKeyProblem.TooShort].
 */
suspend fun AppModel.unlockDrive(password: String, creating: Boolean) {
    val generation = driveGeneration
    val files = drive()
    val existing = DriveSync.keyFile(files)
    if ((existing == null) != creating) throw DriveProblem.KeyChanged
    val key: ByteArray
    val keyFile: BackupKey
    if (existing != null) {
        key = withContext(Dispatchers.Default) { existing.unlock(password) }
        keyFile = existing
    } else {
        val created = withContext(Dispatchers.Default) { BackupKey.create(password) }
        keyFile = DriveSync.storeKeyFile(created.first, files)
        // Another device created the backup at the same moment: its key counts.
        key = if (keyFile == created.first) created.second else withContext(Dispatchers.Default) { keyFile.unlock(password) }
    }
    if (generation != driveGeneration) throw GoogleFailure.Cancelled
    val stored = Base64.getEncoder().encodeToString(key) + ":" + DriveSync.keyID(keyFile)
    if (!secrets().save(stored, DRIVE_KEY_ACCOUNT)) throw BackupKeyProblem.NotSaved
    driveProblem = null
    driveState = DriveState.on
    syncWithGoogleDrive()
}

/** After a change: wait a moment so a burst of edits goes up in one backup. */
fun AppModel.scheduleDriveSync() {
    if (driveState != DriveState.on || store == null || context == null) return
    driveSyncJob?.cancel()
    driveSyncJob = scope.launch {
        delay(3_000)
        syncWithGoogleDrive()
    }
}

/** Merges what other devices saved, uploads this device's changes and documents. */
suspend fun AppModel.syncWithGoogleDrive() {
    val store = store ?: return
    val context = context ?: return
    if (driveState != DriveState.on || isSyncingWithDrive) return
    val (key, keyID) = storedDriveKey() ?: return
    isSyncingWithDrive = true
    val before = copyOf(data)
    val generation = driveGeneration
    val run = scope.async(Dispatchers.IO) {
        val files = drive()
        DriveSync.run(store, files, key, keyID, DocumentFolder.inside(ExamFiles.folder(context)))
    }
    driveRunJob = run
    val result = runCatching { run.await() }
    driveRunJob = null
    isSyncingWithDrive = false
    // The merge may be on disk even when a later step failed: this device follows it
    // (forgotten secrets, removed PDFs, reminders) either way.
    applyMerged(before)
    if (generation != driveGeneration) return
    val error = result.exceptionOrNull()
    when {
        error == null -> {
            lastDriveSync = currentDate
            drivePrefs()?.edit()?.putLong(DRIVE_LAST_SYNC_KEY, currentDate.epochSecond)?.apply()
            driveProblem = null
        }
        error is DriveProblem.KeyChanged -> {
            // Deleted, or restarted with a new password on another device: never write
            // under the old key again. The password opens the new backup.
            secrets().delete(DRIVE_KEY_ACCOUNT)
            driveState = DriveState.needsPassword
            driveProblem = L("Das Backup wurde auf einem anderen Gerät gelöscht oder neu angelegt. Gib das Backup-Passwort ein.")
        }
        error is DriveProblem.NewerVersion ->
            driveProblem = L("Ein anderes Gerät nutzt eine neuere Meldo-Version. Aktualisiere Meldo, damit es weiter sichert.")
        error is GoogleFailure.Expired -> driveProblem = L("Deine Google-Anmeldung ist abgelaufen. Melde dich erneut an.")
        error is CancellationException -> Unit
        else -> driveProblem = L("Google Drive ist gerade nicht erreichbar. Meldo versucht es später noch einmal.")
    }
}

/**
 * Forgets the Google sign-in and the backup key on this device. The backup in
 * Drive stays, for other devices and a later restore.
 */
fun AppModel.disconnectGoogle() {
    driveGeneration += 1
    driveSyncJob?.cancel()
    driveRunJob?.cancel()
    driveAccess?.access?.let { token -> scope.launch { GoogleClient().revoke(token) } }
    secrets().delete(DRIVE_TOKEN_ACCOUNT)
    secrets().delete(DRIVE_KEY_ACCOUNT)
    driveAccess = null
    driveProblem = null
    lastDriveSync = null
    driveAccount = null
    drivePrefs()?.edit()?.clear()?.apply()
    driveState = DriveState.off
}

/**
 * Deletes Meldo's backup from Google Drive (all devices lose it), then disconnects.
 * Also the way out of a forgotten backup password.
 */
suspend fun AppModel.deleteDriveBackup() {
    // Stop this device's own syncs first, so none recreates a file after the sweep.
    driveSyncJob?.cancel()
    driveRunJob?.let { job ->
        job.cancel()
        runCatching { job.await() }
    }
    secrets().delete(DRIVE_KEY_ACCOUNT)
    driveState = DriveState.needsPassword
    val files = drive()
    // The key file last: if this fails halfway, the backup still has its key.
    val all = files.list().sortedBy { if (it.name == BackupKey.FILE_NAME) 1 else 0 }
    for (file in all) files.delete(file.id)
    disconnectGoogle()
}

/** Drive with a fresh access token; Google Play services renews it silently once granted. */
private suspend fun AppModel.drive(): GoogleDriveFiles {
    driveAccess?.let { if (it.expires > Instant.now()) return GoogleDriveFiles(it.access) }
    secrets().read(DRIVE_TOKEN_ACCOUNT) ?: throw GoogleFailure.Expired
    val app = context ?: throw GoogleFailure.Expired
    val generation = driveGeneration
    val account = driveAccount
    val result = PlayAuthorization.authorize(app, account)
    // Access withdrawn in the Google account: the student has to sign in again.
    val access = result.accessToken?.takeUnless { result.hasResolution() } ?: throw GoogleFailure.Expired
    // Only the account the student connected: a token for another Google account on this device
    // would point the sync at that account's Drive (and look like a changed backup).
    if (account != null) {
        val email = GoogleClient().accountEmail(access) ?: throw GoogleFailure.Network
        if (!email.equals(account, ignoreCase = true)) throw GoogleFailure.Expired
    }
    // "Trennen" while Google answered: don't keep the new token.
    if (generation != driveGeneration) throw GoogleFailure.Cancelled
    driveAccess = GoogleClient.Tokens(access, "", Instant.now().plusSeconds(50 * 60))
    return GoogleDriveFiles(access)
}

/**
 * After a sync merged other devices' changes into the store: loads them and lets
 * this device follow what happened on its other devices.
 */
internal fun AppModel.applyMerged(before: AppData) {
    reloadFromDisk()
    val context = context
    // Exams deleted on another device: their PDFs go here too (unless another exam uses the file).
    val kept = data.exams.map { it.fileName }.toSet()
    val ids = data.exams.map { it.id }.toSet()
    if (context != null) {
        for (exam in before.exams) {
            if (exam.id in ids || exam.fileName in kept) continue
            ExamFiles.locate(context, exam.fileName)?.delete()
            scope.launch(Dispatchers.IO) { ScanMirror.remove(context, exam.id) }
        }
        // Documents that arrived from another device also show in the Files app.
        val earlierExams = before.exams.map { it.id }.toSet()
        val arrived = data.exams.filter { it.id !in earlierExams }.map { it.id to it.fileName }
        if (arrived.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                for ((id, name) in arrived) {
                    val file = ExamFiles.locate(context, name) ?: continue
                    if (file.exists()) ScanMirror.ensure(context, id, name, file)
                }
            }
        }
    }
    // Disconnected on another device: this device's secrets go too.
    val oldUntis = before.untis
    val newUntis = data.untis
    if (oldUntis != null && (newUntis == null || keychainAccount(newUntis) != keychainAccount(oldUntis))) {
        // Another account or server now: the old account's secret goes, the login stays.
        secrets().delete(keychainAccount(oldUntis))
    }
    if (before.teams != null && data.teams == null) secrets().delete(TEAMS_ACCOUNT)
    if (context != null) {
        // Reminders follow tasks deleted or changed on another device.
        val earlier = before.tasks.map { it.id }.toSet()
        for (task in before.tasks) {
            if (task in data.tasks) continue
            val now = data.tasks.firstOrNull { it.id == task.id }
            if (now != null) Reminders.schedule(context, now, subject(now.subjectID)?.name) else Reminders.cancel(context, task.id)
        }
        for (task in data.tasks) {
            if (task.id !in earlier && task.reminderAt != null) Reminders.schedule(context, task, subject(task.subjectID)?.name)
        }
        Surfaces.reload(context)
    }
    refreshSchedules()
}

// region Onboarding

/**
 * Who sees the first-run tour. Only new students: an existing student who updates
 * already has subjects or a WebUntis connection and never gets it, even though their
 * file predates the "seen" flag.
 */
object Onboarding {
    fun shouldShow(seen: Boolean, hasSubjects: Boolean, hasUntis: Boolean): Boolean = !seen && !hasSubjects && !hasUntis
}

/** Finished or skipped (including closing it): never shown again on its own. */
fun AppModel.finishOnboarding() {
    showingOnboarding = false
    if (data.preferences.onboardingSeen) return
    mutate { it.preferences.onboardingSeen = true }
}

/** From "Einführung ansehen" in Mehr and Über Meldo. */
fun AppModel.showOnboardingAgain() {
    showingOnboarding = true
}

// endregion
