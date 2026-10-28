package app.meldeplanner.android.core

import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.data.ParticipationEntry
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.TimetableChangeRecord
import app.meldeplanner.android.data.WeekParity
import uniffi.melde_ffi.FfiChangeKind
import uniffi.melde_ffi.FfiHoliday
import uniffi.melde_ffi.FfiLesson
import uniffi.melde_ffi.FfiOccurrence
import uniffi.melde_ffi.FfiParticipationEntry
import uniffi.melde_ffi.FfiTimetable
import uniffi.melde_ffi.FfiTimetableChange
import uniffi.melde_ffi.FfiWeekParity
import uniffi.melde_ffi.FfiWeekSummary
import uniffi.melde_ffi.participationWeekSummary
import uniffi.melde_ffi.timetableCurrent
import uniffi.melde_ffi.timetableNext
import uniffi.melde_ffi.timetableParticipationLesson
import java.time.Instant

/**
 * Pure functions over [AppData] shared by the app, widgets, the Quick Settings
 * tile and notification actions, so every surface logs a Meldung the same way.
 * The rules themselves live in the Rust core. Port of meldo-ios/Shared/MeldeEngine.swift.
 */
object MeldeEngine {
    /** Minutes after the bell in which a Meldung still counts for the lesson that just ended. */
    const val PARTICIPATION_GRACE_MINUTES: UShort = 5u

    fun timetable(data: AppData, calendar: MeldoCalendar): FfiTimetable = FfiTimetable(
        lessons = data.lessons.map(::ffiLesson),
        holidays = data.holidays.map {
            FfiHoliday(it.name, calendar.ffiDate(it.firstDay), calendar.ffiDate(it.lastDay))
        },
        changes = data.changes.map {
            FfiTimetableChange(
                lessonId = it.lessonID,
                date = calendar.ffiDate(it.date),
                kind = if (it.kind == TimetableChangeRecord.Kind.cancelled) {
                    FfiChangeKind.Cancelled
                } else {
                    FfiChangeKind.Substituted(it.substituteSubjectID, it.room)
                },
            )
        },
        weekAReference = data.preferences.weekAReference?.let(calendar::ffiDate),
    )

    fun ffiLesson(l: Lesson): FfiLesson = FfiLesson(
        id = l.id,
        subjectId = l.subjectID,
        weekday = ffiWeekday(l.weekday),
        startMinute = l.startMinute.coerceIn(0, 65535).toUShort(),
        endMinute = l.endMinute.coerceIn(0, 65535).toUShort(),
        parity = when (l.parity) {
            WeekParity.every -> FfiWeekParity.EVERY
            WeekParity.a -> FfiWeekParity.A
            WeekParity.b -> FfiWeekParity.B
        },
        room = l.room.ifEmpty { null },
    )

    fun entries(data: AppData, calendar: MeldoCalendar): List<FfiParticipationEntry> =
        data.participation.map { FfiParticipationEntry(it.id, it.subjectID, calendar.ffiDateTime(it.recordedAt)) }

    fun currentLesson(data: AppData, at: Instant, calendar: MeldoCalendar): FfiOccurrence? =
        runCatching { timetableCurrent(timetable(data, calendar), calendar.ffiDateTime(at)) }.getOrNull()

    fun nextLesson(data: AppData, after: Instant, calendar: MeldoCalendar): FfiOccurrence? =
        runCatching { timetableNext(timetable(data, calendar), calendar.ffiDateTime(after), 14u) }.getOrNull()

    /** The lesson a Meldung at [at] belongs to (current lesson or one that just ended). */
    fun participationLesson(data: AppData, at: Instant, calendar: MeldoCalendar): FfiOccurrence? =
        runCatching {
            timetableParticipationLesson(timetable(data, calendar), calendar.ffiDateTime(at), PARTICIPATION_GRACE_MINUTES)
        }.getOrNull()

    /** Records a Meldung for the lesson at [at]; `null` when no lesson matches. */
    fun recordForLesson(data: AppData, at: Instant, calendar: MeldoCalendar): ParticipationEntry? {
        val lesson = participationLesson(data, at, calendar) ?: return null
        val entry = ParticipationEntry(subjectID = lesson.subjectId, lessonID = lesson.lessonId, recordedAt = at)
        data.participation.add(entry)
        return entry
    }

    fun weekSummary(data: AppData, containing: Instant, calendar: MeldoCalendar): FfiWeekSummary? =
        runCatching { participationWeekSummary(entries(data, calendar), calendar.ffiDate(containing)) }.getOrNull()

    /** Meldungen already recorded for one lesson on one day. */
    fun count(data: AppData, lessonID: String, on: Instant, calendar: MeldoCalendar): Int =
        data.participation.count { it.lessonID == lessonID && calendar.isSameDay(it.recordedAt, on) }

    fun subject(data: AppData, id: String?): Subject? =
        id?.let { key -> data.subjects.firstOrNull { it.id.equals(key, ignoreCase = true) } }
}
