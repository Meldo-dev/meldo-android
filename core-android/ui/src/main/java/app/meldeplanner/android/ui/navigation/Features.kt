package app.meldeplanner.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.model.AppModel

// The feature registry. Every feature module exposes one or more MeldoFeature values; the
// app holds the single list (MeldoFeatures.kt in :app) and everything that shows places
// (the bar, the tablet sidebar, Mehr, Einstellungen) iterates it. Removing a module from
// the build means deleting its line there and its Gradle dependency.

/** Where a feature shows up in the navigation. */
enum class Placement {
    /** Always in the bar, in list order (Start, Stundenplan). */
    bar,

    /** A school place: one of the bar's two flexible places ([MeldoFeature.barChoice]) or under Mehr / Schule. */
    school,

    /** A connected service (WebUntis, Teams, Google Drive): under Mehr / Verbindungen. */
    connections,

    /** About the app (Fächer, Einstellungen, Status, Über Meldo): under Mehr / App. */
    app,

    /** Mehr: the phone's last tab, listing every place outside the bar. Tablets show the sidebar instead. */
    hub,

    /** Not a place: only contributes (the Melden button, the first-run tour). */
    none,
}

/** One module's entry in the app: its page, where it sits, and what it adds to other screens. */
interface MeldoFeature {
    /** Stable id; also the raw value stored in Preferences.barTabs and startTab. */
    val id: String

    /** Localized title (read it during composition, so it follows the app language). */
    val title: String

    /** SF Symbol name, mapped by sfSymbol(). */
    val symbol: String

    val placement: Placement

    /** Can take one of the bar's two flexible places (and be the opening screen). */
    val barChoice: Boolean get() = false

    /** Its row under Mehr when it reads differently from the sidebar entry. */
    val menuTitle: String get() = title

    /** Shown at all for this student (Teams only once switched on). */
    fun isAvailable(model: AppModel): Boolean = true

    /** A short state next to its row under Mehr, e.g. "An". */
    fun status(model: AppModel): String? = null

    /** Needs the student: a badge on Mehr and an orange [status]. */
    fun needsAttention(model: AppModel): Boolean = false

    /** The feature's main screen. */
    @Composable
    fun Page() {}

    /** A screen another feature asks for; `null` when this feature doesn't offer it. */
    fun screen(link: Link): (@Composable () -> Unit)? = null

    /** An action on the bar places' root screens (Melden); [inBar]: in the phone bar's accessory. */
    val action: (@Composable (inBar: Boolean) -> Unit)? get() = null

    /** Drawn above everything (the first-run tour decides itself when to show). */
    @Composable
    fun Overlay() {}

    /** Its own section in Einstellungen (a FormSection), after the built-in ones. */
    val settings: (@Composable () -> Unit)? get() = null

    /** Starts what runs without a screen (sync, background updates). Called once at app start. */
    fun install(model: AppModel) {}
}

/** A screen one feature opens in another; it only renders when a feature in the list answers. */
sealed interface Link {
    /** A feature's main page by its id. [onDone]: after the student finished there (e.g. connected WebUntis). */
    data class Page(val id: String, val onDone: (() -> Unit)? = null) : Link

    data class NewTask(val kind: TaskKind, val subjectID: String? = null) : Link

    /** [onDone] runs after the editor closed. */
    data class NewSubject(val onDone: (() -> Unit)? = null) : Link

    data class NewLesson(val weekday: Int) : Link
}

/** The app's feature list. */
class Features(val all: List<MeldoFeature>) {
    operator fun get(id: String): MeldoFeature? = all.firstOrNull { it.id == id }

    fun has(id: String): Boolean = get(id) != null

    fun placed(placement: Placement): List<MeldoFeature> = all.filter { it.placement == placement }

    /** The screen for [link]: a page, or a screen a feature offers. */
    fun screen(link: Link): (@Composable () -> Unit)? = when (link) {
        is Link.Page -> link.onDone?.let { all.firstNotNullOfOrNull { f -> f.screen(link) } }
            ?: get(link.id)?.let { feature -> @Composable { feature.Page() } }
        else -> all.firstNotNullOfOrNull { it.screen(link) }
    }
}

val LocalFeatures = staticCompositionLocalOf { Features(emptyList()) }

/** Opens [link] on the current navigation stack; `null` (don't show the button) when no feature offers it. */
@Composable
fun rememberLink(link: Link): (() -> Unit)? {
    val screen = LocalFeatures.current.screen(link) ?: return null
    val navigator = LocalNavigator.current
    return { navigator.push(screen) }
}
