package app.meldeplanner.android

import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.TaskPriority
import app.meldeplanner.android.data.TaskRecurrence
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.finishOnboarding
import app.meldeplanner.android.model.upsert
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The sample school week of the "Meldo Demo" build (store screenshots, the website), the
 * same as the iOS demo: five subjects, a full timetable, three weeks of Meldungen,
 * homework and a weekly goal, on a fixed clock (Friday 23 October 2026, 09:00, during
 * the second lesson). Kept in memory only: no store, so nothing is ever saved.
 */
object DemoData {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val clock = LocalDateTime.of(2026, 10, 23, 9, 0).atZone(zone).toInstant()

    fun model(app: MeldoApp): AppModel = AppModel(app, store = null, now = { clock }).also(::seed)

    private fun seed(model: AppModel) {
        val subjects = listOf(
            Subject(name = "Mathematik", shortName = "M", color = SubjectColor.blue, symbolName = "function", teacher = "Fr. Weber"),
            Subject(name = "Deutsch", shortName = "D", color = SubjectColor.red, symbolName = "text.book.closed", teacher = "Hr. Schulz"),
            Subject(name = "Englisch", shortName = "E", color = SubjectColor.purple, symbolName = "character.book.closed"),
            Subject(name = "Biologie", shortName = "Bio", color = SubjectColor.green, symbolName = "leaf"),
            Subject(name = "Sport", shortName = "Sp", color = SubjectColor.orange, symbolName = "figure.run"),
        )
        subjects.forEach(model::upsert)
        val ids = subjects.map { it.id }
        // (weekday, start minute, subject index); 45-minute lessons.
        val plan = listOf(
            Triple(0, 8 * 60, 0), Triple(0, 8 * 60 + 50, 1), Triple(0, 9 * 60 + 50, 2), Triple(0, 10 * 60 + 40, 3),
            Triple(1, 8 * 60, 1), Triple(1, 8 * 60 + 50, 0), Triple(1, 9 * 60 + 50, 4),
            Triple(2, 8 * 60, 2), Triple(2, 8 * 60 + 50, 3), Triple(2, 9 * 60 + 50, 0),
            Triple(3, 8 * 60, 0), Triple(3, 8 * 60 + 50, 2), Triple(3, 9 * 60 + 50, 1),
            Triple(4, 8 * 60, 3), Triple(4, 8 * 60 + 50, 4), Triple(4, 9 * 60 + 50, 1),
        )
        for ((weekday, start, subject) in plan) {
            model.save(Lesson(subjectID = ids[subject], weekday = weekday, startMinute = start, endMinute = start + 45, room = "A${10 + subject}"))
        }
        // Meldungen over the last three weeks (none today, so the first tap is visible).
        val today = clock.atZone(zone).toLocalDate()
        for (dayOffset in 1..20) {
            val day = today.minusDays(dayOffset.toLong())
            if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) continue
            for (i in 0..dayOffset % 3) {
                val at = day.atStartOfDay(zone).toInstant().plusSeconds((8L * 60 + 10 + i * 50) * 60)
                model.recordParticipation(ids[(dayOffset + i) % ids.size], at)
            }
        }
        model.setWeeklyGoal(12)
        fun dayStart(days: Long) = today.plusDays(days).atStartOfDay(zone).toInstant()
        model.upsert(SchoolTask(title = "Aufgaben S. 42, Nr. 3–5", subjectID = ids[0], dueDate = dayStart(1), priority = TaskPriority.high))
        model.upsert(SchoolTask(title = "Gedichtanalyse fertig schreiben", subjectID = ids[1], dueDate = dayStart(7)))
        model.upsert(SchoolTask(title = "Vokabeln Unit 3", kind = TaskKind.homework, subjectID = ids[2], dueDate = dayStart(0), recurrence = TaskRecurrence.weekly))
        model.upsert(SchoolTask(title = "Sportsachen einpacken", kind = TaskKind.todo))
        model.finishOnboarding()
    }
}
