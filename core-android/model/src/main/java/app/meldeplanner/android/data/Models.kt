@file:Suppress("EnumEntryName")

package app.meldeplanner.android.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// The same document as meldo-ios/Shared/Models.swift: identical field names, enum raw
// values and ISO-8601 dates, so one data.json reads on both platforms. Enum entries
// keep the Swift raw values (lower camel case) for that reason.

/** A new id in the format Swift's `UUID().uuidString` produces. */
fun newID(): String = UUID.randomUUID().toString().uppercase()

/** A school subject (Fach). Colors are palette keys, never raw values. */
@Serializable
data class Subject(
    var id: String = newID(),
    var name: String,
    var shortName: String = "",
    var color: SubjectColor = SubjectColor.blue,
    /** SF Symbol name on iOS; mapped to a Material icon on Android. */
    var symbolName: String = "book.closed",
    var teacher: String = "",
    @Serializable(InstantSerializer::class) var updatedAt: Instant = Instant.now(),
    /** Untis short name when imported from WebUntis; matches subjects across imports. */
    var untisKey: String? = null,
) {
    val displayShortName: String get() = shortName.ifEmpty { name.take(3) }
}

@Serializable
enum class WeekParity { every, a, b }

/** A recurring timetable slot. `weekday` is Monday-first (0 = Montag). */
@Serializable
data class Lesson(
    var id: String = newID(),
    var subjectID: String,
    var weekday: Int,
    var startMinute: Int,
    var endMinute: Int,
    var parity: WeekParity = WeekParity.every,
    var room: String = "",
    @Serializable(InstantSerializer::class) var updatedAt: Instant = Instant.now(),
    /** `true` when this lesson came from WebUntis and is updated by later imports. */
    var fromUntis: Boolean? = null,
)

/** A change to one lesson on one date (Ausfall or Vertretung), e.g. from WebUntis. */
@Serializable
data class TimetableChangeRecord(
    var id: String = newID(),
    var lessonID: String,
    /** The day (local midnight). */
    @Serializable(InstantSerializer::class) var date: Instant,
    var kind: Kind,
    var substituteSubjectID: String? = null,
    var room: String? = null,
    var fromUntis: Boolean? = null,
) {
    @Serializable
    enum class Kind { cancelled, substituted }
}

/** The student's WebUntis connection. The password is kept in [SecureStore], never here. */
@Serializable
data class UntisLink(
    var server: String,
    var loginName: String,
    var schoolName: String,
    var user: String,
    @Serializable(InstantSerializer::class) var lastSync: Instant? = null,
    /** Logged in through the school's own login page; the secure store then holds session cookies. */
    var browserLogin: Boolean? = null,
    /** The WebUntis link the student tapped to reach the school's login (a path on [server], not a secret). */
    var ssoPath: String? = null,
)

@Serializable
data class Holiday(
    var id: String = newID(),
    var name: String,
    @Serializable(InstantSerializer::class) var firstDay: Instant,
    @Serializable(InstantSerializer::class) var lastDay: Instant,
    /** Imported from WebUntis; replaced on every import. */
    var fromUntis: Boolean? = null,
)

/** One recorded Meldung. An activity count, never a grade. */
@Serializable
data class ParticipationEntry(
    var id: String = newID(),
    var subjectID: String? = null,
    var lessonID: String? = null,
    @Serializable(InstantSerializer::class) var recordedAt: Instant,
)

@Serializable
enum class AppLanguage(val code: String) {
    @kotlinx.serialization.SerialName("de") german("de"),
    @kotlinx.serialization.SerialName("en") english("en");

    /** Shown in its own language so users can always find theirs. */
    val nativeName: String
        get() = when (this) {
            german -> "Deutsch"
            english -> "English"
        }
}

@Serializable
enum class TaskKind { homework, todo }

@Serializable
enum class TaskPriority { low, normal, high }

@Serializable
enum class TaskRecurrence { never, daily, weekdays, weekly, biweekly }

