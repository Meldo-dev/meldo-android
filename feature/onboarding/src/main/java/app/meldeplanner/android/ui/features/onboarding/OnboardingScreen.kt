@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.onboarding

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.AppLanguage
import app.meldeplanner.android.model.finishOnboarding
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.Mascot
import app.meldeplanner.android.ui.components.MascotMood
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.timetable.TimetableLegend
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.navigation.Navigator
import kotlinx.coroutines.launch
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.rememberLink

/**
 * The first-run tour, port of meldo-ios/MeldePlanner/Features/Onboarding/OnboardingView.swift:
 * what Meldo is, what Melden means (counting, not grading), one page per area in plain
 * words, and one setup choice. "Überspringen" on every page, no sample data and no
 * permission prompts: those come at the moment a feature needs them. Reopened from Mehr.
 * Closing it (back) counts as skipping.
 */
@Composable
fun OnboardingScreen() {
    val model = LocalAppModel.current
    val navigator = remember { Navigator() }
    Dialog(
        onDismissRequest = { model.finishOnboarding() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            CompositionLocalProvider(LocalNavigator provides navigator) {
                navigator.Content { Tour(navigator) }
            }
        }
    }
}

@Composable
private fun Tour(navigator: Navigator) {
    val model = LocalAppModel.current
    val features = featurePages()
    val lastPage = features.size + 2
    val pager = rememberPagerState { lastPage + 1 }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    // After a subject or WebUntis exists the tour has done its job.
    val isSetUp = model.subjects.isNotEmpty() || model.data.untis != null
    MeldoScreen(
        title = "",
        largeTitle = false,
        actions = {
            TextButton(onClick = { model.finishOnboarding() }) {
                Text(if (isSetUp) L("Fertig") else L("Überspringen"), fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                when {
                    page == 0 -> WelcomePage()
                    page == 1 -> MeldenPage()
                    page < lastPage -> FeaturePageView(features[page - 2])
                    else -> SetupPage(
                        // The tour closes by itself once the WebUntis import succeeded.
                        connectUntis = rememberLink(Link.Page("untis", onDone = { model.finishOnboarding() })),
                        // A saved subject is enough to start: Melden is now one tap away on Start.
                        addSubject = rememberLink(Link.NewSubject(onDone = { if (model.subjects.isNotEmpty()) model.finishOnboarding() })),
                        later = { model.finishOnboarding() },
                    )
                }
            }
            PageDots(pager.currentPage, lastPage + 1)
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                // On the last page Weiter ends the tour, like "Später".
                run {
                    Button(
                        onClick = {
                            if (pager.currentPage >= lastPage) model.finishOnboarding()
                            else scope.launch {
                                val next = minOf(pager.currentPage + 1, lastPage)
                                if (reduceMotion) pager.scrollToPage(next) else pager.animateScrollToPage(next)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(horizontal = DS.spacingXL).heightIn(min = 56.dp),
                    ) { Text(L("Weiter"), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = DS.spacingS).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(7.dp).background(color, CircleShape))
        }
    }
}

// region Pages

@Composable
private fun WelcomePage() {
    val model = LocalAppModel.current
    OnboardingPage {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(MascotMood.wave, size = 96.dp) }
        OnboardingTitle(L("Willkommen bei Meldo"))
        Text(
            L("Dein Schultag an einem Ort: Melden, Stundenplan, Aufgaben und Noten."),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(top = DS.spacingS), verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
            Text(
                L("Sprache"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val languages = AppLanguage.entries
            // One row per language with a checkmark (a list, like iOS), not a segmented control.
            Column(
                Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                languages.forEachIndexed { index, language ->
                    val selected = model.preferences.language == language
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(app.meldeplanner.android.ui.form.groupShape(index, languages.size))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .selectable(selected = selected, role = Role.RadioButton) { model.setLanguage(language) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = DS.spacingL),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(language.nativeName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (selected) Icon(sfSymbol("checkmark"), null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeldenPage() {
    OnboardingPage {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(sfSymbol("hand.raised.fill"), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        OnboardingTitle(L("Melden mit einem Tipp"))
        Text(
            L("Hast du dich gemeldet? Tippe auf „Melden“. Meldo ordnet es der laufenden Stunde zu, so siehst du, wie oft du mitmachst."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), DS.rowShape)
                .padding(DS.spacingM)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(DS.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(sfSymbol("checkmark.seal"), null, tint = MaterialTheme.colorScheme.primary)
            Text(L("Meldo zählt nur – bewerten tut deine Lehrkraft."), style = MaterialTheme.typography.titleMedium)
        }
        OnboardingRow(
            "lock.iphone",
            L("Ohne die App zu öffnen"),
            L("Auch über die Schnelleinstellungen, ein Widget oder die Nachfrage nach der Stunde. Halte dich an die Handyregeln deiner Schule."),
        )
    }
}

/** One area of the app: what it is for, then how to use it, in plain words. */
private class FeaturePage(
    val symbol: String,
    val title: String,
    val intro: String,
    val rows: List<Triple<String, String, String>>,
    val showsLegend: Boolean = false,
)

/** Built on every composition, so a language change shows at once. */
private fun featurePages(): List<FeaturePage> = listOf(
    FeaturePage(
        "tablecells", L("Stundenplan"),
        L("Hier siehst du deine Woche: welches Fach, welcher Raum, welche Lehrkraft. Wischen bringt dich zur nächsten oder letzten Woche."),
        listOf(
            Triple("arrow.triangle.2.circlepath", L("Aus WebUntis oder selbst"), L("Verbinde WebUntis, dann kommt dein Plan von allein, mit Vertretungen und Ausfällen. Ohne WebUntis trägst du die Stunden selbst ein.")),
            Triple("hand.tap", L("Auf eine Stunde tippen"), L("Zeigt alles zur Stunde. Von dort trägst du auch eine Hausaufgabe für dieses Fach ein.")),
            Triple("ellipsis.circle", L("Menü oben rechts"), L("„Heute“ springt zurück zum heutigen Tag. Dort erklärt Meldo auch, was die Farben bedeuten.")),
            Triple("a.circle", L("A- und B-Wochen"), L("Hat deine Schule Stunden nur jede zweite Woche? Schalte A/B-Wochen in den Einstellungen ein.")),
        ),
        showsLegend = true,
    ),
    FeaturePage(
        "checklist", L("Aufgaben und Kalender"),
        L("Alles, was du erledigen musst, an einer Stelle: Hausaufgaben, Lernzettel, eigene To-dos."),
        listOf(
            Triple("plus.circle", L("Aufgabe eintragen"), L("Fach wählen, und Meldo schlägt als Fälligkeit die nächste Stunde in diesem Fach vor. Du kannst auch ein anderes Datum nehmen.")),
            Triple("bell", L("Erinnerungen"), L("Lass dich vorher erinnern. Aufgaben, die jede Woche wiederkommen, legt Meldo nach dem Abhaken neu an.")),
            Triple("tray.and.arrow.down", L("Von WebUntis und Teams"), L("Bist du verbunden, landen Hausaufgaben und Teams-Aufgaben hier von selbst.")),
            Triple("calendar", L("Kalender"), L("Zeigt Prüfungen, fällige Aufgaben und deine Meldungen über den ganzen Monat.")),
        ),
    ),
    FeaturePage(
        "graduationcap", L("Noten und Statistik"),
        L("Trag deine Noten ein, und Meldo rechnet deinen Schnitt pro Fach aus. Das ist deine eigene Rechnung, keine offizielle Note."),
        listOf(
            Triple("number", L("Noten oder Punkte"), L("Noten 1 bis 6 oder Punkte 0 bis 15, je nach Klasse. Das stellst du unter Noten ein.")),
            Triple("scalemass", L("Gewichtung"), L("Schriftlich und mündlich zählen oft verschieden. Frag deine Lehrkraft und trag die Gewichtung ein.")),
            Triple("target", L("Was brauche ich?"), L("Meldo zeigt dir, welche Note du in der nächsten Arbeit brauchst, um dein Ziel zu schaffen.")),
            Triple("chart.bar", L("Statistik"), L("Wie oft hast du dich gemeldet, pro Tag und pro Fach? Mit Wochenziel und Serie bleibst du dran.")),
        ),
    ),
    FeaturePage(
        "doc.viewfinder", L("Dokumente"),
        L("Zurückgegebene Arbeiten, Arbeitsblätter und Zettel: einfach scannen statt verlieren."),
        listOf(
            Triple("camera", L("Scannen"), L("Halte die Kamera über das Blatt, Meldo schneidet es zu und macht ein PDF daraus. Mehrere Seiten gehen auch.")),
            Triple("photo.on.rectangle", L("Fotos und PDFs"), L("Schon fotografiert oder als PDF bekommen? Hol es aus deiner Galerie oder deinen Dateien.")),
            Triple("folder", L("Nach Fach sortiert"), L("Jedes Dokument kommt in den Ordner seines Fachs, auch unter Dateien › Dokumente › Meldo.")),
        ),
    ),
    FeaturePage(
        "person.crop.circle.badge.clock", L("Abwesenheiten und Ferien"),
        L("Mit WebUntis siehst du, wann du gefehlt hast und ob es schon entschuldigt ist."),
        listOf(
            Triple("checkmark.circle", L("Entschuldigt oder offen"), L("Grün ist entschuldigt, rot ist noch offen. Verspätungen zählt Meldo extra.")),
            Triple("doc.text", L("Entschuldigung schreiben"), L("Halte eine Abwesenheit gedrückt, und Meldo füllt dir eine Entschuldigung zum Ausdrucken oder Teilen vor.")),
            Triple("sun.horizon", L("Ferien"), L("Ferien und freie Tage kommen aus WebUntis. Dort zeigt der Stundenplan keine Stunden, also fragt Meldo auch nicht nach.")),
        ),
    ),
    FeaturePage(
        "link", L("Verbindungen"),
        L("Meldo kann sich mit Diensten verbinden, die du schon nutzt. Alles ist freiwillig, und du kannst es jederzeit wieder trennen."),
        listOf(
            Triple("arrow.triangle.2.circlepath", L("WebUntis"), L("Stundenplan, Vertretungen, Hausaufgaben, Prüfungen und Abwesenheiten deiner Schule. Du meldest dich mit deinem normalen Schul-Login an.")),
            Triple("person.3", L("Microsoft Teams"), L("Holt die Aufgaben aus deinen Teams-Klassen. Du schaltest es in den Einstellungen ein, wenn deine Schule es erlaubt.")),
            Triple("externaldrive", L("Google Drive"), L("Ein verschlüsseltes Backup in deinem eigenen Google Drive, mit all deinen Einstellungen. Beim Einrichten legst du ein Backup Passwort fest; damit öffnest du es auch auf einem neuen Gerät.")),
        ),
    ),
    FeaturePage(
        "sparkles", L("Kleine Helfer"),
        L("Meldo funktioniert auch, ohne dass du die App öffnest."),
        listOf(
            Triple("square.grid.2x2", L("Widgets"), L("Leg Meldo auf deinen Startbildschirm: nächste Stunde, offene Aufgaben, Meldungen der Woche.")),
            Triple("bell.badge", L("Nachfrage nach der Stunde"), L("Auf Wunsch fragt Meldo kurz nach dem Klingeln, ob du dich gemeldet hast. Du antwortest direkt in der Mitteilung.")),
            Triple("questionmark.circle", L("Diese Einführung"), L("Findest du jederzeit wieder unter Mehr.")),
        ),
    ),
    FeaturePage(
        "slider.horizontal.3", L("So, wie du es magst"),
        L("Ein paar Einstellungen, damit Meldo zu dir passt."),
        listOf(
            Triple("dock.rectangle", L("Leiste und Startseite"), L("Such dir aus, welche zwei Seiten neben Start und Stundenplan in der Leiste stehen und welche Seite Meldo beim Öffnen zeigt.")),
            Triple("rectangle.3.group", L("Startseite anpassen"), L("Zeig auf der Startseite nur die Karten, die du brauchst, in deiner Reihenfolge.")),
            Triple("circle.lefthalf.filled", L("Hell oder dunkel"), L("Meldo folgt deinem Gerät. In den Einstellungen kannst du auch immer hell oder immer dunkel wählen.")),
        ),
    ),
    FeaturePage(
        "lock.shield", L("Deine Daten bleiben bei dir"),
        L("Meldo hat kein Konto, keinen eigenen Server, keine Werbung und kein Tracking."),
        listOf(
            Triple("iphone", L("Auf deinem Gerät"), L("Alles, was du einträgst, liegt auf deinem Handy oder Tablet.")),
            Triple("key", L("Passwörter verschlüsselt auf dem Gerät"), L("Deine Anmeldungen für WebUntis, Teams und Google bleiben verschlüsselt auf diesem Gerät und gehen nie an Meldo.")),
            Triple("hand.raised", L("Du entscheidest"), L("Daten verlassen dein Gerät nur, wenn du selbst eine Verbindung einschaltest. Mehr dazu steht in der Datenschutzerklärung unter Über Meldo.")),
        ),
    ),
)

@Composable
private fun FeaturePageView(feature: FeaturePage) {
    OnboardingPage {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(sfSymbol(feature.symbol), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        OnboardingTitle(feature.title)
        Text(feature.intro, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((symbol, title, detail) in feature.rows) OnboardingRow(symbol, title, detail)
        if (feature.showsLegend) TimetableLegend(Modifier.padding(start = 36.dp + DS.spacingM), vertical = true)
    }
}

@Composable
private fun SetupPage(connectUntis: (() -> Unit)?, addSubject: (() -> Unit)?, later: () -> Unit) {
    OnboardingPage {
        OnboardingTitle(L("Wie startest du?"))
        Text(
            L("Mit deinem Stundenplan ordnet Meldo jede Meldung der richtigen Stunde zu."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(top = DS.spacingS), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
            if (connectUntis != null) {
                Button(onClick = connectUntis, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(sfSymbol("arrow.triangle.2.circlepath"), null, Modifier.size(20.dp))
                    Spacer(Modifier.size(DS.spacingS))
                    Text(L("Mit WebUntis verbinden"), fontWeight = FontWeight.SemiBold)
                }
                Text(
                    L("Deine Anmeldung geht direkt an WebUntis, nie an Meldo, und bleibt verschlüsselt auf diesem Gerät."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (addSubject != null) FilledTonalButton(onClick = addSubject, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(sfSymbol("plus"), null, Modifier.size(20.dp))
                Spacer(Modifier.size(DS.spacingS))
                Text(L("Fach selbst anlegen"), fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = later, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(L("Später")) }
        }
    }
}

// endregion

// region Building blocks

/** One page: scrolls when the text is large, at most 520 dp wide on big screens. */
@Composable
private fun OnboardingPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = DS.spacingXL, vertical = DS.spacingL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingM),
            content = content,
        )
    }
}

@Composable
private fun OnboardingTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun OnboardingRow(symbol: String, title: String, detail: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(DS.spacingM)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.TopCenter) {
            Icon(sfSymbol(symbol), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion
