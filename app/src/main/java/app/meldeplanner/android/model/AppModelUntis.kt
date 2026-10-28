package app.meldeplanner.android.model

import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.core.mondayIndex
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.BellPeriod
import app.meldeplanner.android.data.Holiday
import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.SecureStore
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.TimetableChangeRecord
import app.meldeplanner.android.data.UntisAbsenceRecord
import app.meldeplanner.android.data.UntisClient
import app.meldeplanner.android.data.UntisExamRecord
import app.meldeplanner.android.data.UntisExtras
import app.meldeplanner.android.data.UntisFailure
import app.meldeplanner.android.data.UntisLink
import app.meldeplanner.android.data.UntisNewsRecord
import app.meldeplanner.android.data.UntisPeriodRecord
import app.meldeplanner.android.data.UntisTimetable
import app.meldeplanner.android.data.WeekParity
import app.meldeplanner.android.data.newID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.melde_ffi.FfiDate
import uniffi.melde_ffi.FfiTimeUnit
import uniffi.melde_ffi.FfiUntisAbsence
import uniffi.melde_ffi.FfiUntisAuth
import uniffi.melde_ffi.FfiUntisChangeKind
import uniffi.melde_ffi.FfiUntisExam
import uniffi.melde_ffi.FfiUntisHoliday
import uniffi.melde_ffi.FfiUntisHomework
import uniffi.melde_ffi.FfiUntisNews
import uniffi.melde_ffi.FfiUntisPeriod
import uniffi.melde_ffi.FfiUntisSchedule
import uniffi.melde_ffi.FfiUntisSchool
import uniffi.melde_ffi.FfiWeekParity
import uniffi.melde_ffi.MeldeException
import uniffi.melde_ffi.untisDeriveSchedule
import uniffi.melde_ffi.untisFirstTeachingWeek
import uniffi.melde_ffi.untisParsePeriods
import uniffi.melde_ffi.untisWeekStart
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// WebUntis import, port of meldo-ios/MeldePlanner/App/AppModel+Untis.swift:
// fetch eight weeks, let Rust derive subjects, weekly lessons and changes, then
// merge into the student's data. The merge rules live in [UntisMerge] (pure, unit-tested).

data class UntisImportSummary(
    var newSubjects: Int = 0,
    var lessons: Int = 0,
    var changes: Int = 0,
    var skippedExtra: Int = 0,
    var holidays: Int = 0,
)

/** How old WebUntis data may get while the app is open: just under the 30-second loop, so each tick updates. */
val untisRefreshInterval: Duration = Duration.ofSeconds(25)

/** How old it may get in the background: a little under the 20-minute rhythm, so an early wake-up still updates. */
val untisBackgroundRefreshInterval: Duration = Duration.ofMinutes(18)

/**
 * "server|school|user", the key it always had. A name containing "|" could make two
 * accounts share it, so those get an escaped key in their own "server#…" form.
 */
internal fun keychainAccount(link: UntisLink): String {
    if ('|' !in link.loginName && '|' !in link.user) return "${link.server}|${link.loginName}|${link.user}"
    fun part(text: String) = text.replace("%", "%25").replace("|", "%7C")
    return "${link.server}#${part(link.loginName)}|${part(link.user)}"
}

/** The secure store, bound to the app context on first use. */
internal fun AppModel.secrets(): SecureStore {
    context?.let(SecureStore::init)
    return SecureStore
}

val AppModel.untisLink: UntisLink? get() = data.untis

/** Logs in, imports, and remembers the connection (password → [SecureStore]). */
suspend fun AppModel.connectUntis(school: FfiUntisSchool, user: String, password: String): UntisImportSummary {
    // A new login stops a refresh still running for the old connection.
    untisGeneration += 1
    val generation = untisGeneration
    val link = UntisLink(server = school.server, loginName = school.loginName, schoolName = school.displayName, user = user)
    val summary = importFromUntis(link, password, generation)
    ensureUntisCurrent(generation)
    secrets().save(password, keychainAccount(link))
    return summary
}

/**
 * Imports with a session the student logged in to on the school's own login page
 * (Microsoft 365, IServ, …) inside Meldo. [cookies] is the `Cookie` header for the
 * WebUntis server; Meldo never sees the school password. [server] is the WebUntis
 * host the login ended on, which can differ from the search result.
 */
suspend fun AppModel.connectUntisWithBrowser(
    school: FfiUntisSchool,
    server: String,
    cookies: String,
    sessionID: String,
): UntisImportSummary {
    untisGeneration += 1
    val generation = untisGeneration
    val previous = data.untis
    val link = UntisLink(
        server = server, loginName = school.loginName, schoolName = school.displayName,
        user = "", browserLogin = true,
    )
    val client = UntisClient(browserCookies = cookies)
    val auth = client.browserAuth(link.server, sessionID)
    val summary = importFromUntis(link, client, auth, logout = false, generation = generation)
    ensureUntisCurrent(generation)
    // A login that ended on another server leaves no secret behind for the old one.
    if (previous != null && keychainAccount(previous) != keychainAccount(link)) secrets().delete(keychainAccount(previous))
    secrets().save(cookies, keychainAccount(link))
    return summary
}

/**
 * Re-imports with the stored password or school-login session. Returns null when
 * not connected; a school-login session that ran out throws [UntisFailure.SessionExpired].
 */
suspend fun AppModel.refreshUntis(): UntisImportSummary? {
    val link = data.untis ?: return null
    val generation = untisGeneration
    val secret = secrets().read(keychainAccount(link)) ?: return null
    if (link.browserLogin == true) {
        val client = UntisClient(browserCookies = secret)
        val auth = client.browserAuth(link.server, untisSessionID(secret) ?: "")
        return importFromUntis(link, client, auth, logout = false, generation = generation)
    }
    return importFromUntis(link, secret, generation)
}

/**
 * Whether [link]'s server may open in the school login page. The link can come from another
 * device (sync), so its server is only trusted when it is a webuntis.com host or WebUntis's
 * own school search ([search], by login name) lists it for this school.
 */
suspend fun untisServerIsListed(link: UntisLink, search: suspend (String) -> List<FfiUntisSchool>): Boolean {
    val host = link.server.lowercase()
    if (host == "webuntis.com" || host.endsWith(".webuntis.com")) return true
    return search(link.loginName).any {
        it.server.equals(host, ignoreCase = true) && it.loginName.equals(link.loginName, ignoreCase = true)
    }
}

/** The `JSESSIONID` value in a `Cookie` header. */
fun untisSessionID(cookies: String): String? =
    cookies.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("JSESSIONID=") }
        ?.removePrefix("JSESSIONID=")

/**
 * Automatic update: when the app opens, every 30 seconds while it's open and about
 * every 20 minutes in the background (WorkManager). Silent on failure.
 */
suspend fun AppModel.refreshUntisIfStale(maxAge: Duration = untisRefreshInterval) {
    if (isRefreshingUntis) return
    val link = data.untis ?: return
    val last = link.lastSync
    // A "last update" in the future (clock changed) doesn't block updates.
    val age = last?.let { Duration.between(it, currentDate) }
    if (age != null && !age.isNegative && age < maxAge) return
    // After a failure it waits, so a wrong password isn't sent to the school every 30 seconds.
    untisRetryAfter?.let { if (currentDate.isBefore(it)) return }
    isRefreshingUntis = true
    try {
        refreshUntis()
    } catch (e: UntisFailure) {
        // Silent: the status screen shows the last sync time.
        untisFailures += 1
        untisRetryAfter = untisRetryDelay(e, untisFailures)?.let { currentDate.plus(it) } ?: Instant.MAX
    } finally {
        isRefreshingUntis = false
    }
}

/**
 * How long automatic updates pause after a failure. A wrong password stops them (null) until
 * the next manual update or connect, since schools can lock accounts after failed logins; an
 * expired school login or holidays wait 30 minutes; other errors back off from one minute.
 */
fun untisRetryDelay(failure: UntisFailure, failures: Int): Duration? = when (failure) {
    UntisFailure.WrongCredentials -> null
    UntisFailure.SessionExpired, UntisFailure.NoLessons -> Duration.ofMinutes(30)
    else -> Duration.ofMinutes(1L shl (failures.coerceIn(1, 6) - 1)).coerceAtMost(Duration.ofMinutes(30))
}

/** Forgets the connection and the password. Imported lessons stay as normal lessons. */
fun AppModel.disconnectUntis() {
    untisGeneration += 1
    // Forget the school login kept by the WebView too, so nobody else on this device
    // is signed back in to WebUntis or the school's SSO.
    android.webkit.CookieManager.getInstance().run { removeAllCookies(null); flush() }
    android.webkit.WebStorage.getInstance().deleteAllData()
    data.untis?.let { secrets().delete(keychainAccount(it)) }
    mutate(UntisMerge::disconnect)
}

/** Throws when "WebUntis trennen" ran after the import with this generation started. */
private fun AppModel.ensureUntisCurrent(generation: Int) {
    if (generation != untisGeneration) throw UntisFailure.NoAccess("disconnected")
}

private suspend fun AppModel.importFromUntis(link: UntisLink, password: String, generation: Int): UntisImportSummary {
    val client = UntisClient()
    val auth = client.login(link.server, link.loginName, link.user, password)
    return importFromUntis(link, client, auth, logout = true, generation = generation)
}

/** Failures of optional parts become null, like Swift's `try?` (cancellation still propagates). */
private suspend fun <T> optional(work: suspend () -> T): T? = try {
    work()
} catch (e: UntisFailure) {
    null
}

/**
 * [logout] ends password sessions; school-login sessions stay open so the next
 * refresh can reuse them.
 */
private suspend fun AppModel.importFromUntis(
    link: UntisLink,
    client: UntisClient,
    auth: FfiUntisAuth,
    logout: Boolean,
    generation: Int,
): UntisImportSummary {
    val calendar = calendar
    val today = calendar.ffiDate(currentDate)
    val thisWeek = runCatching { untisWeekStart(today) }.getOrNull() ?: throw UntisFailure.NoAccess("date")
    val thisMonday = runCatching { calendar.instant(thisWeek) }.getOrNull() ?: throw UntisFailure.NoAccess("date")
    val lastDay = calendar.addDays(thisMonday, 8L * 7 - 1)
    // Holidays and the bell schedule are a bonus: an import still works when a school hides them.
    val holidays = optional { client.holidays(link.server, link.loginName) } ?: emptyList()
    val bells = optional { client.timegrid(link.server, link.loginName) } ?: emptyList()
    // Eight weeks in one request. Schools can limit how far ahead students see;
    // then WebUntis refuses the request, so ask for fewer weeks.
    var body = ""
    var fetchedLastDay = lastDay
    for (weeks in listOf(8, 4, 2, 1)) {
        val last = calendar.addDays(thisMonday, weeks * 7L - 1)
        body = client.timetable(link.server, link.loginName, auth, thisWeek, calendar.ffiDate(last))
        fetchedLastDay = last
        if (parsePeriods(body) != null) break
    }
    ensureUntisCurrent(generation)
    parsePeriods(body)?.let { storeUntisWeeks(it, bells, thisMonday, fetchedLastDay) }
    try {
        return importSchedule(link, body, thisWeek, holidays, generation)
    } finally {
        // After the subjects exist (applyUntis), so homework finds the subject it belongs to;
        // also when the weeks hold no lessons, so absences and homework stay current.
        importUntisExtras(client, link.server, auth, generation)
        if (logout) client.logout(link.server, link.loginName)
    }
}

/** Derives the weekly plan from the dated periods and applies it. */
private suspend fun AppModel.importSchedule(
    link: UntisLink,
    body: String,
    thisWeek: FfiDate,
    holidays: List<FfiUntisHoliday>,
    generation: Int,
): UntisImportSummary {
    val schedule: FfiUntisSchedule
    val monday: Instant
    try {
        val timetableBody = body
        val firstWeek = withContext(Dispatchers.Default) { untisFirstTeachingWeek(timetableBody, thisWeek) }
        val firstMonday = firstWeek?.let { runCatching { calendar.instant(it) }.getOrNull() }
        if (firstWeek == null || firstMonday == null) {
            // Never replace the timetable with an empty one; keep the holidays.
            applyUntisHolidays(holidays)
            throw UntisFailure.NoLessons
        }
        schedule = withContext(Dispatchers.Default) { untisDeriveSchedule(timetableBody, firstWeek) }
        monday = firstMonday
    } catch (e: UntisFailure) {
        throw e
    } catch (e: MeldeException.UntisServer) {
        throw if (e.code == -8520L) {
            if (link.browserLogin == true) UntisFailure.SessionExpired else UntisFailure.WrongCredentials
        } else {
            UntisFailure.Server(e.code, e.detail)
        }
    } catch (e: MeldeException) {
        throw UntisFailure.NoAccess(e.toString())
    }
    ensureUntisCurrent(generation)
    applyUntisHolidays(holidays)
    val syncedLink = link.copy(lastSync = currentDate)
    untisFailures = 0
    untisRetryAfter = null
    val summary = applyUntis(schedule, monday, syncedLink)
    summary.holidays = holidays.size
    return summary
}

private suspend fun parsePeriods(body: String): List<FfiUntisPeriod>? =
    withContext(Dispatchers.Default) { runCatching { untisParsePeriods(body) }.getOrNull() }

/**
 * Absences (this school year), exams (next four months), homework (two weeks back
 * to eight ahead) and today's school messages. Each part is optional: schools
 * decide what students may see.
 */
private suspend fun AppModel.importUntisExtras(client: UntisClient, server: String, auth: FfiUntisAuth, generation: Int) {
    val calendar = calendar
    val today = calendar.startOfDay(currentDate)
    val todayLocal = calendar.localDate(today)
    val year = todayLocal.year - if (todayLocal.monthValue < 8) 1 else 0
    val schoolYearStart = calendar.instant(LocalDate.of(year, 8, 1))
    fun day(offset: Long): FfiDate = calendar.ffiDate(calendar.addDays(today, offset))
    val todayFfi = calendar.ffiDate(today)
    val absences = optional { client.absences(server, auth, calendar.ffiDate(schoolYearStart), todayFfi) }
    // Some schools refuse the exam list to students but mark exams in the timetable.
    val exams = optional { client.exams(server, auth, todayFfi, day(120)) }
        ?: optional { client.timetableExams(server, auth, todayFfi, day(120)) }
    val homework = optional { client.homework(server, day(-14), day(56)) }
    val news = optional { client.news(server, todayFfi) }

    if (generation != untisGeneration) return
    val extras = UntisMerge.extras(data.untisExtras, absences, exams, news, currentDate, calendar)
    val tasks = UntisMerge.homeworkTasks(data, homework ?: emptyList(), currentDate, calendar)
    mutate { d -> UntisMerge.storeExtras(d, extras, tasks) }
}

/**
 * Keeps the dated periods for the Untis-style timetable. [lastDay] is the end of
 * the window WebUntis accepted, so a school's look-ahead limit shows.
 */
fun AppModel.storeUntisWeeks(periods: List<FfiUntisPeriod>, bells: List<FfiTimeUnit>, firstDay: Instant, lastDay: Instant) {
    val calendar = calendar
    mutate { d -> UntisMerge.storeWeeks(d, periods, bells, firstDay, lastDay, calendar) }
}

/** Replaces the holidays from an earlier WebUntis import; ones the student added by hand stay. */
fun AppModel.applyUntisHolidays(holidays: List<FfiUntisHoliday>) {
    if (holidays.isEmpty()) return
    val calendar = calendar
    val now = currentDate
    mutate { d -> UntisMerge.applyHolidays(d, holidays, calendar, now) }
}

/**
 * Merges an Untis schedule. Existing subjects are matched by Untis key or name;
 * existing lessons in the same slot keep their id (and Meldungen).
 */
fun AppModel.applyUntis(schedule: FfiUntisSchedule, weekAStart: Instant, link: UntisLink): UntisImportSummary {
    val now = currentDate
    val calendar = calendar
    var summary = UntisImportSummary()
    mutate { d -> summary = UntisMerge.applySchedule(d, schedule, weekAStart, link, now, calendar) }
    return summary
}

