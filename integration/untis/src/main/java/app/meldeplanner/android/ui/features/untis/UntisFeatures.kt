package app.meldeplanner.android.ui.features.untis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.meldeplanner.android.core.L
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.installUntis
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.MeldoFeature
import app.meldeplanner.android.ui.navigation.Placement
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.NavRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.meldeplanner.android.model.setExcuseDetails
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.theme.MeldoAlertDialog

/** WebUntis: the school's timetable, exams, holidays, news and absences. */
object UntisFeature : MeldoFeature {
    override val id = "untis"
    override val title get() = L("WebUntis")
    override val symbol = "arrow.triangle.2.circlepath"
    override val placement = Placement.connections

    @Composable
    override fun Page() = UntisConnectScreen()

    override fun screen(link: Link): (@Composable () -> Unit)? =
        if (link is Link.Page && link.id == id) ({ UntisConnectScreen(onConnected = link.onDone) }) else null

    override fun install(model: AppModel) = model.installUntis()
}

/** Abwesenheiten: absences from WebUntis and the Entschuldigung. */
object AbsencesFeature : MeldoFeature {
    override val id = "absences"
    override val title get() = L("Abwesenheiten")
    override val symbol = "person.crop.circle.badge.clock"
    override val placement = Placement.school
    override val barChoice = true

    @Composable
    override fun Page() = AbsencesScreen()

    override fun screen(link: Link): (@Composable () -> Unit)? =
        if (link is Link.ExcuseNote) ({ ExcuseNoteScreen(link.absence) }) else null

    override val settings: @Composable () -> Unit = { ExcuseSection() }
}

@Composable
private fun ExcuseSection() {
    val p = LocalAppModel.current.preferences
    var editing by remember { mutableStateOf(false) }
    FormSection(header = L("Entschuldigung"), footer = L("Name und Klasse merkt sich Meldo für das nächste Mal.")) {
        val who = listOf(p.studentName, p.className).filter { it.isNotEmpty() }.joinToString(" · ")
        NavRow(L("Angaben"), sfSymbol("doc.text"), supporting = who.ifEmpty { null }) { editing = true }
    }
    if (editing) ExcuseDetailsDialog(onDismiss = { editing = false })
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

