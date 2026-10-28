@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.untis

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.UntisAbsenceRecord
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoMenu
import app.meldeplanner.android.ui.components.MeldoMenuItem
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.rows.meldoAnimateItem
import app.meldeplanner.android.ui.rows.FormItemSurface
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.IconTile
import app.meldeplanner.android.ui.rows.NoteRow
import app.meldeplanner.android.ui.rows.StateBadge
import app.meldeplanner.android.ui.rows.SwitchRow
import app.meldeplanner.android.ui.rows.formItems
import app.meldeplanner.android.ui.rows.formatDate
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import java.text.NumberFormat

/** Absences from the WebUntis class register, with a summary. Port of AbsencesView.swift. */
@Composable
fun AbsencesScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    var onlyOpen by rememberSaveable { mutableStateOf(false) }
    val all = model.data.untisExtras?.absences ?: emptyList()
    val shown = if (onlyOpen) all.filter { !it.excused } else all

    MeldoScreen(L("Abwesenheiten")) { padding ->
        if (model.data.untis == null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(DS.spacingXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconTile(sfSymbol("person.crop.circle.badge.clock"), size = 72.dp)
                Spacer(Modifier.height(DS.spacingL))
                Text(L("Abwesenheiten"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(DS.spacingS))
                Text(
                    L("Verbinde WebUntis, um deine Fehlzeiten zu sehen."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(DS.spacingL))
                Button(onClick = { navigator.push { UntisConnectScreen() } }, shapes = ButtonDefaults.shapes()) {
                    Text(L("WebUntis verbinden"))
                }
            }
            return@MeldoScreen
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "summary") {
                FormSection(
                    footer = L("Aus dem Klassenbuch in WebUntis, dieses Schuljahr. Entschuldigungen reichst du weiter bei deiner Schule ein."),
                ) {
                    // One block, so the counts and the note share the card's full width.
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = DS.spacingL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DS.spacingM),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Summary(all.size, L("gesamt"), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                            Summary(all.count { it.excused }, L("entschuldigt"), StateColors.green(), Modifier.weight(1f))
                            Summary(all.count { !it.excused }, L("offen"), StateColors.red(), Modifier.weight(1f))
                        }
                        // A few minutes late reads very differently from a missed day.
                        val late = all.count { model.lateMinutes(it) != null }
                        if (late > 0) {
                            Text(
                                L("Davon %lld Verspätungen", late),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SwitchRow(L("Nur nicht entschuldigte"), onlyOpen) { onlyOpen = it }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    FormSection(modifier = meldoAnimateItem()) { NoteRow(L("Keine Abwesenheiten.")) }
                }
            } else {
                // Rows slide in and out when "Nur nicht entschuldigte" filters them.
                formItems(sectionKey = "absences", items = shown, key = { it.id }) { absence, shape ->
                    FormItemSurface(shape) {
                        AbsenceRow(
                            absence, model.calendar,
                            contentPadding = PaddingValues(horizontal = DS.spacingL, vertical = DS.spacingM),
                        )
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

@Composable
private fun Summary(count: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            NumberFormat.getIntegerInstance(Localization.locale).format(count),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One absence; a tap or long press offers the Entschuldigung, like the iOS context menu.
 * [contentPadding] is the room around the content: none sideways by default, so the
 * row lines up with LessonRow inside a Card (Kalender); grouped lists pass their row inset.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbsenceRow(
    absence: UntisAbsenceRecord,
    calendar: MeldoCalendar,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = DS.spacingXS),
) {
    val navigator = LocalNavigator.current
    var menu by remember { mutableStateOf(false) }
    val color = if (absence.excused) StateColors.green() else StateColors.red()
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(role = Role.Button, onClick = { menu = true }, onLongClick = { menu = true })
                .padding(contentPadding)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            // Same 36 dp leading badge as SubjectIcon in LessonRow, so texts line up.
            StateBadge(if (absence.excused) "checkmark" else "exclamationmark.circle", color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(absenceDates(absence, calendar), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                Text(TimeText.range(absence.startMinute, absence.endMinute), style = MaterialTheme.typography.bodyMedium, color = secondary)
                LocalAppModel.current.lateMinutes(absence)?.let { late ->
                    Text(L("Verspätung · %lld Min.", late), style = MaterialTheme.typography.labelMedium, color = secondary)
                }
                if (absence.reason.isNotEmpty()) Text(absence.reason, style = MaterialTheme.typography.bodyMedium, color = secondary)
                if (absence.text.isNotEmpty()) Text(absence.text, style = MaterialTheme.typography.bodyMedium, color = secondary)
                Text(
                    if (absence.excused) L("entschuldigt") else L("nicht entschuldigt"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
        }
        MeldoMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MeldoMenuItem(L("Entschuldigung erstellen"), symbol = "doc.text") {
                        navigator.push { ExcuseNoteScreen(absence) }
                    },
                ),
            ),
        )
    }
}

/** "Di., 29. Sept." or a range, in the app language. */
private fun absenceDates(absence: UntisAbsenceRecord, calendar: MeldoCalendar): String {
    val skeleton = "EEEdMMM"
    val first = formatDate(absence.firstDay, skeleton, calendar.zone)
    if (calendar.isSameDay(absence.firstDay, absence.lastDay)) return first
    return "$first–${formatDate(absence.lastDay, skeleton, calendar.zone)}"
}

/**
 * Minutes late, when the absence only covers the start of the day's first lesson
 * (WebUntis records a late arrival as a short absence). Port of iOS `lateMinutes`.
 */
fun app.meldeplanner.android.model.AppModel.lateMinutes(absence: app.meldeplanner.android.data.UntisAbsenceRecord): Int? {
    if (!calendar.isSameDay(absence.firstDay, absence.lastDay)) return null
    val periods = data.untisTimetable?.periods ?: return null
    val first = periods.filter { !it.cancelled && calendar.isSameDay(it.date, absence.firstDay) }.minByOrNull { it.startMinute } ?: return null
    if (absence.startMinute != first.startMinute || absence.endMinute <= absence.startMinute || absence.endMinute >= first.endMinute) return null
    return absence.endMinute - absence.startMinute
}