/**
 * The merge rules of the WebUntis import as pure functions on [AppData], so they
 * run (and are tested) without the app model or the network. Each function may be
 * applied more than once to fresh data (see [AppModel.mutate]); it returns its
 * counts instead of accumulating them.
 */
internal object UntisMerge {
    fun disconnect(d: AppData) {
        d.untis = null
        d.untisTimetable = null
        d.untisExtras = null
        d.changes.removeAll { it.fromUntis == true }
        for (lesson in d.lessons) lesson.fromUntis = null
    }

    private fun MeldoCalendar.day(date: FfiDate): Instant? = runCatching { instant(date) }.getOrNull()

    fun storeWeeks(
        d: AppData,
        periods: List<FfiUntisPeriod>,
        bells: List<FfiTimeUnit>,
        firstDay: Instant,
        lastDay: Instant,
        calendar: MeldoCalendar,
    ) {
        val records = periods.mapNotNull { p ->
            val date = calendar.day(p.date) ?: return@mapNotNull null
            UntisPeriodRecord(
                id = p.id, date = date, startMinute = p.startMinute.toInt(), endMinute = p.endMinute.toInt(),
                subjectShort = p.subjectShort, subjectLong = p.subjectLong, teacher = p.teacher,
                room = p.room, originalRoom = p.originalRoom,
                cancelled = p.cancelled, irregular = p.irregular, exam = p.exam, info = p.info,
            )
        }
        val bellPeriods = bells.map { BellPeriod(it.name, it.startMinute.toInt(), it.endMinute.toInt()) }
        d.untisTimetable = UntisTimetable(
            periods = records.toMutableList(), firstDay = firstDay, lastDay = lastDay,
            bells = (bellPeriods.ifEmpty { d.untisTimetable?.bells ?: emptyList() }).toMutableList(),
        )
    }

    /**
     * WebUntis sends every holiday since the school started using it (often ten years,
     * with duplicates) and splits Ferien into single days. Keeps this school year on
     * (from [now]) and joins days of the same Ferien into one entry, as iOS does; so a
     * day inside the Ferien shows their name, not a single-day entry next to it.
     */
    fun applyHolidays(d: AppData, holidays: List<FfiUntisHoliday>, calendar: MeldoCalendar, now: Instant? = null) {
        if (holidays.isEmpty()) return
        val schoolYearStart = now?.let {
            val today = calendar.localDate(it)
            val year = today.year - if (today.monthValue < 8) 1 else 0
            calendar.instant(java.time.LocalDate.of(year, 8, 1))
        }
        val ranges = holidays.mapNotNull { h ->
            val first = calendar.day(h.firstDay) ?: return@mapNotNull null
            val last = calendar.day(h.lastDay) ?: return@mapNotNull null
            if (schoolYearStart != null && last < schoolYearStart) return@mapNotNull null
            Triple(h.name.trim(), first, maxOf(first, last))
        }.sortedWith(compareBy({ it.second }, { it.third }))
        val merged = mutableListOf<Holiday>()
        for ((name, first, last) in ranges) {
            val i = merged.indexOfLast { it.name == name }
            if (i >= 0 && first <= calendar.addDays(merged[i].lastDay, 1)) {
                merged[i].lastDay = maxOf(merged[i].lastDay, last)
            } else {
                merged.add(Holiday(name = name, firstDay = first, lastDay = last, fromUntis = true))
            }
        }
        d.holidays.removeAll { it.fromUntis == true }
        d.holidays.addAll(merged)
        d.holidays.sortBy { it.firstDay }
    }

