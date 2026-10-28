package app.meldeplanner.android.ui.features.drive

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.DriveState
import app.meldeplanner.android.model.drivePasswordMissing
import app.meldeplanner.android.model.driveState
import app.meldeplanner.android.model.installGoogleDrive
import app.meldeplanner.android.ui.features.drive.GoogleDriveScreen
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Google Drive: the encrypted backup and sync between the student's devices. */
object DriveFeature : MeldoFeature {
    override val id = "drive"
    override val title get() = L("Google Drive")
    override val symbol = "externaldrive"
    override val placement = Placement.connections

    override fun status(model: AppModel): String = when (model.driveState) {
        DriveState.on -> L("An")
        DriveState.needsPassword -> L("Passwort fehlt")
        else -> L("Aus")
    }

    override fun needsAttention(model: AppModel) = model.drivePasswordMissing

    @Composable
    override fun Page() = GoogleDriveScreen()

    override fun install(model: AppModel) = model.installGoogleDrive()
}
