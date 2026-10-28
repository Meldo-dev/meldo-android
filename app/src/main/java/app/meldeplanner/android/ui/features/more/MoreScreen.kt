@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.more

import app.meldeplanner.android.ui.navigation.AppTab
import app.meldeplanner.android.ui.navigation.Page
import app.meldeplanner.android.ui.navigation.barChoices
import app.meldeplanner.android.ui.navigation.barSlots
import app.meldeplanner.android.ui.navigation.symbol
import app.meldeplanner.android.ui.navigation.title
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
import app.meldeplanner.android.model.setExcuseDetails
import app.meldeplanner.android.surfaces.Notifications
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoMenu
import app.meldeplanner.android.ui.components.MeldoMenuItem
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.calendar.CalendarScreen
import app.meldeplanner.android.ui.features.grades.GradeSettingsSheet
import app.meldeplanner.android.ui.features.grades.GradesScreen
import app.meldeplanner.android.ui.features.statistics.StatisticsScreen
import app.meldeplanner.android.ui.features.subjects.SubjectListScreen
import app.meldeplanner.android.ui.features.timetable.HolidayListScreen
import app.meldeplanner.android.ui.features.untis.AbsencesScreen
import app.meldeplanner.android.ui.features.untis.TeamsConnectScreen
import app.meldeplanner.android.model.teamsAvailable
import app.meldeplanner.android.model.setTeamsEnabled
import app.meldeplanner.android.model.drivePasswordMissing
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.features.untis.UntisConnectScreen
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.model.DriveState
import app.meldeplanner.android.model.showOnboardingAgain
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.brandColor
import app.meldeplanner.android.ui.theme.dynamicAccentPreview
import uniffi.melde_ffi.coreVersion

// Port of meldo-ios/MeldePlanner/Features/More/MoreView.swift: the Mehr tab,
// Einstellungen, Über Meldo, the Start card list and the app icon picker.

