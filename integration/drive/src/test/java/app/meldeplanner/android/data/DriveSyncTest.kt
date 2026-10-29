package app.meldeplanner.android.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Google Drive backup: the Android code must read and write exactly what the iOS app
 * does, so an iPhone and an Android phone share one backup.
 *
 * The iOS vectors below were produced on macOS by running the iOS algorithms
 * (`BackupKey.derive` with CommonCrypto's CCKeyDerivationPBKDF, CryptoKit's
 * `AES.GCM.seal(_:using:nonce:authenticating:)`, `JSONEncoder` with `.sortedKeys` +
 * SHA256 for `DriveSync.keyID`) as a Swift script with these fixed inputs:
 * password "Mee" + U+0301 + "ldo-Backup1" (a decomposed é, so NFC matters), salt 00…0f,
 * 600 000 rounds, data key 20…3f, nonce a0…ab.
 */
class DriveSyncTest {
    private val password = "Meéldo-Backup1"
    private val salt = ByteArray(16) { it.toByte() }
    private val dataKey = ByteArray(32) { (0x20 + it).toByte() }
    private val nonce = ByteArray(12) { (0xa0 + it).toByte() }

    private val iosKek = "ed44a9f71033545b1200c661530785476175843a22de6f29bf348922e99d741a"
    private val iosWrapped =
        "a0a1a2a3a4a5a6a7a8a9aaab1b90585508de70197036dc7f0f5174ff7cb4a70a30f3c2495713486b64d2d35df44a2e1852cf6a482897b0b86ef8403f"

    /** `JSONEncoder().encode(key)` on iOS, as stored in Drive's meldo-key. */
    private val iosKeyFile =
        """{"rounds":600000,"version":1,"wrapped":"oKGio6SlpqeoqaqrG5BYVQjecBlwNtx\/D1F0\/3y0pwow88JJVxNIa2TS0130Si4YUs9qSCiXsLhu+EA\/","salt":"AAECAwQFBgcICQoLDA0ODw=="}"""
    private val iosKeyID = "fd4902a471bbbf4cc90410302983220d28f1dbecac52353b05aa0c4fc72e2ae3"

    /** `{"schemaVersion":2}` sealed by CryptoKit under the data key for "drive/meldo-data". */
    private val iosDataBox =
        "a0a1a2a3a4a5a6a7a8a9aaab051ed757acb2edcbf74ddfc94f321884ab4dbc432617cef4ad535c3d12e115e12dbb24"

    @Test
    fun keyDerivationMatchesCommonCrypto() {
        assertEquals(iosKek, hex(BackupKey.derive(password, salt, 600_000)))
        // NFC: the precomposed spelling gives the same key.
        assertEquals(iosKek, hex(BackupKey.derive("Me\u00e9ldo-Backup1", salt, 600_000)))
    }

    @Test
    fun sealingMatchesCryptoKit() {
        val wrapped = SyncBox.seal(dataKey, unhex(iosKek), "MeldoBackupKey/v1".toByteArray(), nonce)
        assertEquals(iosWrapped, hex(wrapped))
        val box = SyncBox.seal("""{"schemaVersion":2}""".toByteArray(), dataKey, DriveSync.context("meldo-data"), nonce)
        assertEquals(iosDataBox, hex(box))
    }

    @Test
    fun opensWhatTheIosAppWrote() {
        val key = DriveSync.decodeKey(iosKeyFile.toByteArray()) ?: error("key file not read")
        assertEquals(iosKeyID, DriveSync.keyID(key))
        assertArrayEquals(dataKey, key.unlock(password))
        val plain = SyncBox.open(unhex(iosDataBox), dataKey, DriveSync.context("meldo-data"))
        assertEquals("""{"schemaVersion":2}""", String(plain))
    }

    @Test
    fun keyIDEscapesSlashesLikeSwift() {
        // Swift's JSONEncoder writes "/" as "\/"; the ID is the SHA-256 of those bytes.
        val key = BackupKey(
            salt = Base64.getEncoder().encodeToString(byteArrayOf(-1, -1, -1, -5, -1, -65) + ByteArray(10)),
            rounds = 600_000,
            wrapped = Base64.getEncoder().encodeToString(byteArrayOf(-4, 0x3f)),
        )
        assertEquals("37a50254597669e2d0a71a0d3b0b6461f0ed11f65504c635d8695b7e87fed6b5", DriveSync.keyID(key))
        assertTrue(String(DriveSync.encodeKey(key)).contains("\\/"))
        assertEquals(key, DriveSync.decodeKey(DriveSync.encodeKey(key)))
    }

