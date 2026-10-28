package app.meldeplanner.android.surfaces

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.surfaces.StatusScreen
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Status: whether widgets, notifications, alarms and connections work. */
object StatusFeature : MeldoFeature {
    override val id = "status"
    override val title get() = L("Status")
    override val menuTitle get() = L("Status: Widgets, Mitteilungen")
    override val symbol = "stethoscope"
    override val placement = Placement.app

    @Composable
    override fun Page() = StatusScreen()
}
