package app.meldeplanner.android.ui.features.statistics

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Statistik: Meldungen per week, subject and trend. */
object StatisticsFeature : MeldoFeature {
    override val id = "statistics"
    override val title get() = L("Statistik")
    override val symbol = "chart.bar"
    override val placement = Placement.school
    override val barChoice = true

    @Composable
    override fun Page() = StatisticsScreen()
}