    @Test
    fun createdKeyRoundTrips() {
        val (file, key) = BackupKey.create("richtig-gut")
        assertEquals(32, key.size)
        assertEquals(BackupKey.DEFAULT_ROUNDS, file.rounds)
        val stored = DriveSync.decodeKey(DriveSync.encodeKey(file)) ?: error("not read back")
        assertArrayEquals(key, stored.unlock("richtig-gut"))
        assertEquals(DriveSync.keyID(file), DriveSync.keyID(stored))
        try {
            stored.unlock("falsch-falsch")
            fail("wrong password accepted")
        } catch (e: BackupKeyProblem.WrongPassword) {
            // expected
        }
        val box = SyncBox.seal("Mathe".toByteArray(), key, DriveSync.context("meldo-data"))
        assertEquals("Mathe", String(SyncBox.open(box, key, DriveSync.context("meldo-data"))))
        // The context binds a box to its file name.
        assertTrue(runCatching { SyncBox.open(box, key, DriveSync.context("meldo-key")) }.isFailure)
    }

    @Test
    fun refusesShortPasswordsAndUnsafeKeyFiles() {
        assertTrue(runCatching { BackupKey.create("kurz") }.exceptionOrNull() is BackupKeyProblem.TooShort)
        val weak = DriveSync.decodeKey(iosKeyFile.toByteArray())!!.copy(rounds = 1_000)
        assertTrue(runCatching { weak.unlock(password) }.exceptionOrNull() is BackupKeyProblem.Damaged)
        val slow = weak.copy(rounds = 3_000_001)
        assertTrue(runCatching { slow.unlock(password) }.exceptionOrNull() is BackupKeyProblem.Damaged)
    }

    // region Sync between two devices

    private class Device(val store: LocalStore, val scans: File) {
        val documents = DocumentFolder.inside(scans)
    }

    private fun device(): Device {
        val dir = Files.createTempDirectory("meldo-device").toFile()
        return Device(LocalStore(File(dir, "data.json")), File(dir, "Scans").apply { mkdirs() })
    }

    @Test
    fun twoDevicesShareSubjectsDeletionsAndDocuments() = runBlocking {
        val drive = MemoryDrive()
        val (file, key) = BackupKey.create("gemeinsam-1")
        val stored = DriveSync.storeKeyFile(file, drive)
        val keyID = DriveSync.keyID(stored)
        val phone = device()
        val tablet = device()

        val math = Subject(name = "Mathematik")
        phone.store.update { it.subjects.add(math) }
        val exam = ExamDocument(title = "1. Klausur", date = Instant.parse("2026-10-12T08:00:00Z"), fileName = "Mathematik/Exams/1.pdf", pageCount = 1)
        File(phone.scans, exam.fileName).apply { parentFile?.mkdirs() }.writeBytes("%PDF-1.7 test".toByteArray())
        phone.store.update { it.exams.add(exam) }
        DriveSync.run(phone.store, drive, key, keyID, phone.documents)
        assertEquals(1, drive.named(DriveSync.fileName(exam.id)).size)

        DriveSync.run(tablet.store, drive, key, keyID, tablet.documents)
        assertEquals(listOf("Mathematik"), tablet.store.load().subjects.map { it.name })
        assertEquals("%PDF-1.7 test", File(tablet.scans, exam.fileName).readText())

        // Deleted on the tablet: gone on the phone after its next sync.
        tablet.store.update { d ->
            d.subjects.removeAll { it.id == math.id }
            d.exams.removeAll { it.id == exam.id }
        }
        DriveSync.run(tablet.store, drive, key, keyID, tablet.documents)
        assertTrue(drive.named(DriveSync.fileName(exam.id)).isEmpty())
        DriveSync.run(phone.store, drive, key, keyID, phone.documents)
        assertTrue(phone.store.load().subjects.isEmpty())
        assertTrue(phone.store.load().exams.isEmpty())
    }

