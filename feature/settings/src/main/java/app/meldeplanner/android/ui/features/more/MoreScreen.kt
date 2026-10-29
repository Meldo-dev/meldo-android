@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.more

import app.meldeplanner.android.ui.theme.MeldoSheet
import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.AppIconChoice
import app.meldeplanner.android.data.AppLanguage
import app.meldeplanner.android.data.AppTheme
import app.meldeplanner.android.data.AppearanceChoice
import app.meldeplanner.android.data.CardSize
import app.meldeplanner.android.data.CardSlot
import app.meldeplanner.android.data.DashboardCard
import app.meldeplanner.android.data.defaultHomeLayout
import app.meldeplanner.android.core.Notifications
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoMenu
import app.meldeplanner.android.ui.components.MeldoMenuItem
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.model.showOnboardingAgain
import app.meldeplanner.android.ui.navigation.LocalFeatures
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.navigation.Placement
import app.meldeplanner.android.ui.navigation.barChoices
import app.meldeplanner.android.ui.navigation.barSlots
import app.meldeplanner.android.ui.navigation.fixedBar
import app.meldeplanner.android.ui.navigation.placesOutsideBar
import app.meldeplanner.android.ui.navigation.startTab
import app.meldeplanner.android.ui.theme.brandColor
import app.meldeplanner.android.ui.theme.dynamicAccentPreview
import uniffi.melde_ffi.coreVersion
import app.meldeplanner.android.ui.rows.meldoAnimateItem
import app.meldeplanner.android.ui.rows.formItems
import app.meldeplanner.android.ui.rows.ValueRow
import app.meldeplanner.android.ui.rows.SwitchRow
import app.meldeplanner.android.ui.rows.StepperRow
import app.meldeplanner.android.ui.rows.SheetBar
import app.meldeplanner.android.ui.rows.NoteRow
import app.meldeplanner.android.ui.rows.NavRow
import app.meldeplanner.android.ui.rows.IconTile
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.FormItemSurface
import app.meldeplanner.android.ui.rows.ChoiceRow
import app.meldeplanner.android.ui.rows.ActionRow
import app.meldeplanner.android.ui.components.AppIcons

// Port of meldo-ios/MeldePlanner/Features/More/MoreView.swift: the Mehr tab,
// Einstellungen, Über Meldo, the Start card list and the app icon picker.

