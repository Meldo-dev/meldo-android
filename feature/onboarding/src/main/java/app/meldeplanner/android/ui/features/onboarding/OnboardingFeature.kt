package app.meldeplanner.android.ui.features.onboarding

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** The first-run tour; "Einführung ansehen" opens it again. */
object OnboardingFeature : MeldoFeature {
    override val id = "onboarding"
    override val title get() = L("Einführung ansehen")
    override val symbol = "sparkles"
    override val placement = Placement.none

    @Composable
    override fun Overlay() {
        if (LocalAppModel.current.showingOnboarding) OnboardingScreen()
    }
}