    /** A document this device can't store under its name (too long here) is skipped; the rest still syncs. */
    @Test
    fun aDocumentThatCantBeStoredDoesNotStopTheOthers() = runBlocking {
        val drive = MemoryDrive()
        val (file, key) = BackupKey.create("gemeinsam-1")
        val keyID = DriveSync.keyID(DriveSync.storeKeyFile(file, drive))
        val phone = device()
        val tablet = device()
        val date = Instant.parse("2026-10-12T08:00:00Z")
        // 250 bytes fit on the phone, but not with the tablet's ".download" suffix.
        val long = ExamDocument(title = "lang", date = date, fileName = "Other/Exams/" + "a".repeat(246) + ".pdf", pageCount = 1)
        val ok = ExamDocument(title = "ok", date = date, fileName = "Other/Exams/ok.pdf", pageCount = 1)
        for (exam in listOf(long, ok)) {
            File(phone.scans, exam.fileName).apply { parentFile?.mkdirs() }.writeBytes("%PDF ${exam.title}".toByteArray())
            phone.store.update { it.exams.add(exam) }
        }
        DriveSync.run(phone.store, drive, key, keyID, phone.documents)

        DriveSync.run(tablet.store, drive, key, keyID, tablet.documents)
        assertEquals("%PDF ok", File(tablet.scans, ok.fileName).readText())
    }

    @Test
    fun aReplacedKeyStopsTheSync() = runBlocking {
        val drive = MemoryDrive()
        val (file, key) = BackupKey.create("erstes-passwort")
        val keyID = DriveSync.keyID(DriveSync.storeKeyFile(file, drive))
        val phone = device()
        DriveSync.run(phone.store, drive, key, keyID, phone.documents)
        // Another device deleted the backup and started a new one.
        drive.clear()
        DriveSync.storeKeyFile(BackupKey.create("zweites-passwort").first, drive)
        try {
            DriveSync.run(phone.store, drive, key, keyID, phone.documents)
            fail("synced under a replaced key")
        } catch (e: DriveProblem.KeyChanged) {
            // expected
        }
        assertTrue(drive.named(DriveSync.DATA_NAME).isEmpty())
    }

    @Test
    fun aPlantedDataFileIsOverwrittenNotTrusted() = runBlocking {
        val drive = MemoryDrive()
        val (file, key) = BackupKey.create("passwort-123")
        val keyID = DriveSync.keyID(DriveSync.storeKeyFile(file, drive))
        drive.create(DriveSync.DATA_NAME, "not sealed".toByteArray())
        val phone = device()
        phone.store.update { it.subjects.add(Subject(name = "Deutsch")) }
        DriveSync.run(phone.store, drive, key, keyID, phone.documents)
        val sealed = drive.named(DriveSync.DATA_NAME).single()
        val plain = String(SyncBox.open(sealed, key, DriveSync.context(DriveSync.DATA_NAME)))
        assertTrue(plain.contains("Deutsch"))
    }

    // endregion

    // region Merge

    @Test
    fun newerEditWinsAndDeletionBeatsOlderEdit() {
        val subject = Subject(name = "Bio")
        val old = AppData()
        val mine = AppData().apply { subjects.add(subject.copy()) }
        SyncMerge.record(old, mine, Instant.parse("2026-10-01T10:00:00Z"))
        val theirs = AppData().apply { subjects.add(subject.copy(name = "Biologie")) }
        SyncMerge.record(old, theirs, Instant.parse("2026-10-01T11:00:00Z"))
        assertEquals("Biologie", SyncMerge.merge(mine, theirs).subjects.single().name)
        assertEquals("Biologie", SyncMerge.merge(theirs, mine).subjects.single().name)

        // Deleted later on one device: the deletion beats the older edit on the other.
        val before = SyncMerge.merge(theirs, mine)
        val after = copy(before).apply { subjects.clear() }
        SyncMerge.record(before, after, Instant.parse("2026-10-01T12:00:00Z"))
        assertEquals(setOf("subjects:${subject.id}"), after.sync.tombstones.keys)
        assertTrue(SyncMerge.merge(theirs, after).subjects.isEmpty())
        assertTrue(SyncMerge.merge(after, theirs).subjects.isEmpty())
    }

    /** A device that has seen a stamp ahead of its clock still stamps its next edit later, after saving. */
    @Test
    fun anEditAfterAStampFromTheFutureStillWinsOnceSaved() {
        val subject = Subject(name = "Bio")
        val ahead = Instant.parse("2027-01-01T00:00:00Z")
        val base = AppData().apply { subjects.add(subject.copy()) }
        SyncMerge.record(AppData(), base, ahead)
        val saved = copy(base)
        val edited = copy(saved).apply { subjects.single().name = "Biologie" }
        SyncMerge.record(saved, edited, Instant.parse("2026-10-01T10:00:00Z"))
        val stored = copy(edited)
        assertTrue(stored.sync.stamps.getValue("subjects:${subject.id}") > ahead)
        assertEquals("Biologie", SyncMerge.merge(saved, stored).subjects.single().name)
    }

