package app.meldeplanner.android.ui.navigation

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.NavigationBarItem
import app.meldeplanner.android.model.teamsAvailable
import app.meldeplanner.android.model.drivePasswordMissing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.onSizeChanged
import app.meldeplanner.android.ui.theme.LocalBottomChrome
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.LocalChromeHaze
import app.meldeplanner.android.ui.theme.meldoChrome
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.SectionHeader
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.calendar.CalendarScreen
import app.meldeplanner.android.ui.features.dashboard.DashboardScreen
import app.meldeplanner.android.ui.features.exams.ExamListScreen
import app.meldeplanner.android.ui.features.grades.GradesScreen
import app.meldeplanner.android.ui.features.more.AboutScreen
import app.meldeplanner.android.ui.features.more.GoogleDriveScreen
import app.meldeplanner.android.ui.features.onboarding.OnboardingScreen
import app.meldeplanner.android.ui.features.more.MoreScreen
import app.meldeplanner.android.ui.features.more.SettingsScreen
import app.meldeplanner.android.ui.features.more.StatusScreen
import app.meldeplanner.android.ui.features.participation.MeldenButton
import app.meldeplanner.android.ui.features.participation.SubjectPickerSheet
import app.meldeplanner.android.ui.features.statistics.StatisticsScreen
import app.meldeplanner.android.ui.features.subjects.SubjectListScreen
import app.meldeplanner.android.ui.features.tasks.TaskListScreen
import app.meldeplanner.android.ui.features.timetable.HolidayListScreen
import app.meldeplanner.android.ui.features.timetable.TimetableScreen
import app.meldeplanner.android.ui.features.untis.AbsencesScreen
import app.meldeplanner.android.ui.features.untis.TeamsConnectScreen
import app.meldeplanner.android.ui.features.untis.UntisConnectScreen

enum class AppTab {
    home, timetable, calendar, tasks, exams, statistics, more,
    absences, holidays, untis, teams, drive, subjects, settings, status, about, grades,
}

private data class Destination(val tab: AppTab, val title: () -> String, val symbol: String)

private val sidebarConnections = listOf(
    Destination(AppTab.untis, { L("WebUntis") }, "arrow.triangle.2.circlepath"),
    Destination(AppTab.teams, { L("Microsoft Teams") }, "person.3"),
    Destination(AppTab.drive, { L("Google Drive") }, "externaldrive"),
)

private val sidebarApp = listOf(
    Destination(AppTab.subjects, { L("Fächer") }, "books.vertical"),
    Destination(AppTab.settings, { L("Einstellungen") }, "gearshape"),
    Destination(AppTab.status, { L("Status") }, "stethoscope"),
    Destination(AppTab.about, { L("Über Meldo") }, "info.circle"),
)

/** Something the app was opened for (widget, tile, notification, shortcut). */
enum class DeepLinkTarget { melden, homework }

/**
 * Main navigation, port of RootView.swift. Phones show five tabs (the rest lives
 * under Mehr); wide screens (tablets, unfolded foldables) show a permanent drawer
 * with every destination, grouped into Schule and App, like the iPad sidebar. The
 * floating Melden button only appears on Start and Stundenplan.
 */
