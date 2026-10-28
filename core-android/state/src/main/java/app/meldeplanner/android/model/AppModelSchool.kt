package app.meldeplanner.android.model

import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.GradeEntry
import app.meldeplanner.android.data.GradeScaleChoice
import app.meldeplanner.android.data.Holiday
import app.meldeplanner.android.data.UntisAbsenceRecord
import app.meldeplanner.android.data.UntisExamRecord
import uniffi.melde_ffi.FfiAssessment
import uniffi.melde_ffi.FfiCategory
import uniffi.melde_ffi.FfiForecast
import uniffi.melde_ffi.FfiGradeScale
import uniffi.melde_ffi.FfiGradingConfig
import uniffi.melde_ffi.FfiPlannedAssessment
import uniffi.melde_ffi.FfiRoundingRule
import uniffi.melde_ffi.FfiSubjectSummary
import uniffi.melde_ffi.FfiSubjectWeight
import uniffi.melde_ffi.gradeForecast
import uniffi.melde_ffi.gradeOverallAverage
import uniffi.melde_ffi.gradeSubjectSummary
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter

// Holidays, WebUntis lookups and the Notenrechner. Ports of AppModel+Holidays,
// AppModel+School and AppModel+Grades.swift.

// region Holidays (Ferien)

/** The holiday that includes [date], if any. */
fun AppModel.holiday(on: Instant): Holiday? {
    val day = calendar.startOfDay(on)
    return data.holidays.firstOrNull { calendar.startOfDay(it.firstDay) <= day && day <= calendar.startOfDay(it.lastDay) }
}

/** A holiday that overlaps the week (Monday–Sunday) containing [date]. */
fun AppModel.holidayInWeekOf(date: Instant): Holiday? {
    val monday = calendar.weekStart(date)
    val sunday = calendar.addDays(monday, 6)
    return data.holidays.firstOrNull { calendar.startOfDay(it.firstDay) <= sunday && monday <= calendar.startOfDay(it.lastDay) }
}

/** Holidays that haven't ended yet, soonest first. */
fun AppModel.upcomingHolidays(from: Instant): List<Holiday> {
    val day = calendar.startOfDay(from)
    return data.holidays.filter { calendar.startOfDay(it.lastDay) >= day }.sortedBy { it.firstDay }
}

object HolidayText {
    /** "19.10.–01.11." in the app language. */
    fun range(holiday: Holiday, calendar: app.meldeplanner.android.core.MeldoCalendar): String {
        val style = DateTimeFormatter.ofPattern(
            if (Localization.language.code == "en") "MM/dd" else "dd.MM.",
            Localization.locale,
        )
        return "${calendar.localDate(holiday.firstDay).format(style)}–${calendar.localDate(holiday.lastDay).format(style)}"
    }
}

// endregion

// region WebUntis extras and Meldung days

/**
 * Exams from today on. Uses the WebUntis exam list; when a school hides it, the
 * exam periods from the timetable stand in.
 */
fun AppModel.upcomingUntisExams(from: Instant): List<UntisExamRecord> {
    val today = calendar.startOfDay(from)
    val order = compareBy<UntisExamRecord>({ it.date }, { it.startMinute })
    val listed = (data.untisExtras?.exams ?: emptyList()).filter { it.date >= today }
    if (listed.isNotEmpty()) return listed.sortedWith(order)
    return (data.untisTimetable?.periods ?: emptyList())
        .filter { it.exam && !it.cancelled && it.date >= today }
        .map { p ->
            UntisExamRecord(
                id = p.id, date = p.date, startMinute = p.startMinute, endMinute = p.endMinute,
                subject = p.subjectShort.ifEmpty { p.subjectLong },
                name = p.info ?: "", examType = "", teachers = if (p.teacher.isEmpty()) emptyList() else listOf(p.teacher),
                rooms = listOfNotNull(p.room), text = "",
            )
        }
        .sortedWith(order)
}

fun AppModel.untisExams(on: Instant): List<UntisExamRecord> =
    upcomingUntisExams(Instant.EPOCH).filter { calendar.isSameDay(it.date, on) }

