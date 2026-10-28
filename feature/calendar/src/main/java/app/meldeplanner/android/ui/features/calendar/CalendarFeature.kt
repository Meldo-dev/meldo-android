package app.meldeplanner.android.ui.features.calendar

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Kalender: the month with lessons, tasks, exams and Meldungen per day. */
object CalendarFeature : MeldoFeature {
    override val id = "calendar"
    override val title get() = L("Kalender")
    override val symbol = "calendar"
    override val placement = Placement.school
    override val barChoice = true

    @Composable
    override fun Page() = CalendarScreen()
}
