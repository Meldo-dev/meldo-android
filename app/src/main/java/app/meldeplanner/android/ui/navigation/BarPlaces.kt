package app.meldeplanner.android.ui.navigation

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.Preferences
import app.meldeplanner.android.ui.features.calendar.CalendarScreen
import app.meldeplanner.android.ui.features.exams.ExamListScreen
import app.meldeplanner.android.ui.features.grades.GradesScreen
import app.meldeplanner.android.ui.features.statistics.StatisticsScreen
import app.meldeplanner.android.ui.features.tasks.TaskListScreen
import app.meldeplanner.android.ui.features.timetable.HolidayListScreen
import app.meldeplanner.android.ui.features.untis.AbsencesScreen

// The phone bar's two flexible places (Einstellungen › Leiste und Startseite).
// Port of AppTab.barChoices / barSlots in meldo-ios/MeldePlanner/Navigation/RootView.swift;
// the raw values in Preferences.barTabs and startTab are the AppTab names, as on iOS.

/** What the student can put into the two flexible places of the bar. */
val barChoices: List<AppTab> = listOf(AppTab.tasks, AppTab.grades, AppTab.calendar, AppTab.statistics, AppTab.exams, AppTab.absences)

/** The two places chosen in Einstellungen; Aufgaben and Noten until then (or when the choice is invalid). */
fun barSlots(preferences: Preferences): List<AppTab> {
    val chosen = preferences.barTabs.mapNotNull { raw -> AppTab.entries.firstOrNull { it.name == raw } }.filter { it in barChoices }
    return if (chosen.size == 2 && chosen[0] != chosen[1]) chosen else listOf(AppTab.tasks, AppTab.grades)
}

/** The phone bar: Start, Stundenplan, the two chosen places, Mehr. */
fun phoneBar(preferences: Preferences): List<AppTab> = listOf(AppTab.home, AppTab.timetable) + barSlots(preferences) + AppTab.more

/** Tablets: Start, Stundenplan, the two chosen places, then Dokumente unless it is one of them. */
fun tabletBar(preferences: Preferences): List<AppTab> {
    val bar = listOf(AppTab.home, AppTab.timetable) + barSlots(preferences)
    return if (AppTab.exams in bar) bar else bar + AppTab.exams
}

/** Every place that isn't in the bar, then Ferien (Mehr on phones, the Schule section on tablets). */
fun placesOutsideBar(preferences: Preferences, wide: Boolean): List<AppTab> {
    val bar = if (wide) tabletBar(preferences) else phoneBar(preferences)
    return barChoices.filter { it !in bar } + AppTab.holidays
}

/** The screen Meldo opens on; Start when the chosen one isn't in the bar (phones). */
fun startTab(preferences: Preferences, wide: Boolean): AppTab {
    val chosen = AppTab.entries.firstOrNull { it.name == preferences.startTab } ?: return AppTab.home
    val allowed = if (wide) listOf(AppTab.home, AppTab.timetable) + barChoices else listOf(AppTab.home, AppTab.timetable) + barSlots(preferences)
    return if (chosen in allowed) chosen else AppTab.home
}

val AppTab.title: String
    get() = when (this) {
        AppTab.home -> L("Start")
        AppTab.timetable -> L("Stundenplan")
        AppTab.tasks -> L("Aufgaben")
        AppTab.grades -> L("Noten")
        AppTab.calendar -> L("Kalender")
        AppTab.statistics -> L("Statistik")
        AppTab.exams -> L("Dokumente")
        AppTab.absences -> L("Abwesenheiten")
        AppTab.holidays -> L("Ferien")
        AppTab.more -> L("Mehr")
        else -> name
    }

val AppTab.symbol: String
    get() = when (this) {
        AppTab.home -> "house"
        AppTab.timetable -> "tablecells"
        AppTab.tasks -> "checklist"
        AppTab.grades -> "graduationcap"
        AppTab.calendar -> "calendar"
        AppTab.statistics -> "chart.bar"
        AppTab.exams -> "doc.on.doc"
        AppTab.absences -> "person.crop.circle.badge.clock"
        AppTab.holidays -> "sun.horizon"
        AppTab.more -> "ellipsis.circle"
        else -> "circle"
    }

/** The screen for a place that can sit in the bar or under Mehr. */
@Composable
fun AppTab.Page() {
    when (this) {
        AppTab.tasks -> TaskListScreen()
        AppTab.grades -> GradesScreen()
        AppTab.calendar -> CalendarScreen()
        AppTab.statistics -> StatisticsScreen()
        AppTab.exams -> ExamListScreen()
        AppTab.absences -> AbsencesScreen()
        AppTab.holidays -> HolidayListScreen()
        else -> {}
    }
}