    /** New extras; a part WebUntis refused (null) keeps the earlier absences and exams. */
    fun extras(
        previous: UntisExtras?,
        absences: List<FfiUntisAbsence>?,
        exams: List<FfiUntisExam>?,
        news: List<FfiUntisNews>?,
        now: Instant,
        calendar: MeldoCalendar,
    ): UntisExtras = UntisExtras(
        absences = (
            absences?.mapNotNull { a ->
                val first = calendar.day(a.firstDay) ?: return@mapNotNull null
                val last = calendar.day(a.lastDay) ?: return@mapNotNull null
                UntisAbsenceRecord(
                    id = a.id, firstDay = first, lastDay = last,
                    startMinute = a.startMinute.toInt(), endMinute = a.endMinute.toInt(),
                    reason = a.reason, text = a.text, excused = a.excused,
                )
            } ?: previous?.absences ?: emptyList()
            ).toMutableList(),
        exams = (
            exams?.mapNotNull { e ->
                val date = calendar.day(e.date) ?: return@mapNotNull null
                UntisExamRecord(
                    id = e.id, date = date, startMinute = e.startMinute.toInt(), endMinute = e.endMinute.toInt(),
                    subject = e.subject, name = e.name, examType = e.examType,
                    teachers = e.teachers, rooms = e.rooms, text = e.text,
                )
            } ?: previous?.exams ?: emptyList()
            ).toMutableList(),
        news = (news?.map { UntisNewsRecord(it.id, it.subject, it.text) } ?: emptyList()).toMutableList(),
        updated = now,
    )

    /** WebUntis homework as Aufgaben (kind homework). */
    fun homeworkTasks(d: AppData, homework: List<FfiUntisHomework>, now: Instant, calendar: MeldoCalendar): List<SchoolTask> =
        homework.mapNotNull { h ->
            val due = calendar.day(h.due) ?: return@mapNotNull null
            val subject = d.subjects.firstOrNull { it.untisKey == h.subject || it.name == h.subject }
            val title = h.text.ifEmpty { h.remark }
            if (title.isEmpty()) return@mapNotNull null
            SchoolTask(
                title = title, notes = if (h.text.isEmpty()) "" else h.remark, kind = TaskKind.homework,
                subjectID = subject?.id, dueDate = due,
                completedAt = if (h.completed) now else null, untisID = h.id,
            )
        }

    fun storeExtras(d: AppData, extras: UntisExtras, tasks: List<SchoolTask>) {
        d.untisExtras = extras
        // New WebUntis homework only; what the student already has stays as they left it.
        val known = d.tasks.mapNotNull { it.untisID }.toSet()
        d.tasks.addAll(tasks.filter { (it.untisID ?: 0L) !in known })
        // Homework imported before its subject existed gets it now; a subject the student set stays.
        val subjects = tasks.associate { it.untisID to it.subjectID }
        for (t in d.tasks) {
            if (t.untisID != null && t.subjectID == null) t.subjectID = subjects[t.untisID]
        }
    }

    /** Case- and diacritic-insensitive equality, like `compare(_:options: [.caseInsensitive, .diacriticInsensitive])`. */
    private fun sameName(a: String, b: String): Boolean = folded(a) == folded(b)

    private val marks = Regex("\\p{Mn}+")

