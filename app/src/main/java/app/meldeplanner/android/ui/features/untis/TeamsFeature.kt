package app.meldeplanner.android.ui.features.untis

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.installTeams
import app.meldeplanner.android.model.setTeamsEnabled
import app.meldeplanner.android.model.teamsAvailable
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.SwitchRow

/** Microsoft Teams: assignments become homework. Off until switched on in Einstellungen › Erweitert. */
object TeamsFeature : MeldoFeature {
    override val id = "teams"
    override val title get() = L("Microsoft Teams")
    override val symbol = "person.3"
    override val placement = Placement.connections

    override fun isAvailable(model: AppModel) = model.teamsAvailable

    @Composable
    override fun Page() = TeamsConnectScreen()

    override val settings: @Composable () -> Unit = {
        val model = LocalAppModel.current
        FormSection(
            header = L("Erweitert"),
            footer = L("Schalte das nur ein, wenn deine Schule Meldo für Teams freigegeben hat. Viele Schulen müssen das erst erlauben."),
        ) {
            SwitchRow(L("Microsoft Teams"), model.preferences.teamsEnabled) { model.setTeamsEnabled(it) }
        }
    }

    override fun install(model: AppModel) = model.installTeams()
}
