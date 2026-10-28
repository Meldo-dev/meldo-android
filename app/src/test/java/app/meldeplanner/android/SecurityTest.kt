package app.meldeplanner.android

import app.meldeplanner.android.data.BackupKey
import app.meldeplanner.android.data.DocumentFolder
import app.meldeplanner.android.data.DriveFile
import app.meldeplanner.android.data.DriveFiles
import app.meldeplanner.android.data.DriveProblem
import app.meldeplanner.android.data.DriveSync
import app.meldeplanner.android.data.ExamDocument
import app.meldeplanner.android.data.GoogleFailure
import app.meldeplanner.android.data.GoogleHttp
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.LocalStore
import app.meldeplanner.android.data.MeldoJson
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.SyncBox
import app.meldeplanner.android.data.UntisFailure
import app.meldeplanner.android.data.UntisLink
import app.meldeplanner.android.model.untisRetryDelay
import app.meldeplanner.android.model.untisServerIsListed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import uniffi.melde_ffi.FfiUntisSchool
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression tests for security fixes. Each one feeds the app what a
 * lower-trust party could send and checks that the fix holds. Dummy data only.
 */
class SecurityTest {
    // region Synced WebUntis server (lead 2)

    private val link = UntisLink(server = "", loginName = "BK-Ohm", schoolName = "Berufskolleg", user = "", browserLogin = true)

    @Test
    fun aWebUntisHostOpensWithoutAsking() = runBlocking {
        var searched = false
        assertTrue(untisServerIsListed(link.copy(server = "nessa.webuntis.com")) { searched = true; emptyList() })
        assertFalse(searched)
    }

    @Test
    fun aSyncedForeignServerIsRefused() = runBlocking {
        val search: suspend (String) -> List<FfiUntisSchool> = {
            listOf(FfiUntisSchool(server = "untis.bk-ohm.de", loginName = "BK-Ohm", displayName = "Berufskolleg", address = ""))
        }
        // Planted by a device or backup writer: not what WebUntis lists for this school.
        assertFalse(untisServerIsListed(link.copy(server = "login.example.test"), search))
        // Lookalike names don't count as webuntis.com.
        assertFalse(untisServerIsListed(link.copy(server = "webuntis.com.example.test"), search))
        assertFalse(untisServerIsListed(link.copy(server = "evilwebuntis.com"), search))
        // A self-hosted school the search lists still works.
        assertTrue(untisServerIsListed(link.copy(server = "UNTIS.bk-ohm.de"), search))
    }

    // endregion

    // region Refresh backoff (lead 4)

    @Test
    fun aWrongPasswordStopsAutomaticRetries() {
        assertNull(untisRetryDelay(UntisFailure.WrongCredentials, 1))
    }

    @Test
    fun failuresBackOffAndStayBounded() {
        assertEquals(Duration.ofMinutes(30), untisRetryDelay(UntisFailure.SessionExpired, 1))
        assertEquals(Duration.ofMinutes(30), untisRetryDelay(UntisFailure.NoLessons, 1))
        val delays = (1..10).map { untisRetryDelay(UntisFailure.Network, it)!! }
        assertEquals(Duration.ofMinutes(1), delays.first())
        assertTrue(delays.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(delays.all { it <= Duration.ofMinutes(30) })
    }

    // endregion

    // region Drive files someone else wrote (lead 6)

    @Test
    fun aFileTooBigForMemoryIsRemovedNotRetried() = runBlocking {
        val drive = FakeDrive()
        val (file, key) = BackupKey.create("passwort-123")
        val keyID = DriveSync.keyID(DriveSync.storeKeyFile(file, drive))
        val dir = Files.createTempDirectory("meldo-security").toFile()
        val store = LocalStore(File(dir, "data.json"))
        val scans = File(dir, "Scans").apply { mkdirs() }
        val exam = ExamDocument(title = "Klausur", date = Instant.parse("2026-10-12T08:00:00Z"), fileName = "Mathe/1.pdf", pageCount = 1)
        store.update { it.exams.add(exam) }
        // The exam's PDF in Drive was replaced by something this device can't hold in memory.
        val planted = drive.create(DriveSync.fileName(exam.id), ByteArray(16))
        drive.outOfMemory = setOf(planted.id)
        DriveSync.run(store, drive, key, keyID, DocumentFolder.inside(scans))
        assertTrue(drive.list().none { it.id == planted.id })
        assertFalse(File(scans, exam.fileName).exists())
    }

    // endregion

    // region Google redirects

    @Test
    fun theTokenNeverFollowsARedirectToAnotherHost() {
        val api = URL("https://www.googleapis.com/drive/v3/files/abc?alt=media")
        val headers = mapOf("Authorization" to "Bearer t")
        assertEquals(headers, GoogleHttp.follow(api, URL("https://www.googleapis.com/other"), "GET", headers))
        val content = GoogleHttp.follow(api, URL("https://doc-0.googleusercontent.com/x"), "GET", headers)
        assertEquals(emptyMap<String, String>(), content)
        assertNull(GoogleHttp.follow(api, URL("https://evil.example/x"), "GET", headers))
        assertNull(GoogleHttp.follow(api, URL("http://www.googleapis.com/x"), "GET", headers))
        assertNull(GoogleHttp.follow(api, URL("https://doc-0.googleusercontent.com/x"), "POST", headers))
        assertNull(GoogleHttp.follow(api, URL("https://www.googleapis.com:8443/x"), "GET", headers))
    }

    // endregion

    // region What other apps can reach (manifest)

    private val android = "http://schemas.android.com/apk/res/android"

    private fun application(): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        return doc.getElementsByTagName("application").item(0) as Element
    }