fun AppModel.absences(on: Instant): List<UntisAbsenceRecord> {
    val day = calendar.startOfDay(on)
    return (data.untisExtras?.absences ?: emptyList()).filter {
        calendar.startOfDay(it.firstDay) <= day && day <= calendar.startOfDay(it.lastDay)
    }
}

/** Days (local midnight) with at least one Meldung. */
val AppModel.meldungDays: Set<Instant>
    get() = data.participation.map { calendar.startOfDay(it.recordedAt) }.toSet()

// endregion

// region Notenrechner

// The student's own grades, averaged by the Rust core with the configured scale
// and weights. Estimates only; official grades come from the school.
// Participation counts (Meldungen) are never mixed in.

val AppModel.gradeConfig: FfiGradingConfig
    get() {
        val p = data.preferences
        return FfiGradingConfig(
            scale = if (p.gradeScale == GradeScaleChoice.points) FfiGradeScale.POINTS else FfiGradeScale.TRADITIONAL,
            writtenWeight = p.writtenWeight,
            oralWeight = 1 - p.writtenWeight,
            rounding = FfiRoundingRule.HALF_TOWARD_BETTER,
        )
    }

fun AppModel.grades(subjectID: String): List<GradeEntry> =
    data.grades.filter { it.subjectID == subjectID }.sortedByDescending { it.date }

private fun assessments(grades: List<GradeEntry>): List<FfiAssessment> =
    grades.map { FfiAssessment(it.value, it.weight, if (it.written) FfiCategory.WRITTEN else FfiCategory.ORAL) }

fun AppModel.gradeSummary(subjectID: String): FfiSubjectSummary? {
    val list = grades(subjectID)
    if (list.isEmpty()) return null
    return runCatching { gradeSubjectSummary(gradeConfig, assessments(list)) }.getOrNull()
}

/** Average over all subjects with grades, each subject weighted equally. */
val AppModel.overallGradeAverage: Double?
    get() {
        val weights = data.subjects.mapNotNull { s -> gradeSummary(s.id)?.exact?.let { FfiSubjectWeight(it, 1.0) } }
        return gradeOverallAverage(weights)
    }

/** What the next written grade must be to reach [target] (e.g. 2.0 or 11 points). */
fun AppModel.gradeNeeded(subjectID: String, target: Double): FfiForecast? = runCatching {
    gradeForecast(
        gradeConfig, assessments(grades(subjectID)),
        listOf(FfiPlannedAssessment(1.0, FfiCategory.WRITTEN)), target,
    )
}.getOrNull()

fun AppModel.saveGrade(grade: GradeEntry) = mutate { d ->
    val i = d.grades.indexOfFirst { it.id == grade.id }
    if (i >= 0) d.grades[i] = grade else d.grades.add(grade)
}

fun AppModel.deleteGrade(id: String) = mutate { d -> d.grades.removeAll { it.id == id } }

/** Remembered for the next Entschuldigung. */
fun AppModel.setExcuseDetails(name: String, className: String, salutation: String, adult: Boolean) = mutate { d ->
    d.preferences.studentName = name.trim()
    d.preferences.className = className.trim()
    d.preferences.excuseSalutation = salutation.trim()
    d.preferences.adultStudent = adult
}

fun AppModel.setGradeScale(scale: GradeScaleChoice) = mutate { it.preferences.gradeScale = scale }

fun AppModel.setWrittenWeight(weight: Double) = mutate { it.preferences.writtenWeight = weight.coerceIn(0.0, 1.0) }

fun AppModel.setTendencyStep(step: Double) = mutate { it.preferences.tendencyStep = step }

/** "2,33" or "11,0 P." in the student's scale. */
fun AppModel.gradeText(value: Double): String {
    val points = data.preferences.gradeScale == GradeScaleChoice.points
    val format = NumberFormat.getNumberInstance(Localization.locale).apply {
        minimumFractionDigits = if (points) 1 else 2
        maximumFractionDigits = if (points) 1 else 2
    }
    val number = format.format(value)
    return if (points) L("%@ P.", number) else number
}

// endregion
