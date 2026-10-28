package app.meldeplanner.android.ui.features.more

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Mehr: the phone's last tab with every place outside the bar. */
object MoreFeature : MeldoFeature {
    override val id = "more"
    override val title get() = L("Mehr")
    override val symbol = "ellipsis.circle"
    override val placement = Placement.hub

    @Composable
    override fun Page() = MoreScreen()
}

object SettingsFeature : MeldoFeature {
    override val id = "settings"
    override val title get() = L("Einstellungen")
    override val symbol = "gearshape"
    override val placement = Placement.app

    @Composable
    override fun Page() = SettingsScreen()
}

object AboutFeature : MeldoFeature {
    override val id = "about"
    override val title get() = L("Über Meldo")
    override val symbol = "info.circle"
    override val placement = Placement.app

    @Composable
    override fun Page() = AboutScreen()
}