/** A Hausaufgabe or to-do. Named `SchoolTask` like on iOS. */
@Serializable
data class SchoolTask(
    var id: String = newID(),
    var title: String,
    var notes: String = "",
    var kind: TaskKind = TaskKind.homework,
    var subjectID: String? = null,
    /** Due day (local midnight); `null` for undated to-dos. */
    @Serializable(InstantSerializer::class) var dueDate: Instant? = null,
    @Serializable(InstantSerializer::class) var completedAt: Instant? = null,
    var priority: TaskPriority = TaskPriority.normal,
    var recurrence: TaskRecurrence = TaskRecurrence.never,
    @Serializable(InstantSerializer::class) var reminderAt: Instant? = null,
    @Serializable(InstantSerializer::class) var updatedAt: Instant = Instant.now(),
    /** Homework imported from WebUntis; keeps it from being added twice. */
    var untisID: Long? = null,
    /** Assignment imported from Microsoft Teams (Graph id). */
    var teamsID: String? = null,
    /** The recurring task whose completion created this one (set by iOS; kept so sync doesn't drop it). */
    var spawnedFrom: String? = null,
) {
    val isDone: Boolean get() = completedAt != null
}

/** A scanned or imported exam (Klassenarbeit, Klausur, Test) stored as a PDF. */
@Serializable
data class ExamDocument(
    var id: String = newID(),
    var title: String,
    var subjectID: String? = null,
    @Serializable(InstantSerializer::class) var date: Instant,
    /** Path inside the Scans folder, e.g. "Mathematik/Exams/1. Klausur (12.10.2026).pdf". */
    var fileName: String,
    var pageCount: Int,
    /** The grade as written by the teacher (e.g. "2-" or "11 P."), shown as-is. */
    var grade: String = "",
    var notes: String = "",
    @Serializable(InstantSerializer::class) var updatedAt: Instant = Instant.now(),
    var category: DocumentCategory? = null,
) {
    val kind: DocumentCategory get() = category ?: DocumentCategory.exam
}

/** What a scan is; also its folder name (English names, as on iOS). */
@Serializable
enum class DocumentCategory {
    exam, worksheet, document;

    val folderName: String
        get() = when (this) {
            exam -> "Exams"
            worksheet -> "Worksheets"
            document -> "Documents"
        }
}

/** Cards on the home screen; order and visibility are customizable. */
@Serializable
enum class DashboardCard {
    today, weekPreview, participation, streak, homework, exams, news, absences,
    nextLesson, weekChart, nextHoliday, todos, grades,
}

/**
 * The Start cards this build offers. Exams, news, absences and holidays came only from
 * the WebUntis import, which is not in this build; their kinds stay so saved layouts and
 * synced preferences keep working.
 */
val shownDashboardCards: List<DashboardCard> =
    DashboardCard.entries - setOf(DashboardCard.exams, DashboardCard.news, DashboardCard.absences, DashboardCard.nextHoliday)

/** Cards on the Statistik tab. */
@Serializable
enum class StatsCard { perDay, bySubject, trend }

/** How much room a card takes. */
@Serializable
enum class CardSize { small, medium, large }

/** A visible card and its size, in display order. */
@Serializable
data class CardSlot<K>(var kind: K, var size: CardSize)

val defaultHomeLayout: List<CardSlot<DashboardCard>>
    get() = listOf(
        CardSlot(DashboardCard.today, CardSize.large),
        CardSlot(DashboardCard.participation, CardSize.small),
        CardSlot(DashboardCard.streak, CardSize.small),
        CardSlot(DashboardCard.weekPreview, CardSize.medium),
        CardSlot(DashboardCard.homework, CardSize.medium),
        CardSlot(DashboardCard.exams, CardSize.medium),
        CardSlot(DashboardCard.news, CardSize.medium),
        CardSlot(DashboardCard.absences, CardSize.small),
    )