    private fun folded(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    fun applySchedule(
        d: AppData,
        schedule: FfiUntisSchedule,
        weekAStart: Instant,
        link: UntisLink,
        now: Instant,
        calendar: MeldoCalendar,
    ): UntisImportSummary {
        val summary = UntisImportSummary(changes = schedule.changes.size, skippedExtra = schedule.skippedExtra.toInt())
        // Subjects.
        val subjectByKey = mutableMapOf<String, String>()
        val palette = SubjectColor.entries
        schedule.subjects.forEachIndexed { index, s ->
            var i = d.subjects.indexOfFirst { it.untisKey == s.key }
            if (i < 0) {
                i = d.subjects.indexOfFirst {
                    sameName(it.name, s.name) || (s.shortName.isNotEmpty() && it.shortName.equals(s.shortName, ignoreCase = true))
                }
            }
            if (i >= 0) {
                d.subjects[i].untisKey = s.key
                subjectByKey[s.key] = d.subjects[i].id
            } else {
                val subject = Subject(
                    name = s.name, shortName = s.shortName,
                    color = palette[(d.subjects.size + index) % palette.size],
                    symbolName = SubjectSymbols.guess(s.name),
                    updatedAt = now, untisKey = s.key,
                )
                d.subjects.add(subject)
                subjectByKey[s.key] = subject.id
                summary.newSubjects += 1
            }
        }

        // Lessons: reuse ids of lessons in the same slot, replace earlier Untis lessons.
        val kept = mutableSetOf<String>()
        val lessonByKeySlot = mutableMapOf<String, String>()
        for (l in schedule.lessons) {
            val subjectID = subjectByKey[l.subjectKey] ?: continue
            val weekday = l.weekday.mondayIndex
            val parity = when (l.parity) {
                FfiWeekParity.EVERY -> WeekParity.every
                FfiWeekParity.A -> WeekParity.a
                FfiWeekParity.B -> WeekParity.b
            }
            val start = l.startMinute.toInt()
            val existing = d.lessons.indexOfFirst {
                it.weekday == weekday && it.startMinute == start && it.subjectID == subjectID
            }
            val id = if (existing >= 0) d.lessons[existing].id else newID()
            val lesson = Lesson(
                id = id, subjectID = subjectID, weekday = weekday,
                startMinute = start, endMinute = l.endMinute.toInt(),
                parity = parity, room = l.room ?: "", updatedAt = now, fromUntis = true,
            )
            if (existing >= 0) d.lessons[existing] = lesson else d.lessons.add(lesson)
            kept.add(id)
            lessonByKeySlot["${l.subjectKey}|$weekday|$start"] = id
        }
        val removed = d.lessons.filter { it.fromUntis == true && it.id !in kept }.map { it.id }.toSet()
        d.lessons.removeAll { it.id in removed }
        for (p in d.participation) {
            if (p.lessonID != null && p.lessonID in removed) p.lessonID = null
        }
        summary.lessons = kept.size

        // Changes: replace earlier Untis changes.
        d.changes.removeAll { it.fromUntis == true || it.lessonID in removed }
        for (c in schedule.changes) {
            val day = calendar.day(c.date) ?: continue
            val weekday = calendar.mondayIndex(day)
            val lessonID = lessonByKeySlot["${c.subjectKey}|$weekday|${c.startMinute.toInt()}"] ?: continue
            when (val kind = c.kind) {
                is FfiUntisChangeKind.Cancelled -> d.changes.add(
                    TimetableChangeRecord(lessonID = lessonID, date = day, kind = TimetableChangeRecord.Kind.cancelled, fromUntis = true),
                )
                is FfiUntisChangeKind.Substituted -> d.changes.add(
                    TimetableChangeRecord(
                        lessonID = lessonID, date = day, kind = TimetableChangeRecord.Kind.substituted,
                        substituteSubjectID = kind.subjectKey?.let { subjectByKey[it] }, room = kind.room, fromUntis = true,
                    ),
                )
            }
        }

        // A/B weeks: the first imported week is week A when lessons alternate.
        if (schedule.lessons.any { it.parity != FfiWeekParity.EVERY }) {
            d.preferences.weekAReference = weekAStart
        }
        d.untis = link
        return summary
    }
}

/** SF Symbol guesses for common German subject names (mapped to Material icons by `sfSymbol`). */
object SubjectSymbols {
    private val table = listOf(
        "mathe" to "function", "deutsch" to "text.book.closed", "englisch" to "character.book.closed",
        "franz" to "character.book.closed", "latein" to "building.columns", "spanisch" to "character.book.closed",
        "physik" to "atom", "chemie" to "flask", "bio" to "leaf", "geschicht" to "building.columns",
        "erdkunde" to "globe.europe.africa", "geograph" to "globe.europe.africa", "politik" to "scalemass",
        "sozial" to "scalemass", "wirtschaft" to "chart.line.uptrend.xyaxis", "kunst" to "paintpalette",
        "musik" to "music.note", "sport" to "figure.run", "religion" to "hands.and.sparkles",
        "ethik" to "hands.and.sparkles", "philosoph" to "theatermasks", "informatik" to "laptopcomputer",
        "theater" to "theatermasks",
    )

    fun guess(name: String): String {
        val n = name.lowercase()
        return table.firstOrNull { n.contains(it.first) }?.second ?: "book.closed"
    }
}
