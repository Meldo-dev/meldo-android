package app.meldeplanner.android.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.AppIconChoice
import app.meldeplanner.android.data.AppLanguage
import app.meldeplanner.android.data.AppTheme
import app.meldeplanner.android.data.AppearanceChoice
import app.meldeplanner.android.data.CardSlot
import app.meldeplanner.android.data.DashboardCard
import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.data.LocalStore
import app.meldeplanner.android.data.MeldoJson
import app.meldeplanner.android.data.ParticipationEntry
import app.meldeplanner.android.data.Preferences
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.StatsCard
import app.meldeplanner.android.data.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uniffi.melde_ffi.FfiOccurrence
import uniffi.melde_ffi.FfiParticipationEntry
import uniffi.melde_ffi.FfiTimetable
import uniffi.melde_ffi.FfiTimetableIssue
import uniffi.melde_ffi.FfiWeekSummary
import uniffi.melde_ffi.participationStreak
import uniffi.melde_ffi.participationSuggestWeeklyTarget
import uniffi.melde_ffi.timetableOccurrencesOn
import uniffi.melde_ffi.timetableValidate
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Single source of truth for the UI, port of meldo-ios/MeldePlanner/App/AppModel.swift.
 * Business rules (current lesson, streaks, targets, validation) come from the Rust
 * core via [MeldeEngine]; this class owns state, persistence and the mapping
 * between the Kotlin models and the FFI records. Feature extensions (AppModel*.kt)
 * change data only through [mutate].
 *
 * Widgets, notifications and Google Drive hear about changes through [Listener]s, and
 * connected services keep their own state in [part]s, so this class knows none of them.
 */