val defaultStatsLayout: List<CardSlot<StatsCard>>
    get() = listOf(
        CardSlot(StatsCard.perDay, CardSize.medium),
        CardSlot(StatsCard.bySubject, CardSize.medium),
        CardSlot(StatsCard.trend, CardSize.large),
    )

/** Launcher icons, switched with activity aliases (the iOS alternate icons). */
@Serializable
enum class AppIconChoice { standard, orange, night }

@Serializable
data class Preferences(
    var language: AppLanguage = AppLanguage.german,
    var theme: AppTheme = AppTheme.orange,
    var appearance: AppearanceChoice = AppearanceChoice.system,
    /** Weekly Melde target; `null` until the student sets or accepts one. */
    var weeklyGoal: Int? = null,
    /** Any date in an A week; enables A/B-week lessons. */
    @Serializable(InstantSerializer::class) var weekAReference: Instant? = null,
    /** Ask "Hast du dich gemeldet?" after each lesson (local notification). */
    var askAfterLesson: Boolean = false,
    /** Show a live lesson notification (iOS: Live Activity). */
    var liveActivities: Boolean = true,
    var showMascot: Boolean = true,
    var haptics: Boolean = true,
    /** Start screen cards with their sizes, in display order; missing cards are hidden. */
    var homeLayout: MutableList<CardSlot<DashboardCard>> = defaultHomeLayout.toMutableList(),
    var statsLayout: MutableList<CardSlot<StatsCard>> = defaultStatsLayout.toMutableList(),
    /** Parallel courses the student doesn't attend, as "weekday|start|subject|teacher". */
    var hiddenCourses: MutableList<String> = mutableListOf(),
    var gradeScale: GradeScaleChoice = GradeScaleChoice.traditional,
    /** Share of written grades in a subject average (the rest is oral). */
    var writtenWeight: Double = 0.5,
    /** What + and − add or subtract on the 1–6 scale (e.g. 0.33 or 0.25). */
    var tendencyStep: Double = 0.33,
    /** For the excuse note (Entschuldigung). */
    var studentName: String = "",
    var className: String = "",
    var excuseSalutation: String = "",
    var adultStudent: Boolean = false,
    var appIcon: AppIconChoice = AppIconChoice.standard,
    /** The start screen stops suggesting after-lesson check-ins once answered. */
    var checkInPromptDismissed: Boolean = false,
    /**
     * The first-run tour was finished or skipped. Students who already have data never
     * see it, so older files don't need this set.
     */
    var onboardingSeen: Boolean = false,
    /** Android only: take the accent from the wallpaper (Material You) instead of [theme]. */
    var dynamicColor: Boolean = false,
    /**
     * Microsoft Teams shows up only when switched on (Einstellungen › Erweitert) or
     * already connected: many schools have to allow Meldo for Teams first. Same key on iOS.
     */
    var teamsEnabled: Boolean = false,
    /** The phone bar's two flexible places (feature ids), like iOS. Invalid → Aufgaben, Noten. */
    var barTabs: MutableList<String> = mutableListOf("tasks", "grades"),
    /** The screen Meldo opens on (a feature id). */
    var startTab: String = "home",
)

/** One dated period exactly as WebUntis shows it (Untis-style timetable). */
@Serializable
data class UntisPeriodRecord(
    var id: Long,
    @Serializable(InstantSerializer::class) var date: Instant,
    var startMinute: Int,
    var endMinute: Int,
    var subjectShort: String,
    var subjectLong: String,
    var teacher: String,
    var room: String? = null,
    /** Planned room when the period moved (Raumänderung). */
    var originalRoom: String? = null,
    var cancelled: Boolean,
    /** Vertretung or extra period. */
    var irregular: Boolean,
    var exam: Boolean,
    var info: String? = null,
) {
    /** Matches [Subject.untisKey]. */
    val subjectKey: String get() = subjectShort.ifEmpty { subjectLong }
}

/** One period of the school's bell schedule; breaks are the gaps between them. */
@Serializable
data class BellPeriod(var name: String, var startMinute: Int, var endMinute: Int)

