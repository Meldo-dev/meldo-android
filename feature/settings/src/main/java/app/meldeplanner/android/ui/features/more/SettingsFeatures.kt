package app.meldeplanner.android.ui.features.more

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.navigation.Placement
import app.meldeplanner.android.ui.rows.EmptyState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box

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

/**
 * WebUntis under Verbindungen: the import waits for Untis to approve Meldo as an
 * integration partner, so this build only says so. Data imported earlier stays.
 */
object UntisFeature : MeldoFeature {
    override val id = "untis"
    override val title get() = L("WebUntis")
    override val symbol = "arrow.triangle.2.circlepath"
    override val placement = Placement.connections

    @Composable
    override fun Page() = MeldoScreen(L("WebUntis")) { padding ->
        Box(Modifier.padding(padding)) {
            EmptyState(
                "hourglass",
                L("Wartet auf Freigabe durch Untis"),
                L("In dieser Version und in den kommenden Versionen nicht verfügbar, bis Untis die Anbindung freigibt. Bis dahin trägst du deinen Stundenplan selbst ein."),
            )
        }
    }
}
