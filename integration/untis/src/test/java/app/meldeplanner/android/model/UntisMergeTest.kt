package app.meldeplanner.android.model

import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.Holiday
import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.data.ParticipationEntry
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.data.TimetableChangeRecord
import app.meldeplanner.android.data.UntisExtras
import app.meldeplanner.android.data.UntisLink
import app.meldeplanner.android.data.UntisNewsRecord
import app.meldeplanner.android.data.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.melde_ffi.FfiDate
import uniffi.melde_ffi.FfiUntisChange
import uniffi.melde_ffi.FfiUntisChangeKind
import uniffi.melde_ffi.FfiUntisExam
import uniffi.melde_ffi.FfiUntisHoliday
import uniffi.melde_ffi.FfiUntisHomework
import uniffi.melde_ffi.FfiUntisLesson
import uniffi.melde_ffi.FfiUntisSchedule
import uniffi.melde_ffi.FfiUntisSubject
import uniffi.melde_ffi.FfiWeekParity
import uniffi.melde_ffi.FfiWeekday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The WebUntis merge rules (ports of MeldePlannerTests/UntisImportTests.swift).
 * The schedule is written out by hand here instead of derived by the Rust core, so the
 * test runs on the JVM without the native library.
 */
class UntisMergeTest {
    private val calendar = MeldoCalendar(ZoneId.of("Europe/Berlin"))
    private fun day(d: Int): Instant = calendar.instant(LocalDate.of(2026, 10, d))
    private fun ffi(d: Int) = FfiDate(2026, 10u, d.toUByte())
    private val now get() = day(21)

    /** Mathe weekly Monday 08:00 (cancelled on the 26th), Physik / Chemie alternating at 10:00. */
    private val schedule = FfiUntisSchedule(
        subjects = listOf(
            FfiUntisSubject("M", "Mathematik", "M"),
            FfiUntisSubject("PH", "Physik", "PH"),
            FfiUntisSubject("CH", "Chemie", "CH"),
        ),
        lessons = listOf(
            FfiUntisLesson("M", FfiWeekday.MONDAY, 480u, 525u, "A1", FfiWeekParity.EVERY),
            FfiUntisLesson("PH", FfiWeekday.MONDAY, 600u, 645u, null, FfiWeekParity.A),
            FfiUntisLesson("CH", FfiWeekday.MONDAY, 600u, 645u, null, FfiWeekParity.B),
        ),
        changes = listOf(FfiUntisChange("M", ffi(26), 480u, 525u, FfiUntisChangeKind.Cancelled)),
        skippedExtra = 0u,
    )

    private val link = UntisLink(server = "nessa.webuntis.com", loginName = "gym", schoolName = "Gymnasium", user = "max")

    @Test
    fun mergesIntoExistingData() {
        val d = AppData()
        // The student already has Mathematik with a Monday 08:00 lesson and a Meldung in it.
        val math = Subject(name = "Mathematik", color = SubjectColor.red)
        d.subjects.add(math)
        val lesson = Lesson(subjectID = math.id, weekday = 0, startMinute = 480, endMinute = 525)
        d.lessons.add(lesson)
        d.participation.add(ParticipationEntry(subjectID = math.id, lessonID = lesson.id, recordedAt = day(19)))

        val summary = UntisMerge.applySchedule(d, schedule, day(19), link, now, calendar)

        assertEquals(2, summary.newSubjects) // Physik, Chemie; Mathematik is reused.
        assertEquals(3, d.subjects.size)
        assertEquals("M", d.subjects.first { it.id == math.id }.untisKey)
        assertEquals(SubjectColor.red, d.subjects.first { it.id == math.id }.color) // keeps the student's color
        // The existing lesson keeps its id (so Meldungen stay linked) and is now from Untis.
        assertEquals(true, d.lessons.first { it.id == lesson.id }.fromUntis)
        assertEquals(lesson.id, d.participation.single().lessonID)
        assertEquals(3, d.lessons.size)
        assertEquals(3, summary.lessons)
        // Alternating lessons switch on A/B weeks with the imported week as A.
        assertEquals(day(19), d.preferences.weekAReference)
        assertTrue(d.lessons.any { it.parity == WeekParity.a } && d.lessons.any { it.parity == WeekParity.b })
        // The cancellation on the 26th points at the student's lesson.
        val change = d.changes.single()
        assertEquals(lesson.id, change.lessonID)
        assertEquals(day(26), change.date)
        assertEquals(TimetableChangeRecord.Kind.cancelled, change.kind)
        assertEquals("max", d.untis?.user)
        assertEquals("atom", d.subjects.first { it.untisKey == "PH" }.symbolName)
    }

    @Test
    fun matchesSubjectsIgnoringCaseAndDiacritics() {
        val d = AppData()
        val french = Subject(name = "FRANZÖSISCH")
        d.subjects.add(french)
        val s = FfiUntisSchedule(
            subjects = listOf(FfiUntisSubject("F", "Franzosisch", "F")),
            lessons = emptyList(), changes = emptyList(), skippedExtra = 0u,
        )
        assertEquals(0, UntisMerge.applySchedule(d, s, day(19), link, now, calendar).newSubjects)
        assertEquals("F", french.untisKey)
    }