/** The weeks WebUntis lets the student see, refreshed on every import. */
@Serializable
data class UntisTimetable(
    var periods: MutableList<UntisPeriodRecord>,
    @Serializable(InstantSerializer::class) var firstDay: Instant,
    /** Last day WebUntis returned; schools can limit how far ahead students see. */
    @Serializable(InstantSerializer::class) var lastDay: Instant,
    var bells: MutableList<BellPeriod>,
)

/** An absence from the WebUntis class register. */
@Serializable
data class UntisAbsenceRecord(
    var id: Long,
    @Serializable(InstantSerializer::class) var firstDay: Instant,
    @Serializable(InstantSerializer::class) var lastDay: Instant,
    var startMinute: Int,
    var endMinute: Int,
    var reason: String,
    var text: String,
    var excused: Boolean,
)

/** An exam WebUntis lists for the student (Klausur, Test). */
@Serializable
data class UntisExamRecord(
    var id: Long,
    @Serializable(InstantSerializer::class) var date: Instant,
    var startMinute: Int,
    var endMinute: Int,
    var subject: String,
    var name: String,
    var examType: String,
    var teachers: List<String>,
    var rooms: List<String>,
    var text: String,
)

/** A message of the day from the school. */
@Serializable
data class UntisNewsRecord(var id: Long, var subject: String, var text: String)

/** Data from the WebUntis web API, refreshed on every import. */
@Serializable
data class UntisExtras(
    var absences: MutableList<UntisAbsenceRecord> = mutableListOf(),
    var exams: MutableList<UntisExamRecord> = mutableListOf(),
    var news: MutableList<UntisNewsRecord> = mutableListOf(),
    @Serializable(InstantSerializer::class) var updated: Instant,
)

/** One grade the student enters. The student's own record, never official. */
@Serializable
data class GradeEntry(
    var id: String = newID(),
    var subjectID: String,
    /** 1.0–6.0 (traditional, tendency already applied) or 0–15 points. */
    var value: Double,
    var written: Boolean,
    var weight: Double = 1.0,
    var title: String = "",
    @Serializable(InstantSerializer::class) var date: Instant,
)

/** How grades are entered and averaged. Configuration, not constants: schools differ. */
@Serializable
enum class GradeScaleChoice {
    /** 1 (best) to 6. */
    traditional,

    /** 15 (best) to 0, Oberstufe. */
    points,
}

/** The Microsoft Teams connection. The refresh token lives in [SecureStore]. */
@Serializable
data class TeamsLink(
    var account: String,
    @Serializable(InstantSerializer::class) var lastSync: Instant? = null,
)

/** Everything persisted on device. Versioned like on iOS. */
@Serializable
data class AppData(
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    var subjects: MutableList<Subject> = mutableListOf(),
    var lessons: MutableList<Lesson> = mutableListOf(),
    var holidays: MutableList<Holiday> = mutableListOf(),
    var participation: MutableList<ParticipationEntry> = mutableListOf(),
    var tasks: MutableList<SchoolTask> = mutableListOf(),
    var exams: MutableList<ExamDocument> = mutableListOf(),
    var changes: MutableList<TimetableChangeRecord> = mutableListOf(),
    var untis: UntisLink? = null,
    var untisTimetable: UntisTimetable? = null,
    var untisExtras: UntisExtras? = null,
    var teams: TeamsLink? = null,
    var grades: MutableList<GradeEntry> = mutableListOf(),
    var preferences: Preferences = Preferences(),
    /**
     * Imported WebUntis homework ("untis:<id>") and Teams assignments ("teams:<id>")
     * the student deleted, so the next import doesn't bring them back.
     */
    var dismissedImports: MutableSet<String> = mutableSetOf(),
    /** What changed when, for Google Drive sync (see [SyncMerge]). */
    var sync: SyncLedger = SyncLedger(),
) {
    companion object {
        /** 2: adds tasks and exams. */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
