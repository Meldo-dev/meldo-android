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
import app.meldeplanner.android.ui.navigation.LocalTabReselections
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
import app.meldeplanner.android.ui.components.SubjectPickerSheet

/** Something the app was opened for (widget, tile, notification, shortcut). */
enum class DeepLinkTarget { melden, homework }

/**
 * Main navigation, port of RootView.swift. Phones show five tabs (the rest lives
 * under Mehr); wide screens (tablets, unfolded foldables) show a permanent drawer
 * with every destination, grouped into Schule, Verbindungen and App, like the iPad
 * sidebar. Every place comes from [features]; the floating Melden button (a feature's
 * action) only appears on the bar's fixed places, Start and Stundenplan.
 */
@Composable
fun RootScreen(model: AppModel, features: Features, deepLink: DeepLinkTarget?, onDeepLinkHandled: () -> Unit) {
    // The screen chosen under Einstellungen › Leiste und Startseite (tablets have every place).
    val openWide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    var tab by rememberSaveable { mutableStateOf(features.startTab(model.preferences, openWide)?.id.orEmpty()) }
    val reselections = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var pickingSubject by remember { mutableStateOf(false) }
    val navigators = remember { mutableMapOf<String, Navigator>() }
    fun navigator(t: String) = navigators.getOrPut(t) { Navigator() }
    val home = features.fixedBar.firstOrNull()?.id
    val action = features.all.firstNotNullOfOrNull { it.action }
    fun hasAction(t: String) = action != null && features[t]?.placement == Placement.bar

    LaunchedEffect(deepLink) {
        when (deepLink) {
            // Any app can send this link, so it never records by itself: the student picks
            // the subject. Widgets, tile and notifications record through LogParticipation.
            DeepLinkTarget.melden -> {
                home?.let { tab = it }
                pickingSubject = true
            }
            DeepLinkTarget.homework -> if (features.has("tasks")) tab = "tasks"
            null -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }

    // No focused text field (and keyboard) waiting when the student comes back to Meldo.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { focusManager.clearFocus() }

    val chromeHaze = dev.chrisbanes.haze.rememberHazeState()
    CompositionLocalProvider(LocalAppModel provides model, LocalFeatures provides features, LocalChromeHaze provides chromeHaze) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            // Sidebar-only destinations have no phone tab; land on Mehr instead (and back).
            // Also when the student moves a place out of the bar while it is open.
            val bar = features.phoneBar(model.preferences).map { it.id }
            val hub = features.hub?.id
            LaunchedEffect(wide, bar) {
                if (!wide && tab !in bar) (hub ?: bar.firstOrNull())?.let { tab = it }
                if (wide && tab == hub) (features["settings"] ?: features.placed(Placement.app).firstOrNull())?.let { tab = it.id }
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
                            features.tabletBar(model.preferences).forEach { DrawerItem(it, tab) { t -> if (t == tab) reselections[t] = (reselections[t] ?: 0) + 1 else tab = t } }
                            val sections = listOf(
                                L("Schule") to features.placesOutsideBar(model.preferences, wide = true),
                                L("Verbindungen") to features.placed(Placement.connections),
                                L("App") to features.placed(Placement.app),
                            )
                            for ((header, places) in sections) {
                                val shown = places.filter { it.isAvailable(model) }
                                if (shown.isEmpty()) continue
                                SectionHeader(header)
                                shown.forEach { DrawerItem(it, tab) { t -> if (t == tab) reselections[t] = (reselections[t] ?: 0) + 1 else tab = t } }
                            }
                        }
                    }
                    val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding()
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalBottomChrome provides navInset) { TabContent(features[tab], navigator(tab), action.takeIf { hasAction(tab) }, reselections[tab] ?: 0) }
                    }
                }
            } else {
                // A floating, rounded bar like the iOS tab bar: translucent (no glass effect),
                // content scrolls under it, the selection stays inside the bar.
                val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding()
                // Melden sits in the bar's accessory on Start and Stundenplan (iOS: tabViewBottomAccessory),
                // not before the first subject.
                val melden = hasAction(tab) && navigator(tab).depth == 0 &&
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
                        TabContent(features[tab], navigator(tab), action = null, reselections = reselections[tab] ?: 0)
                    }
                    // One morph on a spring, like iOS: the bar narrows into a circle with the selected tab
                    // while Melden slides down beside it and widens into the freed space (and back).
                    val p by androidx.compose.animation.core.animateFloatAsState(
                        if (minimized) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "tab-bar",
                    )
                    val places = features.phoneBar(model.preferences)
                    // Mehr carries a badge when a place under it needs the student (e.g. the Drive password).
                    val attention = features.all.any { it.placement != Placement.bar && it.needsAttention(model) }
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
                                    badgeOn = { it.placement == Placement.hub && attention },
                                    modifier = Modifier
                                        .wrapContentWidth(Alignment.Start, unbounded = true)
                                        .width(full)
                                        .alpha((1f - p * 2f).coerceIn(0f, 1f)),
                                ) { picked ->
                                    // Tapping the selected tab again goes back to its root, like iOS.
                                    if (tab == picked) {
                                        navigator(picked).popToRoot()
                                        reselections[picked] = (reselections[picked] ?: 0) + 1
                                    } else {
                                        tab = picked
                                    }
                                }
                            }
                            if (minimized || p > 0.01f) {
                                // The selected tab as a small circle; tapping it brings the full bar back.
                                val current = places.firstOrNull { it.id == tab } ?: places.first()
                                Box(
                                    Modifier
                                        .size(FloatingBarHeight)
                                        .alpha(((p - 0.5f) * 2f).coerceIn(0f, 1f))
                                        .then(if (minimized) Modifier.clickable { minimized = false } else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(sfSymbol(current.symbol), current.title, tint = MaterialTheme.colorScheme.primary) }
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
                            ) { action?.invoke(true) }
                        }
                    }
                }
            }
            model.storageProblem?.let { StorageBanner(it, Modifier.align(Alignment.TopCenter)) }
        }
        features.all.forEach { it.Overlay() }
        if (pickingSubject) {
            SubjectPickerSheet(onDismiss = { pickingSubject = false }) { subjectID ->
                model.recordParticipation(subjectID)
                pickingSubject = false
            }
        }
    }
}

