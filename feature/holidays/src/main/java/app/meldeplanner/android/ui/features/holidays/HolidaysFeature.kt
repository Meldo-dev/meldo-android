package app.meldeplanner.android.ui.features.holidays

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.features.holidays.HolidayListScreen
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Ferien: the school's holidays (from WebUntis). */
object HolidaysFeature : MeldoFeature {
    override val id = "holidays"
    override val title get() = L("Ferien")
    override val symbol = "sun.horizon"
    override val placement = Placement.school

    @Composable
    override fun Page() = HolidayListScreen()
}
