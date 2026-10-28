@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.DocumentCategory
import app.meldeplanner.android.data.ExamDocument
import app.meldeplanner.android.model.ExamException
import app.meldeplanner.android.model.addExam
import app.meldeplanner.android.model.deleteExam
import app.meldeplanner.android.model.update
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.MeldoSegmented
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.tasks.ActionRow
import app.meldeplanner.android.ui.features.tasks.DateRow
import app.meldeplanner.android.ui.features.tasks.FormDivider
import app.meldeplanner.android.ui.features.tasks.FormSection
import app.meldeplanner.android.ui.features.tasks.SubjectPickerRow
import app.meldeplanner.android.ui.features.tasks.transparentFieldColors
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import kotlinx.coroutines.launch

sealed interface ExamEditorMode {
    data class New(val pending: PendingExam) : ExamEditorMode
    data class Existing(val exam: ExamDocument) : ExamEditorMode
}

/** Details of a new scan or an existing document. Port of ExamEditorView. */
@Composable
fun ExamEditorScreen(mode: ExamEditorMode, onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val scope = rememberCoroutineScope()
    val existing = (mode as? ExamEditorMode.Existing)?.exam
    val isNew = existing == null

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var subjectID by remember { mutableStateOf(existing?.subjectID) }
    var date by remember { mutableStateOf(existing?.date ?: calendar.startOfDay(model.currentDate)) }
    var grade by remember { mutableStateOf(existing?.grade ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var category by remember { mutableStateOf(existing?.kind ?: DocumentCategory.exam) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        when (mode) {
            is ExamEditorMode.New -> {
                saving = true
                scope.launch {
                    try {
                        model.addExam(mode.pending.pdf, title, subjectID, date, grade, notes, category)
                        onDismiss()
                    } catch (e: ExamException) {
                        problem = L("Die Prüfung konnte nicht gespeichert werden.")
                    } finally {
                        saving = false
                    }
                }
            }
            is ExamEditorMode.Existing -> {
                val exam = mode.exam.copy(
                    title = title.ifEmpty { mode.exam.title },
                    subjectID = subjectID,
                    date = date,
                    grade = grade,
                    notes = notes,
                    category = category,
                )
                model.update(exam)
                onDismiss()
            }
        }
    }

    MeldoScreen(
        if (isNew) L("Neue Prüfung") else L("Prüfung bearbeiten"),
        largeTitle = false,
        onBack = onDismiss,
        actions = {
            if (saving) {
                LoadingIndicator(Modifier.padding(horizontal = DS.spacingM).size(32.dp))
            } else {
                TextButton(onClick = ::save, shapes = ButtonDefaults.shapes()) { Text(L("Sichern"), fontWeight = FontWeight.SemiBold) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                // Inside the scroll, so the form scrolls under the translucent bars.
                .padding(padding)
                .padding(DS.spacingL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingXL),
        ) {
            FormSection {
                MeldoSegmented(
                    options = DocumentCategory.entries,
                    selected = category,
                    label = DocumentCategoryText::name,
                    onSelect = { category = it },
                    symbol = DocumentCategoryText::symbol,
                    modifier = Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                )
                FormDivider()
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(L("Titel, z. B. 2. Klassenarbeit")) },
                    singleLine = true,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormDivider()
                SubjectPickerRow(subjectID) { subjectID = it }
                FormDivider()
                DateRow(L("Datum"), date, calendar) { date = it }
            }
            FormSection(footer = L("So, wie sie auf der Arbeit steht. In den Notenrechner fließt sie erst, wenn du sie dort einträgst.")) {
                TextField(
                    value = grade,
                    onValueChange = { grade = it },
                    placeholder = { Text(L("Note, z. B. 2- oder 11 P.")) },
                    singleLine = true,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FormSection(header = L("Notizen")) {
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text(L("Was lief gut, was üben?")) },
                    minLines = 3,
                    maxLines = 8,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            problem?.let { ProblemLabel(it) }
            if (existing != null) {
                FormSection {
                    ActionRow(L("Prüfung löschen"), color = MaterialTheme.colorScheme.error) {
                        model.deleteExam(existing.id)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** `Label(problem, systemImage: "exclamationmark.triangle.fill")` in red. */
@Composable
internal fun ProblemLabel(text: String, modifier: Modifier = Modifier) {
    val red = StateColors.red()
    Row(modifier.padding(horizontal = DS.spacingS), horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.CenterVertically) {
        Icon(sfSymbol("exclamationmark.triangle.fill"), null, tint = red)
        Text(text, color = red, style = MaterialTheme.typography.bodyMedium)
    }
}