@Composable
fun RootScreen(model: AppModel, deepLink: DeepLinkTarget?, onDeepLinkHandled: () -> Unit) {
    // The screen chosen under Einstellungen › Leiste und Startseite (tablets have every place).
    val openWide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    var tab by rememberSaveable { mutableStateOf(startTab(model.preferences, openWide)) }
    var pickingSubject by remember { mutableStateOf(false) }
    val navigators = remember { mutableMapOf<AppTab, Navigator>() }
    fun navigator(t: AppTab) = navigators.getOrPut(t) { Navigator() }

    LaunchedEffect(deepLink) {
        when (deepLink) {
            // Any app can send this link, so it never records by itself: the student picks
            // the subject. Widgets, tile and notifications record through LogParticipation.
            DeepLinkTarget.melden -> {
                tab = AppTab.home
                pickingSubject = true
            }
            DeepLinkTarget.homework -> tab = AppTab.tasks
            null -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }

    // No focused text field (and keyboard) waiting when the student comes back to Meldo.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { focusManager.clearFocus() }

    val chromeHaze = dev.chrisbanes.haze.rememberHazeState()
    CompositionLocalProvider(LocalAppModel provides model, LocalChromeHaze provides chromeHaze) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            // Sidebar-only destinations have no phone tab; land on Mehr instead (and back).
            // Also when the student moves a place out of the bar while it is open.
            val bar = phoneBar(model.preferences)
            LaunchedEffect(wide, bar) {
                if (!wide && tab !in bar) tab = AppTab.more
                if (wide && tab == AppTab.more) tab = AppTab.settings
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    PermanentDrawerSheet(Modifier.width(280.dp).fillMaxHeight()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = DS.spacingM)) {
                            Text(
                                "Meldo", style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(DS.spacingL),
                            )
                            // The bar follows Einstellungen › Leiste und Startseite; the rest is under Schule.
                            tabletBar(model.preferences).forEach { DrawerItem(Destination(it, { it.title }, it.symbol), tab) { t -> tab = t } }
                            SectionHeader(L("Schule"))
                            placesOutsideBar(model.preferences, wide = true).forEach { DrawerItem(Destination(it, { it.title }, it.symbol), tab) { t -> tab = t } }
                            SectionHeader(L("Verbindungen"))
                            sidebarConnections.filter { it.tab != AppTab.teams || model.teamsAvailable }.forEach { DrawerItem(it, tab) { t -> tab = t } }
                            SectionHeader(L("App"))
                            sidebarApp.forEach { DrawerItem(it, tab) { t -> tab = t } }
                        }
                    }
                    val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding()
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalBottomChrome provides navInset) { TabContent(tab, navigator(tab)) }
                    }
                }
            } else {
                // A floating, rounded bar like the iOS tab bar: translucent (no glass effect),
                // content scrolls under it, the selection stays inside the bar.
                val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding()
                // Melden sits in the bar's accessory on Start and Stundenplan (iOS: tabViewBottomAccessory),
                // not before the first subject.
                val melden = (tab == AppTab.home || tab == AppTab.timetable) && navigator(tab).depth == 0 &&
                    (model.subjects.isNotEmpty() || model.data.untis != null)
                // Like iOS' tabBarMinimizeBehavior(.onScrollDown): the bar shrinks while scrolling down.
                var minimized by remember { mutableStateOf(false) }
                LaunchedEffect(tab) { minimized = false }
                val scrollWatch = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPreScroll(
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                        ): androidx.compose.ui.geometry.Offset {
                            if (available.y < -6f) minimized = true else if (available.y > 6f) minimized = false
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                val chrome = navInset + DS.spacingS * 2 + FloatingBarHeight + if (melden) AccessoryHeight + DS.spacingS else 0.dp
                Box(Modifier.fillMaxSize().nestedScroll(scrollWatch)) {
                    CompositionLocalProvider(LocalBottomChrome provides chrome) {
                        TabContent(tab, navigator(tab), floatingMelden = false)
                    }
                    // One morph on a spring, like iOS: the bar narrows into a circle with the selected tab
                    // while Melden slides down beside it and widens into the freed space (and back).
                    val p by androidx.compose.animation.core.animateFloatAsState(
                        if (minimized) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "tab-bar",
                    )
                    val places = phoneBar(model.preferences).map { Destination(it, { it.title }, it.symbol) }
                    BoxWithConstraints(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = navInset + DS.spacingS, start = DS.spacingL, end = DS.spacingL)
                            .height(FloatingBarHeight + if (melden) AccessoryHeight + DS.spacingS else 0.dp),
                    ) {
                        val full = maxWidth
                        // The circle plus the gap: how far Melden moves right, and how far above the bar it sits.
                        val inline = FloatingBarHeight + DS.spacingS
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .width(androidx.compose.ui.unit.lerp(full, FloatingBarHeight, p))
                                .height(FloatingBarHeight)
                                .floatingSurface(CircleShape),
                        ) {
                            if (!minimized || p < 0.99f) {
                                // Laid out at full width and clipped by the narrowing bar, fading out.
                                FloatingTabBar(
                                    places = places,
                                    selected = tab,
                                    badgeOn = { it == AppTab.more && model.drivePasswordMissing },
                                    modifier = Modifier
                                        .wrapContentWidth(Alignment.Start, unbounded = true)
                                        .width(full)
                                        .alpha((1f - p * 2f).coerceIn(0f, 1f)),
                                ) { picked ->
                                    // Tapping the selected tab again goes back to its root, like iOS.
                                    if (tab == picked) navigator(picked).popToRoot() else tab = picked
                                }
                            }
                            if (minimized || p > 0.01f) {
                                // The selected tab as a small circle; tapping it brings the full bar back.
                                val current = places.firstOrNull { it.tab == tab } ?: places.first()
                                Box(
                                    Modifier
                                        .size(FloatingBarHeight)
                                        .alpha(((p - 0.5f) * 2f).coerceIn(0f, 1f))
                                        .then(if (minimized) Modifier.clickable { minimized = false } else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(sfSymbol(current.symbol), current.title(), tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                        // Drawn last: Melden passes in front of the narrowing bar.
                        if (melden) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = androidx.compose.ui.unit.lerp(0.dp, inline, p), y = -androidx.compose.ui.unit.lerp(inline, 0.dp, p))
                                    .width(androidx.compose.ui.unit.lerp(full, full - inline, p))
                                    .height(androidx.compose.ui.unit.lerp(AccessoryHeight, FloatingBarHeight, p))
                                    .floatingSurface(CircleShape),
                            ) { MeldenButton(inAccessory = true) }
                        }
                    }
                }
            }
            model.storageProblem?.let { StorageBanner(it, Modifier.align(Alignment.TopCenter)) }
        }
        if (model.showingOnboarding) OnboardingScreen()
        if (pickingSubject) {
            SubjectPickerSheet(onDismiss = { pickingSubject = false }) { subjectID ->
                model.recordParticipation(subjectID)
                pickingSubject = false
            }
        }
    }
}