@Composable
private fun DrawerItem(feature: MeldoFeature, selected: String, select: (String) -> Unit) {
    NavigationDrawerItem(
        label = { Text(feature.title) },
        icon = { Icon(sfSymbol(feature.symbol), null) },
        selected = selected == feature.id,
        onClick = { select(feature.id) },
    )
}

/**
 * One tab's content in its own navigation stack. [action]: the floating Melden button on
 * Start and Stundenplan (phones show it in the bar's accessory instead).
 */
@Composable
private fun TabContent(feature: MeldoFeature?, navigator: Navigator, action: (@Composable (Boolean) -> Unit)?, reselections: Int) {
    CompositionLocalProvider(LocalNavigator provides navigator, LocalTabReselections provides reselections) {
        val bottomChrome = LocalBottomChrome.current
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMeldenClearance provides (action != null)) { navigator.Content { feature?.Page() } }
            // Only on the tab's root screen, never on screens pushed from it; above the navigation bar.
            if (action != null && navigator.depth == 0) {
                Box(Modifier.align(Alignment.BottomEnd).padding(end = DS.spacingL, bottom = bottomChrome + DS.spacingL)) { action(false) }
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
    places: List<MeldoFeature>,
    selected: String,
    badgeOn: (MeldoFeature) -> Boolean,
    modifier: Modifier = Modifier,
    select: (String) -> Unit,
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
            val on = place.id == selected
            val tint = if (on) colors.primary else colors.onSurface
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (on) colors.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { select(place.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.BadgedBox(
                    badge = { if (badgeOn(place)) androidx.compose.material3.Badge(containerColor = StateColors.orange()) },
                ) { Icon(sfSymbol(place.symbol), null, tint = tint, modifier = Modifier.size(24.dp)) }
                Text(
                    place.title, color = tint, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                )
            }
        }
    }
}
