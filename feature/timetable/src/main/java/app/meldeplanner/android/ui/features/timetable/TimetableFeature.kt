package app.meldeplanner.android.ui.features.timetable

import androidx.compose.runtime.Composable
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement

/** Stundenplan: the week grid and the lesson editor. */
object TimetableFeature : MeldoFeature {
    override val id = "timetable"
    override val title get() = L("Stundenplan")
    override val symbol = "tablecells"
    override val placement = Placement.bar

    @Composable
    override fun Page() = TimetableScreen()

    override fun screen(link: Link): (@Composable () -> Unit)? = when (link) {
        is Link.NewLesson -> {
            {
                val navigator = LocalNavigator.current
                LessonEditorScreen(lesson = null, weekday = link.weekday, onDismiss = { navigator.pop() })
            }
        }
        else -> null
    }
}