@Composable
private fun DrawerItem(d: Destination, selected: AppTab, select: (AppTab) -> Unit) {
    NavigationDrawerItem(
        label = { Text(d.title()) },
        icon = { Icon(sfSymbol(d.symbol), null) },
        selected = selected == d.tab,
        onClick = { select(d.tab) },
    )
}

/** One tab's content in its own navigation stack; Start and Stundenplan get the Melden button. */
@Composable
private fun TabContent(tab: AppTab, navigator: Navigator, floatingMelden: Boolean = true) {
    CompositionLocalProvider(LocalNavigator provides navigator) {
        // Phones show Melden in the bar's accessory instead of a floating button.
        val melden = floatingMelden && (tab == AppTab.home || tab == AppTab.timetable)
        val bottomChrome = LocalBottomChrome.current
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMeldenClearance provides melden) { navigator.Content {
                when (tab) {
                    AppTab.home -> DashboardScreen()
                    AppTab.timetable -> TimetableScreen()
                    AppTab.tasks -> TaskListScreen()
                    AppTab.exams -> ExamListScreen()
                    AppTab.more -> MoreScreen()
                    AppTab.calendar -> CalendarScreen()
                    AppTab.statistics -> StatisticsScreen()
                    AppTab.grades -> GradesScreen()
                    AppTab.absences -> AbsencesScreen()
                    AppTab.holidays -> HolidayListScreen()
                    AppTab.untis -> UntisConnectScreen()
                    AppTab.teams -> TeamsConnectScreen()
                    AppTab.drive -> GoogleDriveScreen()
                    AppTab.subjects -> SubjectListScreen()
                    AppTab.settings -> SettingsScreen()
                    AppTab.status -> StatusScreen()
                    AppTab.about -> AboutScreen()
                }
            } }
            // Only on the tab's root screen, never on screens pushed from it; above the navigation bar.
            if (melden && navigator.depth == 0) {
                Box(Modifier.align(Alignment.BottomEnd).padding(end = DS.spacingL, bottom = bottomChrome + DS.spacingL)) { MeldenButton() }
            }
        }
    }
}

@Composable
private fun StorageBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.statusBarsPadding().padding(horizontal = DS.spacingL),
        shape = DS.rowShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(Modifier.padding(DS.spacingM), verticalAlignment = Alignment.CenterVertically) {
            Icon(sfSymbol("exclamationmark.triangle.fill"), null)
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = DS.spacingS))
        }
    }
}

private val FloatingBarHeight = 64.dp
private val AccessoryHeight = 48.dp

/** The translucent floating material of the tab bar and its accessory (no glass effect). */
@Composable
private fun Modifier.floatingSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .shadow(12.dp, shape, clip = false)
        .clip(shape)
        .background(colors.surfaceContainerHigh.copy(alpha = 0.86f))
        .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
}

/** The phone tab bar's tabs; the caller draws the floating pill around them. */
@Composable
private fun FloatingTabBar(
    places: List<Destination>,
    selected: AppTab,
    badgeOn: (AppTab) -> Boolean,
    modifier: Modifier = Modifier,
    select: (AppTab) -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .height(FloatingBarHeight)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        places.forEach { place ->
            val on = place.tab == selected
            val tint = if (on) colors.primary else colors.onSurface
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (on) colors.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { select(place.tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.BadgedBox(
                    badge = { if (badgeOn(place.tab)) androidx.compose.material3.Badge(containerColor = StateColors.orange()) },
                ) { Icon(sfSymbol(place.symbol), null, tint = tint, modifier = Modifier.size(24.dp)) }
                Text(
                    place.title(), color = tint, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                )
            }
        }
    }
}
