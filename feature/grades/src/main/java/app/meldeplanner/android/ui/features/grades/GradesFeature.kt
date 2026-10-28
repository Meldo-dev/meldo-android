package app.meldeplanner.android.ui.features.grades

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.GradeScaleChoice
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.NavRow

/** Noten: the grade calculator (Notenrechner) and the grade scale in Einstellungen. */
object GradesFeature : MeldoFeature {
    override val id = "grades"
    override val title get() = L("Noten")
    override val symbol = "graduationcap"
    override val placement = Placement.school
    override val barChoice = true

    @Composable
    override fun Page() = GradesScreen()

    override val settings: @Composable () -> Unit = { GradeScaleSection() }
}

@Composable
private fun GradeScaleSection() {
    val model = LocalAppModel.current
    var editing by remember { mutableStateOf(false) }
    FormSection(header = L("Noten")) {
        NavRow(
            L("Notensystem"), sfSymbol("slider.horizontal.3"),
            trailing = if (model.preferences.gradeScale == GradeScaleChoice.points) L("Punkte 0–15") else L("Noten 1–6"),
        ) { editing = true }
    }
    if (editing) GradeSettingsSheet(onDismiss = { editing = false })
}
