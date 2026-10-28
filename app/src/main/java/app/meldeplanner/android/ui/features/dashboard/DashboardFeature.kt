package app.meldeplanner.android.ui.features.dashboard

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Start: the dashboard with its cards. */
object DashboardFeature : MeldoFeature {
    override val id = "home"
    override val title get() = L("Start")
    override val symbol = "house"
    override val placement = Placement.bar

    @Composable
    override fun Page() = DashboardScreen()
}
