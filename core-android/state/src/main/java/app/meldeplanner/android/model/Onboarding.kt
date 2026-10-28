package app.meldeplanner.android.model

/**
 * Who sees the first-run tour. Only new students: an existing student who updates
 * already has subjects or a WebUntis connection and never gets it, even though their
 * file predates the "seen" flag.
 */
object Onboarding {
    fun shouldShow(seen: Boolean, hasSubjects: Boolean, hasUntis: Boolean): Boolean = !seen && !hasSubjects && !hasUntis
}

/** Finished or skipped (including closing it): never shown again on its own. */
fun AppModel.finishOnboarding() {
    showingOnboarding = false
    if (data.preferences.onboardingSeen) return
    mutate { it.preferences.onboardingSeen = true }
}

/** From "Einführung ansehen" in Mehr and Über Meldo. */
fun AppModel.showOnboardingAgain() {
    showingOnboarding = true
}
