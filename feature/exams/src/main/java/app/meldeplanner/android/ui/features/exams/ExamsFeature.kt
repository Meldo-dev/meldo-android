package app.meldeplanner.android.ui.features.exams

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Dokumente: scanned and imported exams and worksheets. */
object ExamsFeature : MeldoFeature {
    override val id = "exams"
    override val title get() = L("Dokumente")
    override val symbol = "doc.on.doc"
    override val placement = Placement.school
    override val barChoice = true

    @Composable
    override fun Page() = ExamListScreen()
}
