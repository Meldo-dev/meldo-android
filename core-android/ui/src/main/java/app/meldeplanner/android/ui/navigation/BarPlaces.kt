package app.meldeplanner.android.ui.navigation

import app.meldeplanner.android.data.Preferences

// The phone bar's two flexible places (Einstellungen › Leiste und Startseite).
// Port of AppTab.barChoices / barSlots in meldo-ios/MeldePlanner/Navigation/RootView.swift;
// the raw values in Preferences.barTabs and startTab are the feature ids, as on iOS.

/** Aufgaben and Noten until the student chooses (or when the choice is invalid). */
private val defaultSlots = listOf("tasks", "grades")

/** On tablets Dokumente always sits in the top group. */
private const val TABLET_PLACE = "exams"

/** What the student can put into the two flexible places of the bar. */
val Features.barChoices: List<MeldoFeature> get() = placed(Placement.school).filter { it.barChoice }

/** The places always in the bar (Start, Stundenplan). */
val Features.fixedBar: List<MeldoFeature> get() = placed(Placement.bar)

/** Mehr, the phone's last tab. */
val Features.hub: MeldoFeature? get() = placed(Placement.hub).firstOrNull()

/** The two places chosen in Einstellungen, filled up from the defaults and the other choices. */
fun Features.barSlots(preferences: Preferences): List<MeldoFeature> {
    val choices = barChoices
    val chosen = preferences.barTabs.mapNotNull { id -> choices.firstOrNull { it.id == id } }
    if (chosen.size == 2 && chosen[0] != chosen[1]) return chosen
    val fallback = defaultSlots.mapNotNull { id -> choices.firstOrNull { it.id == id } } + choices
    return fallback.distinct().take(2)
}

/** The phone bar: Start, Stundenplan, the two chosen places, Mehr. */
fun Features.phoneBar(preferences: Preferences): List<MeldoFeature> = fixedBar + barSlots(preferences) + listOfNotNull(hub)

/** Tablets: Start, Stundenplan, the two chosen places, then Dokumente unless it is one of them. */
fun Features.tabletBar(preferences: Preferences): List<MeldoFeature> {
    val bar = fixedBar + barSlots(preferences)
    val extra = get(TABLET_PLACE)
    return if (extra == null || extra in bar) bar else bar + extra
}

/** Every school place that isn't in the bar (Mehr on phones, the Schule section on tablets). */
fun Features.placesOutsideBar(preferences: Preferences, wide: Boolean): List<MeldoFeature> {
    val bar = if (wide) tabletBar(preferences) else phoneBar(preferences)
    return barChoices.filter { it !in bar } + placed(Placement.school).filter { !it.barChoice }
}

/** The screen Meldo opens on; Start when the chosen one isn't in the bar (phones). */
fun Features.startTab(preferences: Preferences, wide: Boolean): MeldoFeature? {
    val allowed = fixedBar + if (wide) barChoices else barSlots(preferences)
    return allowed.firstOrNull { it.id == preferences.startTab } ?: fixedBar.firstOrNull() ?: all.firstOrNull()
}