    private fun copy(data: AppData) =
        MeldoJson.decodeFromString(AppData.serializer(), MeldoJson.encodeToString(AppData.serializer(), data))

    @Test
    fun deviceSettingsStayLocal() {
        val mine = AppData().apply { preferences.appIcon = AppIconChoice.night; preferences.dynamicColor = true }
        val theirs = AppData().apply { preferences.weeklyGoal = 9 }
        SyncMerge.record(AppData(), theirs, Instant.parse("2026-10-01T10:00:00Z"))
        val merged = SyncMerge.merge(mine, theirs)
        assertEquals(9, merged.preferences.weeklyGoal)
        assertEquals(AppIconChoice.night, merged.preferences.appIcon)
        assertTrue(merged.preferences.dynamicColor)
        assertFalse(merged.sync.stamps.isEmpty())
    }

    /** [base] changed by [change] on one device at second [second] (iOS test helper `edit`). */
    private fun edit(base: AppData, second: Long, change: (AppData) -> Unit): AppData {
        val next = copy(base).apply(change)
        SyncMerge.record(base, next, Instant.ofEpochSecond(1_790_000_000 + second))
        return next
    }

    @Test
    fun settingsChangedOnDifferentDevicesAreBothKept() {
        val base = edit(AppData(), 1) { it.preferences.weeklyGoal = 8 }
        val phone = edit(base, 2) { it.preferences.studentName = "Yavuz"; it.preferences.className = "10b" }
        val tablet = edit(base, 5) { it.preferences.appearance = AppearanceChoice.dark; it.preferences.weeklyGoal = null }
        assertTrue(tablet.sync.tombstones.containsKey("preferences.weeklyGoal"))
        for (merged in listOf(SyncMerge.merge(phone, tablet), SyncMerge.merge(tablet, phone))) {
            // The name entered on the phone survives a later change on the tablet.
            assertEquals("Yavuz", merged.preferences.studentName)
            assertEquals("10b", merged.preferences.className)
            assertEquals(AppearanceChoice.dark, merged.preferences.appearance)
            // Clearing a setting syncs too.
            assertEquals(null, merged.preferences.weeklyGoal)
        }
    }

    @Test
    fun oldWholePreferencesStampStillCounts() {
        // A backup from before per-setting items: one "preferences" stamp for the whole block.
        val remote = AppData().apply {
            preferences.studentName = "Alt"
            sync.stamps["preferences"] = Instant.parse("2026-10-02T10:00:00Z")
        }
        val local = edit(AppData(), 0) { it.preferences.className = "9a" } // 2026-09 stamp, older
        val merged = SyncMerge.merge(local, remote)
        assertEquals("Alt", merged.preferences.studentName)
    }

    // endregion

    @Test
    fun leftoverCopiesGoAfterThirtyDays() {
        val dir = Files.createTempDirectory("meldo-leftovers").toFile()
        val now = Instant.parse("2026-10-27T12:00:00Z")
        val old = File(dir, "data-unreadable-${now.epochSecond - 31L * 24 * 3600}.json").apply { writeText("{") }
        val recent = File(dir, "data-unreadable-${now.epochSecond - 3600}.json").apply { writeText("{") }
        val data = File(dir, "data.json").apply { writeText("{}") }
        LocalStore.removeLeftovers(dir, now)
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(data.exists())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** Stand-in for the Google Drive app-data folder, kept in memory (tests only). */
private class MemoryDrive : DriveFiles {
    private data class Stored(val name: String, val data: ByteArray, val created: Instant)

    private val files = linkedMapOf<String, Stored>()
    private var clock = Instant.parse("2027-01-15T08:00:00Z")

    override suspend fun list(): List<DriveFile> = files.map { DriveFile(it.key, it.value.name, it.value.created) }

    override suspend fun read(id: String, limit: Int): ByteArray {
        val file = files[id] ?: throw GoogleFailure.Network
        if (file.data.size > limit) throw DriveProblem.TooLarge
        return file.data
    }

    override suspend fun create(name: String, data: ByteArray): DriveFile {
        clock = clock.plusSeconds(1)
        val id = UUID.randomUUID().toString().replace("-", "")
        files[id] = Stored(name, data, clock)
        return DriveFile(id, name, clock)
    }

    override suspend fun update(id: String, data: ByteArray) {
        val file = files[id] ?: throw GoogleFailure.Network
        files[id] = file.copy(data = data)
    }

    override suspend fun delete(id: String) {
        files.remove(id)
    }

    fun named(name: String): List<ByteArray> = files.values.filter { it.name == name }.map { it.data }

    fun clear() = files.clear()
}
