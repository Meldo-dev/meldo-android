package app.meldeplanner.android.ui.features.subjects

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Fächer: the subject list and editor. */
object SubjectsFeature : MeldoFeature {
    override val id = "subjects"
    override val title get() = L("Fächer")
    override val symbol = "books.vertical"
    override val placement = Placement.app

    @Composable
    override fun Page() = SubjectListScreen()

    override fun screen(link: Link): (@Composable () -> Unit)? = when (link) {
        is Link.NewSubject -> {
            {
                val navigator = LocalNavigator.current
                SubjectEditorScreen(subject = null, onDismiss = {
                    navigator.pop()
                    link.onDone?.invoke()
                })
            }
        }
        else -> null
    }
}
