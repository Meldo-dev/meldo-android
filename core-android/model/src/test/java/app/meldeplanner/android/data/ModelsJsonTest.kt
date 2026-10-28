package app.meldeplanner.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

/** data.json written by the iOS app must load unchanged on Android, and back. */
class ModelsJsonTest {
    // Shape of a file from the iOS app (JSONEncoder, .iso8601, .sortedKeys), trimmed.
    private val iosFile = """
        {"exams":[],"grades":[],"holidays":[],"lessons":[{"endMinute":525,"id":"6F1C2E5A-1111-4C3B-9D2A-0A1B2C3D4E5F",
        "parity":"every","room":"A12","startMinute":480,"subjectID":"0B7E9D3C-2222-4F11-8E8E-112233445566",
        "updatedAt":"2026-10-20T07:00:00Z","weekday":0}],"participation":[{"id":"AAAAAAAA-3333-4444-5555-666666666666",
        "lessonID":"6F1C2E5A-1111-4C3B-9D2A-0A1B2C3D4E5F","recordedAt":"2026-10-20T07:10:00Z",
        "subjectID":"0B7E9D3C-2222-4F11-8E8E-112233445566"}],"preferences":{"adultStudent":false,"appIcon":"orange",
        "appearance":"dark","askAfterLesson":true,"checkInPromptDismissed":true,"className":"10b","excuseSalutation":"",
        "gradeScale":"points","haptics":true,"hiddenCourses":[],"homeLayout":[{"kind":"today","size":"large"},
        {"kind":"weekPreview","size":"medium"}],"language":"tr","liveActivities":true,"showMascot":false,
        "statsLayout":[{"kind":"trend","size":"large"}],"studentName":"","tendencyStep":0.25,"theme":"indigo",
        "weeklyGoal":12,"writtenWeight":0.6},"schemaVersion":2,"subjects":[{"color":"teal","id":"0B7E9D3C-2222-4F11-8E8E-112233445566",
        "name":"Mathematik","shortName":"M","symbolName":"function","teacher":"Frau Kaya","updatedAt":"2026-10-01T12:00:00Z"}],
        "tasks":[{"id":"BBBBBBBB-1111-2222-3333-444444444444","kind":"homework","notes":"","priority":"high",
        "recurrence":"weekly","title":"Arbeitsblatt","dueDate":"2026-10-21T22:00:00Z","updatedAt":"2026-10-20T15:00:00Z"}]}
    """.trimIndent()

    @Test
    fun readsTheIosFormat() {
        val data = MeldoJson.decodeFromString(AppData.serializer(), iosFile)
        assertEquals("Mathematik", data.subjects.single().name)
        assertEquals(SubjectColor.teal, data.subjects.single().color)
        assertEquals(480, data.lessons.single().startMinute)
        assertEquals(Instant.parse("2026-10-20T07:10:00Z"), data.participation.single().recordedAt)
        // Turkish was removed: a saved "tr" falls back to German.
        assertEquals(AppLanguage.german, data.preferences.language)
        assertEquals(AppTheme.indigo, data.preferences.theme)
        assertEquals(DashboardCard.weekPreview, data.preferences.homeLayout[1].kind)
        assertEquals(GradeScaleChoice.points, data.preferences.gradeScale)
        assertEquals(TaskRecurrence.weekly, data.tasks.single().recurrence)
        // Fields the iOS file doesn't have take their defaults.
        assertTrue(data.changes.isEmpty())
        assertNull(data.untis)
        assertEquals(false, data.preferences.dynamicColor)
    }

    @Test
    fun writesWholeSecondsAndRoundTrips() {
        val data = AppData()
        data.participation.add(ParticipationEntry(recordedAt = Instant.parse("2026-10-20T07:10:00.123456Z")))
        val json = MeldoJson.encodeToString(AppData.serializer(), data)
        assertTrue(json.contains("\"recordedAt\":\"2026-10-20T07:10:00Z\""))
        val back = MeldoJson.decodeFromString(AppData.serializer(), json)
        assertEquals(Instant.parse("2026-10-20T07:10:00Z"), back.participation.single().recordedAt)
    }

    @Test
    fun keepsAnUnreadableFileAndStartsFresh() {
        val dir = Files.createTempDirectory("meldo").toFile()
        val file = File(dir, "data.json").apply { writeText("{not json") }
        val store = LocalStore(file)
        val saved = store.update { it.subjects.add(Subject(name = "Deutsch")) }
        assertEquals("Deutsch", saved.subjects.single().name)
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("data-unreadable-") })
    }

    @Test
    fun refusesNewerSchema() {
        val dir = Files.createTempDirectory("meldo").toFile()
        val file = File(dir, "data.json").apply { writeText("""{"schemaVersion":99}""") }
        val result = runCatching { LocalStore(file).update { } }
        assertTrue(result.exceptionOrNull() is LocalStore.LoadException.NewerSchema)
        assertEquals("""{"schemaVersion":99}""", file.readText())
    }
}