class AppModel(
    /** `null` in unit tests; then reminders, widgets and notifications are skipped. */
    val context: Context?,
    val store: LocalStore?,
    val calendar: MeldoCalendar = MeldoCalendar.current,
    private val now: () -> Instant = Instant::now,
) {
    var data: AppData by mutableStateOf(AppData())
        private set

    /** User-facing problem with loading or saving, shown as a banner. */
    var storageProblem: String? by mutableStateOf(null)

    /** The first-run tour is showing (new students, or "Einführung ansehen"). */
    var showingOnboarding: Boolean by mutableStateOf(false)

    /** Work that outlives a screen (imports, schedules). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        data = try {
            store?.load() ?: AppData()
        } catch (e: LocalStore.LoadException) {
            storageProblem = L("Deine gespeicherten Daten konnten nicht gelesen werden. Eine Sicherungskopie wurde behalten.")
            AppData()
        }
        Localization.apply(data.preferences.language)
        // An unreadable file looks empty, but its owner is not new: no tour then.
        showingOnboarding = store != null && storageProblem == null && Onboarding.shouldShow(
            seen = data.preferences.onboardingSeen,
            hasSubjects = data.subjects.isNotEmpty(),
            hasUntis = data.untis != null,
        )
    }

    val currentDate: Instant get() = now()

    // region Listeners and parts

    /**
     * Hears about changes: widgets, reminders and the live lesson (Surfaces), and
     * Google Drive sync. Registered at app start; none in unit tests.
     */
    interface Listener {
        /** After every saved change. */
        fun saved(model: AppModel) {}

        /** The data changed: home screen widgets and the tile show it again. */
        fun reloadWidgets(context: Context) {}

        /** Plans after-lesson check-ins and the live lesson again (off the main thread). */
        fun reschedule(context: Context, data: AppData, calendar: MeldoCalendar, at: Instant) {}

        /** A Meldung was recorded for the current lesson. */
        fun recorded(context: Context, entry: ParticipationEntry, calendar: MeldoCalendar) {}

        /** A task was saved: plans its reminder (or cancels it when done or without one). */
        fun scheduleReminder(context: Context, task: SchoolTask, subjectName: String?) {}

        /** A task was deleted. */
        fun cancelReminder(context: Context, taskID: String) {}
    }

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    /** Tells every listener; [AppModel] extensions call this after their own changes. */
    fun notify(event: Listener.() -> Unit) = listeners.forEach(event)

    /**
     * A service that keeps itself current (Google Drive, WebUntis, Teams): when the app
     * opens, every 30 seconds while it is open ([frequent]) and in the background.
     */
    interface Connection {
        /** Updates if due. [background]: a WorkManager run, which comes less often. */
        suspend fun refresh(model: AppModel, background: Boolean)

        /** Also in the open app's 30-second loop. */
        val frequent: Boolean get() = false

        /** Runs before the others: a sync, so a disconnect made on another device is seen first. */
        val syncsData: Boolean get() = false

        /** Wants another background run about 5 minutes after the student leaves Meldo. */
        fun wantsSoon(model: AppModel): Boolean = false
    }

    private val connections = mutableListOf<Connection>()

    fun addConnection(connection: Connection) {
        connections += connection
        connections.sortBy { !it.syncsData }
    }

    /** Updates every connected service that is due, syncs first. */
    suspend fun refreshConnections(background: Boolean = false, onlyFrequent: Boolean = false) {
        for (c in connections.toList()) if (!onlyFrequent || c.frequent) c.refresh(this, background)
    }

    val wantsRefreshSoon: Boolean get() = connections.any { it.wantsSoon(this) }

    private val parts = mutableMapOf<KClass<*>, Any>()

    /** State a module keeps for this model (Drive, WebUntis, Teams), created on first use. */
    fun <T : Any> part(type: KClass<T>, create: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return parts.getOrPut(type, create) as T
    }

    // endregion

    // region Persistence

    /**
     * Applies a change to the latest stored data and saves it, so entries written
     * meanwhile by a widget or notification action are kept.
     */
    fun mutate(change: (AppData) -> Unit) {
        if (store == null) {
            val copy = copyOf(data)
            change(copy)
            data = copy
            return
        }
        try {
            data = store.update(change = change)
            storageProblem = null
        } catch (e: Exception) {
            val copy = copyOf(data)
            change(copy)
            data = copy
            storageProblem = L("Änderungen konnten nicht gespeichert werden. Prüfe den freien Speicher.")
        }
        context?.let { notify { reloadWidgets(it) } }
        refreshSchedules()
        notify { saved(this@AppModel) }
    }

    /** Re-plans after-lesson check-ins and the live lesson notification for the latest data. */
    fun refreshSchedules() {
        val context = context ?: return
        val snapshot = copyOf(data)
        val at = now()
        scope.launch(Dispatchers.Default) {
            notify { reschedule(context, snapshot, calendar, at) }
        }
    }

    /** Picks up changes made outside the app (widgets, tile, notification actions). */
    fun reloadFromDisk() {
        val fresh = runCatching { store?.load() }.getOrNull() ?: return
        data = fresh
        Localization.apply(fresh.preferences.language)
    }

    fun copyOf(d: AppData): AppData =
        MeldoJson.decodeFromString(AppData.serializer(), MeldoJson.encodeToString(AppData.serializer(), d))

    // endregion

    // region Preferences

    val preferences: Preferences get() = data.preferences

    fun setLanguage(language: AppLanguage) {
        Localization.apply(language)
        mutate { it.preferences.language = language }
        context?.let { Localization.applyToSystem(it, language) }
    }

    fun setAppearance(appearance: AppearanceChoice) = mutate { it.preferences.appearance = appearance }

    fun setTheme(theme: AppTheme) = mutate {
        it.preferences.theme = theme
        it.preferences.dynamicColor = false
    }

    /** Material You: the accent follows the wallpaper. */
    fun setDynamicColor(on: Boolean) = mutate { it.preferences.dynamicColor = on }

    fun setWeeklyGoal(goal: Int?) {
        val clamped = goal?.coerceIn(1, 200)
        mutate { it.preferences.weeklyGoal = clamped }
    }

    fun setAskAfterLesson(on: Boolean) = mutate {
        it.preferences.askAfterLesson = on
        it.preferences.checkInPromptDismissed = true
    }

    fun dismissCheckInPrompt() = mutate { it.preferences.checkInPromptDismissed = true }

    fun setLiveActivities(on: Boolean) = mutate { it.preferences.liveActivities = on }

    fun setShowMascot(on: Boolean) = mutate { it.preferences.showMascot = on }

    fun setHaptics(on: Boolean) = mutate { it.preferences.haptics = on }

    fun setBarTabs(tabs: List<String>) = mutate { it.preferences.barTabs = tabs.toMutableList() }

    fun setStartTab(tab: String) = mutate { it.preferences.startTab = tab }

    fun setHomeLayout(layout: List<CardSlot<DashboardCard>>) = mutate { it.preferences.homeLayout = layout.toMutableList() }

    fun setStatsLayout(layout: List<CardSlot<StatsCard>>) = mutate { it.preferences.statsLayout = layout.toMutableList() }

    fun setAppIcon(icon: AppIconChoice) = mutate { it.preferences.appIcon = icon }

    fun setWeekAReference(date: Instant?) = mutate { it.preferences.weekAReference = date }

    /** Marks the week containing today as an A week (or B week). */
    fun setCurrentWeekIsA(isA: Boolean) {
        val today = now()
        setWeekAReference(if (isA) today else calendar.addWeeks(today, -1))
    }

    fun isWeekA(date: Instant): Boolean {
        val reference = data.preferences.weekAReference ?: return true
        return calendar.weeksBetween(reference, date) % 2 == 0L
    }

    // endregion

    // region Subjects

    val subjects: List<Subject> get() = data.subjects

    fun subject(id: String?): Subject? = MeldeEngine.subject(data, id)

    fun upsert(subject: Subject) {
        val s = subject.copy(name = subject.name.trim())
        if (s.name.isEmpty()) return
        s.updatedAt = now()
        mutate { d ->
            val i = d.subjects.indexOfFirst { it.id == s.id }
            if (i >= 0) d.subjects[i] = s else d.subjects.add(s)
        }
    }

    /** Deletes a subject and its lessons. Past Meldungen are kept without a subject. */
    fun deleteSubject(id: String) = mutate { d ->
        val lessonIDs = d.lessons.filter { it.subjectID == id }.map { it.id }.toSet()
        d.subjects.removeAll { it.id == id }
        d.lessons.removeAll { it.subjectID == id }
        d.changes.removeAll { it.lessonID in lessonIDs }
        d.changes.filter { it.substituteSubjectID == id }.forEach { it.substituteSubjectID = null }
        for (p in d.participation) {
            if (p.subjectID == id) p.subjectID = null
            if (p.lessonID in lessonIDs) p.lessonID = null
        }
    }

    // endregion

    // region Timetable

    val lessons: List<Lesson> get() = data.lessons

    fun lessons(onWeekday: Int): List<Lesson> =
        data.lessons.filter { it.weekday == onWeekday }.sortedBy { it.startMinute }

    /** Validates with the Rust core and saves when there is no conflict. */
    fun save(lesson: Lesson): List<FfiTimetableIssue> {
        val updated = lesson.copy(updatedAt = now())
        val candidate = data.lessons.filter { it.id != lesson.id } + updated
        val issues = validate(candidate)
        if (issues.isNotEmpty()) return issues
        mutate { d ->
            d.lessons.removeAll { it.id == updated.id }
            d.lessons.add(updated)
        }
        return emptyList()
    }

    fun deleteLesson(id: String) = mutate { d ->
        d.lessons.removeAll { it.id == id }
        d.changes.removeAll { it.lessonID == id }
        d.participation.filter { it.lessonID == id }.forEach { it.lessonID = null }
    }

    fun validate(lessons: List<Lesson>): List<FfiTimetableIssue> {
        // Only the weekly plan is validated here; per-date changes are managed separately.
        val timetable = ffiTimetable.copy(lessons = lessons.map(MeldeEngine::ffiLesson), changes = emptyList())
        return runCatching { timetableValidate(timetable) }.getOrDefault(emptyList())
    }

    val ffiTimetable: FfiTimetable get() = MeldeEngine.timetable(data, calendar)

    fun occurrences(on: Instant): List<FfiOccurrence> =
        runCatching { timetableOccurrencesOn(ffiTimetable, calendar.ffiDate(on)) }.getOrDefault(emptyList())

    fun currentLesson(at: Instant): FfiOccurrence? = MeldeEngine.currentLesson(data, at, calendar)

    fun nextLesson(after: Instant): FfiOccurrence? = MeldeEngine.nextLesson(data, after, calendar)

    fun subject(occurrence: FfiOccurrence): Subject? = subject(occurrence.subjectId)

    // endregion

    // region Melde Tracker

    sealed interface RecordOutcome {
        data class Recorded(val entry: ParticipationEntry) : RecordOutcome

        /** No lesson right now: the UI asks for the subject (second tap). */
        data object NeedsSubject : RecordOutcome
    }

    /** One-tap recording, auto-assigned to the current lesson when possible. */
    fun recordParticipation(at: Instant? = null): RecordOutcome {
        val time = at ?: now()
        if (MeldeEngine.participationLesson(data, time, calendar) == null) return RecordOutcome.NeedsSubject
        var entry: ParticipationEntry? = null
        mutate { entry = MeldeEngine.recordForLesson(it, time, calendar) }
        val recorded = entry ?: return RecordOutcome.NeedsSubject
        context?.let { notify { recorded(it, recorded, calendar) } }
        return RecordOutcome.Recorded(recorded)
    }

    fun recordParticipation(subjectID: String?, at: Instant? = null): ParticipationEntry {
        val entry = ParticipationEntry(subjectID = subjectID, lessonID = null, recordedAt = at ?: now())
        mutate { it.participation.add(entry) }
        return entry
    }

    fun deleteParticipation(id: String) = mutate { d -> d.participation.removeAll { it.id == id } }

    fun reassignParticipation(id: String, subjectID: String?) = mutate { d ->
        d.participation.firstOrNull { it.id == id }?.let {
            it.subjectID = subjectID
            it.lessonID = null
        }
    }

    /** Today's Meldungen logged without a subject (e.g. from the tile outside a lesson). */
    fun unassignedEntries(on: Instant): List<ParticipationEntry> = entries(on).filter { it.subjectID == null }

    private val ffiEntries: List<FfiParticipationEntry> get() = MeldeEngine.entries(data, calendar)

    fun weekSummary(containing: Instant): FfiWeekSummary? = MeldeEngine.weekSummary(data, containing, calendar)

    fun streak(asOf: Instant): Int =
        runCatching { participationStreak(ffiEntries, ffiTimetable, calendar.ffiDate(asOf)).toInt() }.getOrDefault(0)

    /** Totals of the [count] weeks before the week containing [date], oldest first. */
    fun previousWeekTotals(before: Instant, count: Int): List<UInt> =
        (maxOf(count, 1) downTo 1).mapNotNull { offset -> weekSummary(calendar.addWeeks(before, -offset.toLong()))?.total }

    fun suggestedWeeklyGoal(asOf: Instant): Int? {
        // Leading empty weeks are from before the student started using the app.
        val history = previousWeekTotals(asOf, 4).dropWhile { it == 0u }
        return participationSuggestWeeklyTarget(history)?.toInt()
    }

    fun entries(on: Instant): List<ParticipationEntry> =
        data.participation.filter { calendar.isSameDay(it.recordedAt, on) }.sortedByDescending { it.recordedAt }

    // endregion
}
