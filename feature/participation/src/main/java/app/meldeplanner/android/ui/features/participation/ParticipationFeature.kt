package app.meldeplanner.android.ui.features.participation

import androidx.compose.runtime.Composable
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Melden: the one-tap button on Start and Stundenplan. */
object ParticipationFeature : MeldoFeature {
    override val id = "participation"
    override val title = "Melden"
    override val symbol = "hand.raised"
    override val placement = Placement.none

    override val action: @Composable (Boolean) -> Unit = { inBar -> MeldenButton(inAccessory = inBar) }
}