/** The phone's Mehr tab. Wide screens list the same destinations in the drawer. */
@Composable
fun MoreScreen() {
    val navigator = LocalNavigator.current
    val model = LocalAppModel.current
    // On phones Kalender and Statistik have no tab of their own (the drawer shows them on wide screens).
    val compact = LocalConfiguration.current.screenWidthDp < 840
    MeldoScreen(L("Mehr")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            // Everything that isn't in the bar (the student picks the bar's two places), then Ferien.
            item(key = "school") {
                FormSection(header = L("Schule")) {
                    app.meldeplanner.android.ui.navigation.placesOutsideBar(model.preferences, wide = !compact).forEach { place ->
                        NavRow(place.title, sfSymbol(place.symbol)) { navigator.push { place.Page() } }
                    }
                }
            }
            item(key = "connections") {
                FormSection(header = L("Verbindungen")) {
                    NavRow(L("WebUntis"), sfSymbol("arrow.triangle.2.circlepath")) { navigator.push { UntisConnectScreen() } }
                    if (model.teamsAvailable) {
                        NavRow(L("Microsoft Teams"), sfSymbol("person.3")) { navigator.push { TeamsConnectScreen() } }
                    }
                    NavRow(
                        L("Google Drive"), sfSymbol("externaldrive"),
                        trailing = when (model.driveState) {
                            DriveState.on -> L("An")
                            DriveState.needsPassword -> L("Passwort fehlt")
                            else -> L("Aus")
                        },
                        trailingAccent = if (model.drivePasswordMissing) StateColors.orange() else null,
                    ) { navigator.push { GoogleDriveScreen() } }
                }
            }
            item(key = "app") {
                FormSection(header = L("App")) {
                    NavRow(L("Fächer"), sfSymbol("books.vertical")) { navigator.push { SubjectListScreen() } }
                    NavRow(L("Einstellungen"), sfSymbol("gearshape")) { navigator.push { SettingsScreen() } }
                    NavRow(L("Status: Widgets, Mitteilungen"), sfSymbol("stethoscope")) { navigator.push { StatusScreen() } }
                    NavRow(L("Über Meldo"), sfSymbol("info.circle")) { navigator.push { AboutScreen() } }
                    ActionRow(L("Einführung ansehen"), symbol = "sparkles") { model.showOnboardingAgain() }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

@Composable
fun SettingsScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val p = model.preferences
    var editingGoal by remember { mutableStateOf(false) }
    var gradeSettings by remember { mutableStateOf(false) }
    var excuseDetails by remember { mutableStateOf(false) }
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
            item(key = "grades") {
                FormSection(header = L("Noten")) {
                    NavRow(
                        L("Notensystem"), sfSymbol("slider.horizontal.3"),
                        trailing = if (p.gradeScale == app.meldeplanner.android.data.GradeScaleChoice.points) L("Punkte 0–15") else L("Noten 1–6"),
                    ) { gradeSettings = true }
                }
            }
            item(key = "excuse") {
                FormSection(header = L("Entschuldigung"), footer = L("Name und Klasse merkt sich Meldo für das nächste Mal.")) {
                    val who = listOf(p.studentName, p.className).filter { it.isNotEmpty() }.joinToString(" · ")
                    NavRow(L("Angaben"), sfSymbol("doc.text"), supporting = who.ifEmpty { null }) { excuseDetails = true }
                }
            }
            item(key = "advanced") {
                FormSection(
                    header = L("Erweitert"),
                    footer = L("Schalte das nur ein, wenn deine Schule Meldo für Teams freigegeben hat. Viele Schulen müssen das erst erlauben."),
                ) {
                    SwitchRow(L("Microsoft Teams"), p.teamsEnabled) { model.setTeamsEnabled(it) }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
    if (editingGoal) WeeklyGoalSheet(onDismiss = { editingGoal = false })
    if (gradeSettings) GradeSettingsSheet(onDismiss = { gradeSettings = false })
    if (excuseDetails) ExcuseDetailsDialog(onDismiss = { excuseDetails = false })
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

/** The student's details for the Entschuldigung, remembered for the next one. */
@Composable
private fun ExcuseDetailsDialog(onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val p = model.preferences
    var name by remember { mutableStateOf(p.studentName) }
    var className by remember { mutableStateOf(p.className) }
    var salutation by remember { mutableStateOf(p.excuseSalutation) }
    var adult by remember { mutableStateOf(p.adultStudent) }
    MeldoAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Entschuldigung")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                OutlinedTextField(name, { name = it }, label = { Text(L("Dein Name")) }, singleLine = true, shape = DS.rowShape)
                OutlinedTextField(className, { className = it }, label = { Text(L("Klasse")) }, singleLine = true, shape = DS.rowShape)
                OutlinedTextField(
                    salutation, { salutation = it }, label = { Text(L("Anrede")) }, singleLine = true,
                    placeholder = { Text(L("Sehr geehrte Damen und Herren,")) },
                    shape = DS.rowShape,
                )
                Row(
                    Modifier.fillMaxWidth().clip(DS.rowShape).selectable(adult, role = Role.Switch) { adult = !adult }
                        .padding(vertical = DS.spacingXS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(L("Ich bin volljährig"), Modifier.weight(1f))
                    Switch(checked = adult, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    model.setExcuseDetails(name, className, salutation, adult)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(L("Sichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) } },
    )
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
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
                    ActionRow(L("Einführung ansehen")) { model.showOnboardingAgain() }
                }
            }
            item {
                FormSection(header = L("Datenschutz")) {
                    NoteRow(
                        L("Deine Daten bleiben auf deinem Gerät. Meldo hat keinen eigenen Server, keine Werbung und kein Tracking."),
                        MaterialTheme.colorScheme.onSurface,
                    )
                    NavRow(L("Datenschutzerklärung")) { navigator.push { PrivacyPolicyScreen() } }
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
    val slots = barSlots(model.preferences)
    val opening = app.meldeplanner.android.ui.navigation.startTab(model.preferences, wide = false)
    fun choose(index: Int, place: AppTab) {
        val next = slots.toMutableList()
        // Choosing what the other place shows swaps the two.
        val other = next.indexOf(place)
        if (other >= 0 && other != index) java.util.Collections.swap(next, other, index) else next[index] = place
        model.setBarTabs(next.map { it.name })
    }
    MeldoScreen(L("Leiste und Startseite"), largeTitle = false) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "bar") {
                FormSection(
                    header = L("Leiste"),
                    footer = L("Start und Stundenplan bleiben immer in der Leiste. Alles andere findest du unter Mehr."),
                ) {
                    PlacePicker(L("Dritter Platz"), barChoices, slots[0]) { choose(0, it) }
                    PlacePicker(L("Vierter Platz"), barChoices, slots[1]) { choose(1, it) }
                }
            }
            item(key = "start") {
                FormSection(header = L("Startseite"), footer = L("Diese Seite zeigt Meldo, wenn du die App öffnest.")) {
                    PlacePicker(L("Beim Öffnen"), listOf(AppTab.home, AppTab.timetable) + slots, opening) { model.setStartTab(it.name) }
                }
            }
        }
    }
}

/** A row showing the chosen place; tapping opens a menu with every option (iOS: menu `Picker`). */
@Composable
private fun PlacePicker(title: String, options: List<AppTab>, selected: AppTab, onSelect: (AppTab) -> Unit) {
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