/** The phone's Mehr tab: every place outside the bar. Wide screens list them in the drawer. */
@Composable
fun MoreScreen() {
    val navigator = LocalNavigator.current
    val model = LocalAppModel.current
    val features = LocalFeatures.current
    // On phones Kalender and Statistik have no tab of their own (the drawer shows them on wide screens).
    val compact = LocalConfiguration.current.screenWidthDp < 840
    val sections = listOf(
        // Everything that isn't in the bar (the student picks the bar's two places), then Ferien.
        Triple("school", L("Schule"), features.placesOutsideBar(model.preferences, wide = !compact)),
        Triple("connections", L("Verbindungen"), features.placed(Placement.connections)),
        Triple("app", L("App"), features.placed(Placement.app)),
    )
    MeldoScreen(L("Mehr")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            for ((key, header, places) in sections) {
                val shown = places.filter { it.isAvailable(model) }
                val tour = key == "app" && features.has("onboarding")
                if (shown.isEmpty() && !tour) continue
                item(key = key) {
                    FormSection(header = header) {
                        shown.forEach { PlaceRow(it) }
                        if (tour) ActionRow(L("Einführung ansehen"), symbol = "sparkles") { model.showOnboardingAgain() }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

@Composable
private fun PlaceRow(place: MeldoFeature) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    NavRow(
        place.menuTitle, sfSymbol(place.symbol),
        trailing = place.status(model),
        trailingAccent = if (place.needsAttention(model)) StateColors.orange() else null,
    ) { navigator.push { place.Page() } }
}

@Composable
fun SettingsScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val p = model.preferences
    var editingGoal by remember { mutableStateOf(false) }
    val features = LocalFeatures.current
    var notificationsDenied by remember { mutableStateOf(false) }
    val requestNotifications = Notifications.rememberPermissionRequester { allowed ->
        notificationsDenied = !allowed
        model.setAskAfterLesson(allowed)
    }

    MeldoScreen(L("Einstellungen")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "language") {
                FormSection(header = L("Sprache")) {
                    ChoiceRow(
                        title = null,
                        options = AppLanguage.entries.map { it to it.nativeName },
                        selected = p.language,
                        onSelect = { model.setLanguage(it) },
                    )
                }
            }
            item(key = "customize") {
                FormSection(header = L("Anpassen")) {
                    SwitchRow(L("Meldo anzeigen"), p.showMascot) { model.setShowMascot(it) }
                    SwitchRow(L("Haptisches Feedback"), p.haptics) { model.setHaptics(it) }
                    NavRow(L("Startseite anpassen"), sfSymbol("rectangle.3.group")) { navigator.push { DashboardCardsScreen() } }
                    NavRow(L("Leiste und Startseite"), sfSymbol("dock.rectangle")) { navigator.push { BarSettingsScreen() } }
                    NavRow(L("App-Symbol"), sfSymbol("app.badge")) { navigator.push { AppIconPickerScreen() } }
                }
            }
            item(key = "design") {
                // Meldo is always orange: only the appearance is a choice.
                FormSection(header = L("Design")) {
                    ChoiceRow(
                        title = null,
                        options = AppearanceChoice.entries.map { it to appearanceName(it) },
                        selected = p.appearance,
                        onSelect = { model.setAppearance(it) },
                    )
                }
            }
            item(key = "timetable") {
                FormSection(header = L("Stundenplan"), footer = L("Für Stunden, die nur jede zweite Woche stattfinden.")) {
                    SwitchRow(L("A/B-Wochen verwenden"), p.weekAReference != null) {
                        model.setWeekAReference(if (it) model.currentDate else null)
                    }
                    if (p.weekAReference != null) {
                        ChoiceRow(
                            title = L("Diese Woche ist"),
                            options = listOf(true to L("A-Woche"), false to L("B-Woche")),
                            selected = model.isWeekA(model.currentDate),
                            onSelect = { model.setCurrentWeekIsA(it) },
                        )
                    }
                }
            }
            item(key = "tracker") {
                FormSection(header = L("Melde Tracker")) {
                    NavRow(L("Wochenziel"), sfSymbol("hand.raised"), trailing = p.weeklyGoal?.toString() ?: L("Keins")) { editingGoal = true }
                }
            }
            item(key = "notifications") {
                FormSection(
                    header = L("Mitteilungen"),
                    footer = if (notificationsDenied) {
                        L("Mitteilungen sind aus. Erlaube sie in den Einstellungen, damit Erinnerungen ankommen.")
                    } else {
                        L("Fragt kurz nach dem Klingeln „Hast du dich gemeldet?“. Antworte direkt in der Mitteilung. Stunden mit Meldung werden übersprungen.")
                    },
                ) {
                    SwitchRow(L("Nach jeder Stunde nachfragen"), p.askAfterLesson) { on ->
                        if (on) requestNotifications() else model.setAskAfterLesson(false)
                    }
                    SwitchRow(L("Live-Update während der Stunde"), p.liveActivities) { model.setLiveActivities(it) }
                }
            }
            // Sections the features add (Noten, Entschuldigung, Erweitert).
            for (feature in features.all) {
                val section = feature.settings ?: continue
                item(key = "feature-${feature.id}") { section() }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
    if (editingGoal) WeeklyGoalSheet(onDismiss = { editingGoal = false })
}

/** Accent swatches: Orange, Indigo, Grün and (Android) Material You in the wallpaper's color. */
@Composable
private fun AccentRow() {
    val model = LocalAppModel.current
    val p = model.preferences
    val wallpaper = dynamicAccentPreview()
    val dynamic = p.dynamicColor && app.meldeplanner.android.ui.theme.dynamicColorAvailable
    Column(
        Modifier.fillMaxWidth().padding(horizontal = DS.spacingL, vertical = DS.spacingM),
        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
    ) {
        Text(L("Farbe"), style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTheme.entries.forEach { theme ->
                AccentSwatch(theme.brandColor(), themeName(theme), !dynamic && p.theme == theme) { model.setTheme(theme) }
            }
            // Wallpaper colors exist from Android 12.
            if (app.meldeplanner.android.ui.theme.dynamicColorAvailable) {
                AccentSwatch(wallpaper, L("Material You"), dynamic, wallpaper = true) { model.setDynamicColor(true) }
            }
        }
        AnimatedVisibility(dynamic) {
            Text(
                "${L("Material You")} · ${L("Farben vom Hintergrundbild")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A [Morph] at [progress], scaled to the component (Material shapes are normalized to 0–1). */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * One accent color: a circle that morphs into a cookie shape inside a ring when
 * selected (Expressive shape morph); instant when animations are off.
 */
@Composable
private fun AccentSwatch(color: Color, label: String, selected: Boolean, wallpaper: Boolean = false, onClick: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatch",
    )
    val shape = MorphShape(morph, progress)
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { rotationZ = progress * 40f }
                .border(2.5.dp, color.copy(alpha = progress.coerceIn(0f, 1f)), shape)
                .padding(5.dp)
                .clip(shape)
                .background(color),
        )
        when {
            selected -> Icon(sfSymbol("checkmark"), null, Modifier.size(18.dp), tint = onColor)
            wallpaper -> Icon(sfSymbol("sparkles"), null, Modifier.size(18.dp), tint = onColor)
        }
    }
}

private fun themeName(theme: AppTheme): String = when (theme) {
    AppTheme.orange -> L("Orange")
    AppTheme.indigo -> L("Indigo")
    AppTheme.green -> L("Grün")
}

private fun appearanceName(choice: AppearanceChoice): String = when (choice) {
    AppearanceChoice.system -> L("System")
    AppearanceChoice.light -> L("Hell")
    AppearanceChoice.dark -> L("Dunkel")
}

/** Weekly Melde target with the suggestion from past weeks (iOS: WeeklyGoalSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyGoalSheet(onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val now = remember { model.currentDate }
    val suggestion = remember { model.suggestedWeeklyGoal(now) }
    var goal by remember { mutableIntStateOf(model.preferences.weeklyGoal ?: suggestion ?: 10) }
    MeldoSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = DS.spacingL)) {
            SheetBar(L("Wochenziel"), onCancel = onDismiss, confirm = L("Sichern")) {
                model.setWeeklyGoal(goal)
                onDismiss()
            }
            FormSection(
                footer = if (suggestion != null) {
                    L("Vorschlag aus deinen letzten Wochen: %lld", suggestion)
                } else {
                    L("Nach einer Woche mit Meldungen schlagen wir dir ein realistisches Ziel vor.")
                },
            ) {
                StepperRow(
                    title = L("%lld Meldungen pro Woche", goal), value = "",
                    canDecrease = goal > 1, canIncrease = goal < 200,
                    onDecrease = { goal-- }, onIncrease = { goal++ },
                )
            }
            if (suggestion != null && suggestion != goal) {
                FormSection { ActionRow(L("Vorschlag übernehmen"), symbol = "sparkles") { goal = suggestion } }
            }
            if (model.preferences.weeklyGoal != null) {
                FormSection {
                    ActionRow(L("Ziel entfernen"), destructive = true) {
                        model.setWeeklyGoal(null)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: L("Unbekannt")
    }
    val core = remember { runCatching { coreVersion() }.getOrDefault(L("Unbekannt")) }
    MeldoScreen(L("Über Meldo")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                FormSection {
                    ValueRow(L("Version"), version)
                    ValueRow(L("Rust-Kern"), core)
                    // Wide screens have no Mehr tab; the tour is reopened from here.
                    if (LocalFeatures.current.has("onboarding")) ActionRow(L("Einführung ansehen")) { model.showOnboardingAgain() }
                }
            }
            item {
                FormSection(header = L("Datenschutz")) {
                    NoteRow(
                        L("Deine Daten bleiben auf deinem Gerät. Meldo hat keinen eigenen Server, keine Werbung und kein Tracking."),
                        MaterialTheme.colorScheme.onSurface,
                    )
                    // The legal texts live on meldo.dev, in the app language, so they can't drift apart.
                    val language = model.data.preferences.language.code
                    NavRow(L("Datenschutzerklärung")) { uriHandler.openUri("https://meldo.dev/$language/privacy") }
                    NavRow(L("Nutzungsbedingungen")) { uriHandler.openUri("https://meldo.dev/$language/terms") }
                }
            }
            item {
                FormSection(header = L("Hinweis")) {
                    NoteRow(
                        L("Meldo zählt deine Beteiligung und berechnet Durchschnitte. Offizielle Noten vergibt allein deine Schule."),
                        MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

// region Startseite anpassen

private fun cardTitle(card: DashboardCard): String = when (card) {
    DashboardCard.today -> L("Heute")
    DashboardCard.weekPreview -> L("Woche")
    DashboardCard.participation -> L("Meldungen diese Woche")
    DashboardCard.streak -> L("Serie")
    DashboardCard.homework -> L("Hausaufgaben")
    DashboardCard.exams -> L("Prüfungen")
    DashboardCard.news -> L("Nachrichten")
    DashboardCard.absences -> L("Abwesenheiten")
    DashboardCard.nextLesson -> L("Nächste Stunde")
    DashboardCard.weekChart -> L("Wochenverlauf")
    DashboardCard.nextHoliday -> L("Nächste Ferien")
    DashboardCard.todos -> L("To-dos")
    DashboardCard.grades -> L("Notenschnitt")
}

private fun cardSymbol(card: DashboardCard): String = when (card) {
    DashboardCard.today -> "sun.max"
    DashboardCard.weekPreview -> "calendar.day.timeline.left"
    DashboardCard.participation -> "hand.raised"
    DashboardCard.streak -> "flame"
    DashboardCard.homework -> "book"
    DashboardCard.exams -> "pencil.and.list.clipboard"
    DashboardCard.news -> "megaphone"
    DashboardCard.absences -> "person.crop.circle.badge.clock"
    DashboardCard.nextLesson -> "clock"
    DashboardCard.weekChart -> "chart.bar.xaxis"
    DashboardCard.nextHoliday -> "sun.horizon"
    DashboardCard.todos -> "checklist"
    DashboardCard.grades -> "graduationcap"
}

private fun sizeTitle(size: CardSize): String = when (size) {
    CardSize.small -> L("Klein")
    CardSize.medium -> L("Mittel")
    CardSize.large -> L("Groß")
}

/**
 * The Start cards as a list: show, hide, sort and size them. Same layout as
 * "Anpassen" on the Start screen, easier with TalkBack. Rows slide when sorted.
 */
@Composable
fun DashboardCardsScreen() {
    val model = LocalAppModel.current
    val layout = model.preferences.homeLayout
    val hidden = DashboardCard.entries.filter { kind -> layout.none { it.kind == kind } }

    fun update(change: (MutableList<CardSlot<DashboardCard>>) -> Unit) {
        val list = layout.map { it.copy() }.toMutableList()
        change(list)
        model.setHomeLayout(list)
    }

    MeldoScreen(L("Startseite anpassen")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            formItems(
                sectionKey = "visible",
                items = layout,
                key = { "visible-${it.kind}" },
                header = L("Sichtbar"),
                footer = L("Mit den Pfeilen sortieren. Tipp: Auf der Startseite kannst du Karten auch mit „Anpassen“ verschieben."),
            ) { slot, shape ->
                val index = layout.indexOf(slot)
                var sizeMenu by remember { mutableStateOf(false) }
                FormItemSurface(shape) {
                    ListItem(
                        headlineContent = { Text(cardTitle(slot.kind)) },
                        leadingContent = { IconTile(sfSymbol(cardSymbol(slot.kind))) },
                        supportingContent = {
                            Box {
                                TextButton(onClick = { sizeMenu = true }, shapes = ButtonDefaults.shapes()) {
                                    Text("${L("Größe")}: ${sizeTitle(slot.size)}")
                                }
                                MeldoMenu(
                                    expanded = sizeMenu,
                                    onDismiss = { sizeMenu = false },
                                    groups = listOf(
                                        CardSize.entries.map { size ->
                                            MeldoMenuItem(sizeTitle(size), checked = size == slot.size) {
                                                update { it[index].size = size }
                                            }
                                        },
                                    ),
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { update { it.add(index - 1, it.removeAt(index)) } },
                                    enabled = index > 0, shapes = IconButtonDefaults.shapes(),
                                ) { Icon(Icons.Rounded.KeyboardArrowUp, L("Nach oben")) }
                                IconButton(
                                    onClick = { update { it.add(index + 1, it.removeAt(index)) } },
                                    enabled = index < layout.lastIndex, shapes = IconButtonDefaults.shapes(),
                                ) { Icon(Icons.Rounded.KeyboardArrowDown, L("Nach unten")) }
                                IconButton(onClick = { update { it.removeAt(index) } }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.RemoveCircleOutline, L("Ausblenden"), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            if (hidden.isNotEmpty()) {
                formItems(
                    sectionKey = "hidden",
                    items = hidden,
                    key = { "hidden-$it" },
                    header = L("Ausgeblendet"),
                ) { kind, shape ->
                    FormItemSurface(shape) {
                        NavRow(cardTitle(kind), sfSymbol(cardSymbol(kind))) {
                            model.setHomeLayout(layout + CardSlot(kind, CardSize.medium))
                        }
                    }
                }
            }
            item(key = "reset") {
                FormSection(modifier = meldoAnimateItem()) {
                    ActionRow(L("Standard wiederherstellen")) { model.setHomeLayout(defaultHomeLayout) }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

// endregion

/** Alternate launcher icons, rendered from the same Meldo artwork. */
@Composable
fun AppIconPickerScreen() {
    val model = LocalAppModel.current
    val context = LocalContext.current
    var problem by remember { mutableStateOf<String?>(null) }
    MeldoScreen(L("App-Symbol")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                FormSection(modifier = Modifier.selectableGroup()) {
                    AppIconChoice.entries.forEach { choice ->
                        val selected = model.preferences.appIcon == choice
                        ListItem(
                            headlineContent = { Text(iconTitle(choice)) },
                            leadingContent = { IconPreview(choice) },
                            trailingContent = if (selected) {
                                { Icon(sfSymbol("checkmark"), null, tint = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).selectable(selected, role = Role.RadioButton) {
                                if (AppIcons.apply(context, choice)) {
                                    model.setAppIcon(choice)
                                    problem = null
                                } else {
                                    problem = L("Das App-Symbol konnte nicht geändert werden.")
                                }
                            },
                        )
                    }
                }
            }
            problem?.let { item { NoteRow(it, MaterialTheme.colorScheme.error) } }
        }
    }
}

/** The adaptive icon's visible area (72 of 108 dp) in a rounded square. */
@Composable
private fun IconPreview(choice: AppIconChoice) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(AppIcons.background(choice)), null, Modifier.requiredSize(72.dp))
        Image(painterResource(AppIcons.foreground(choice)), null, Modifier.requiredSize(72.dp))
    }
}

private fun iconTitle(choice: AppIconChoice): String = when (choice) {
    AppIconChoice.standard -> L("Indigo (Standard)")
    AppIconChoice.orange -> L("Orange")
    AppIconChoice.night -> L("Nacht")
}

/** Einstellungen › Leiste und Startseite: the bar's two places and the opening screen. Port of BarSettingsView. */
@Composable
fun BarSettingsScreen() {
    val model = LocalAppModel.current
    val features = LocalFeatures.current
    val slots = features.barSlots(model.preferences)
    val opening = features.startTab(model.preferences, wide = false)
    fun choose(index: Int, place: MeldoFeature) {
        val next = slots.toMutableList()
        // Choosing what the other place shows swaps the two.
        val other = next.indexOf(place)
        if (other >= 0 && other != index) java.util.Collections.swap(next, other, index) else next[index] = place
        model.setBarTabs(next.map { it.id })
    }
    MeldoScreen(L("Leiste und Startseite"), largeTitle = false) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "bar") {
                FormSection(
                    header = L("Leiste"),
                    footer = L("Start und Stundenplan bleiben immer in der Leiste. Alles andere findest du unter Mehr."),
                ) {
                    slots.getOrNull(0)?.let { PlacePicker(L("Dritter Platz"), features.barChoices, it) { p -> choose(0, p) } }
                    slots.getOrNull(1)?.let { PlacePicker(L("Vierter Platz"), features.barChoices, it) { p -> choose(1, p) } }
                }
            }
            item(key = "start") {
                FormSection(header = L("Startseite"), footer = L("Diese Seite zeigt Meldo, wenn du die App öffnest.")) {
                    opening?.let { PlacePicker(L("Beim Öffnen"), features.fixedBar + slots, it) { p -> model.setStartTab(p.id) } }
                }
            }
        }
    }
}

/** A row showing the chosen place; tapping opens a menu with every option (iOS: menu `Picker`). */
@Composable
private fun PlacePicker(title: String, options: List<MeldoFeature>, selected: MeldoFeature, onSelect: (MeldoFeature) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        NavRow(title, trailing = selected.title) { open = true }
        app.meldeplanner.android.ui.components.MeldoMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                options.map { place ->
                    app.meldeplanner.android.ui.components.MeldoMenuItem(place.title, symbol = place.symbol, checked = place == selected) {
                        open = false
                        onSelect(place)
                    }
                },
            ),
        )
    }
}