    @Test
    fun reimportReplacesUntisChangesAndDisconnectKeepsLessons() {
        val d = AppData()
        UntisMerge.applySchedule(d, schedule, day(19), link, now, calendar)
        val again = UntisMerge.applySchedule(d, schedule, day(19), link, now, calendar)
        assertEquals(0, again.newSubjects)
        assertEquals(1, d.changes.size)
        assertEquals(3, d.subjects.size)
        assertEquals(3, d.lessons.size)

        UntisMerge.disconnect(d)
        assertNull(d.untis)
        assertTrue(d.changes.isEmpty())
        assertEquals(3, d.lessons.size)
        assertTrue(d.lessons.all { it.fromUntis == null })
    }

    @Test
    fun lessonsGoneFromUntisAreRemovedAndUnlinked() {
        val d = AppData()
        UntisMerge.applySchedule(d, schedule, day(19), link, now, calendar)
        val physics = d.lessons.first { it.parity == WeekParity.a }
        d.participation.add(ParticipationEntry(subjectID = physics.subjectID, lessonID = physics.id, recordedAt = day(19)))
        val withoutPhysics = schedule.copy(lessons = schedule.lessons.filter { it.subjectKey != "PH" })
        UntisMerge.applySchedule(d, withoutPhysics, day(19), link, now, calendar)
        assertEquals(2, d.lessons.size)
        assertNull(d.participation.single().lessonID)
        assertEquals(physics.subjectID, d.participation.single().subjectID)
    }

    @Test
    fun holidaysFromUntisAreReplacedOwnOnesStay() {
        val d = AppData()
        d.holidays.add(Holiday(name = "Eigene", firstDay = day(1), lastDay = day(2)))
        d.holidays.add(Holiday(name = "Alt", firstDay = day(5), lastDay = day(6), fromUntis = true))
        UntisMerge.applyHolidays(d, listOf(FfiUntisHoliday("Herbstferien", ffi(12), ffi(24))), calendar)
        assertEquals(listOf("Eigene", "Herbstferien"), d.holidays.map { it.name })
        assertEquals(true, d.holidays[1].fromUntis)
        // An empty answer never removes holidays.
        UntisMerge.applyHolidays(d, emptyList(), calendar)
        assertEquals(2, d.holidays.size)
    }

    @Test
    fun homeworkIsAddedOnce() {
        val d = AppData()
        d.subjects.add(Subject(name = "Mathematik", untisKey = "M"))
        val homework = listOf(
            FfiUntisHomework(7, "M", ffi(20), ffi(22), "S. 12 Nr. 3", "", false),
            FfiUntisHomework(8, "D", ffi(20), ffi(23), "", "Gedicht lernen", true),
            FfiUntisHomework(9, "D", ffi(20), ffi(23), "", "", false),
        )
        val tasks = UntisMerge.homeworkTasks(d, homework, now, calendar)
        assertEquals(2, tasks.size) // No title, no task.
        assertEquals(d.subjects[0].id, tasks[0].subjectID)
        assertEquals(day(22), tasks[0].dueDate)
        assertEquals("Gedicht lernen", tasks[1].title)
        assertEquals("", tasks[1].notes)
        assertEquals(now, tasks[1].completedAt)

        UntisMerge.storeExtras(d, UntisExtras(updated = now), tasks)
        // The student edits one; the next import doesn't bring the old version back.
        d.tasks[0].title = "Bearbeitet"
        UntisMerge.storeExtras(d, UntisExtras(updated = now), UntisMerge.homeworkTasks(d, homework, now, calendar))
        assertEquals(2, d.tasks.size)
        assertEquals("Bearbeitet", d.tasks[0].title)
    }

    @Test
    fun refusedExtrasKeepEarlierAbsencesAndExamsButNotNews() {
        val exam = FfiUntisExam(1, ffi(28), 480u, 570u, "M", "Klausur", "KA", listOf("Kaya"), listOf("A1"), "")
        val first = UntisMerge.extras(null, emptyList(), listOf(exam), emptyList(), now, calendar)
        val previous = first.copy(news = mutableListOf(UntisNewsRecord(1, "Info", "Heute frei")))
        val next = UntisMerge.extras(previous, null, null, null, now, calendar)
        assertEquals(1, next.exams.size)
        assertEquals(day(28), next.exams[0].date)
        assertTrue(next.news.isEmpty())
    }

    @Test
    fun sessionIdAndSymbols() {
        assertEquals("ABC123", untisSessionID("schoolname=\"_Z3lt\"; JSESSIONID=ABC123; traceId=1"))
        assertNull(untisSessionID("schoolname=x"))
        assertEquals("function", SubjectSymbols.guess("Mathematik"))
        assertEquals("hands.and.sparkles", SubjectSymbols.guess("Evangelische Religion"))
        assertEquals("book.closed", SubjectSymbols.guess("Robotik-AG"))
    }
}
