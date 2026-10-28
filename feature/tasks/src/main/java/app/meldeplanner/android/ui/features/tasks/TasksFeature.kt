package app.meldeplanner.android.ui.features.tasks

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Aufgaben: homework and to-dos. */
object TasksFeature : MeldoFeature {
    override val id = "tasks"
    override val title get() = L("Aufgaben")
    override val symbol = "checklist"
    override val placement = Placement.school
    override val barChoice = true

    @Composable
    override fun Page() = TaskListScreen()

    override fun screen(link: Link): (@Composable () -> Unit)? = when (link) {
        is Link.NewTask -> {
            {
                val navigator = LocalNavigator.current
                TaskEditorScreen(task = null, kind = link.kind, subjectID = link.subjectID, onDismiss = { navigator.pop() })
            }
        }
        else -> null
    }
}