    private fun components(app: Element): List<Element> =
        (0 until app.childNodes.length).mapNotNull { app.childNodes.item(it) as? Element }

    @Test
    fun onlyTheExpectedComponentsAreExported() {
        val exported = components(application())
            .filter { it.getAttributeNS(android, "exported") == "true" }
            .associate { it.getAttributeNS(android, "name") to it.getAttributeNS(android, "permission") }
        val expected = setOf(
            ".MainActivity", ".LauncherStandard", ".LauncherOrange", ".LauncherNight",
            ".ui.features.untis.TeamsAuthActivity", ".surfaces.MeldenTileService",
            ".surfaces.widgets.MeldoTodayWidgetReceiver", ".surfaces.widgets.MeldoStatsWidgetReceiver",
            ".surfaces.widgets.TodayScheduleWidgetReceiver", ".surfaces.widgets.HomeworkWidgetReceiver",
            ".surfaces.widgets.HolidayCountdownWidgetReceiver",
        )
        assertEquals(expected, exported.keys)
        // Only the system may bind the Quick Settings tile.
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", exported[".surfaces.MeldenTileService"])
    }

    @Test
    fun filesAndBackupsStayPrivate() {
        val app = application()
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
        val provider = components(app).single { it.getAttributeNS(android, "name") == "androidx.core.content.FileProvider" }
        assertEquals("false", provider.getAttributeNS(android, "exported"))
        assertEquals("true", provider.getAttributeNS(android, "grantUriPermissions"))
        val rules = File("src/main/res/xml/securestore_backup_rules.xml").readText()
        assertTrue("SecureStore excluded", rules.contains("securestore"))
        assertTrue("WebView cookies excluded", rules.contains("app_webview"))
    }

    // endregion

    // region Sealed-but-undecodable sync data

    @Test
    fun aSealedButUndecodableFileIsOverwrittenNotTreatedAsNewer() = runBlocking {
        val drive = FakeDrive()
        val (file, key) = BackupKey.create("passwort-123")
        val keyID = DriveSync.keyID(DriveSync.storeKeyFile(file, drive))
        val dir = Files.createTempDirectory("meldo-wedge").toFile()
        val store = LocalStore(File(dir, "data.json"))
        store.update { it.subjects.add(Subject(name = "Mathe")) }
        // A peer uploads bytes that open with the real key but aren't valid AppData JSON.
        val garbage = SyncBox.seal("nicht json".toByteArray(), key, DriveSync.context(DriveSync.DATA_NAME))
        drive.create(DriveSync.DATA_NAME, garbage)
        // Must not throw NewerVersion: the corrupt file is overwritten with this device's data.
        DriveSync.run(store, drive, key, keyID, DocumentFolder.inside(File(dir, "Scans").apply { mkdirs() }))
        val dataFile = drive.list().single { it.name == DriveSync.DATA_NAME }
        val plain = SyncBox.open(drive.read(dataFile.id, 10_000_000), key, DriveSync.context(DriveSync.DATA_NAME))
        val decoded = MeldoJson.decodeFromString(AppData.serializer(), String(plain, Charsets.UTF_8))
        assertTrue(decoded.subjects.any { it.name == "Mathe" })
    }

    // endregion
}

/** Drive folder in memory; [outOfMemory] files fail to read like a file too big for the heap. */
private class FakeDrive : DriveFiles {
    private data class Stored(val name: String, val data: ByteArray, val created: Instant)

    private val files = linkedMapOf<String, Stored>()
    private var clock = Instant.parse("2027-01-15T08:00:00Z")
    var outOfMemory: Set<String> = emptySet()

    override suspend fun list(): List<DriveFile> = files.map { DriveFile(it.key, it.value.name, it.value.created) }

    override suspend fun read(id: String, limit: Int): ByteArray {
        if (id in outOfMemory) throw OutOfMemoryError("test")
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
}
