@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.timetable

import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.data.WeekParity
import app.meldeplanner.android.data.newID
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.WeekdayText
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.form.OptionsMenu
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import uniffi.melde_ffi.FfiTimetableIssue

/**
 * Create or edit a lesson. Overlaps are detected by the Rust core before saving.
 * Port of LessonEditorView(lesson:weekday:).
 */
@Composable
fun LessonEditorScreen(lesson: Lesson?, weekday: Int, onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val original = lesson
    var draft by remember {
        mutableStateOf(
            lesson?.copy() ?: Lesson(subjectID = newID(), weekday = weekday, startMinute = 8 * 60, endMinute = 8 * 60 + 45),
        )
    }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = Beginn, false = Ende

    LaunchedEffect(Unit) {
        if (model.subject(draft.subjectID) == null) {
            model.subjects.firstOrNull()?.let { draft = draft.copy(subjectID = it.id) }
        }
    }

    fun describe(issues: List<FfiTimetableIssue>): String {
        for (issue in issues) {
            if (issue is FfiTimetableIssue.Overlap) {
                val otherID = if (issue.first == draft.id) issue.second else issue.first
                val other = model.lessons.firstOrNull { it.id == otherID }
                val name = other?.let { model.subject(it.subjectID)?.name } ?: L("Unbekanntes Fach")
                return L("Überschneidet sich mit %@.", name)
            }
        }
        return L("Diese Stunde ist ungültig.")
    }

    fun save() {
        if (draft.endMinute <= draft.startMinute) {
            problem = L("Das Ende muss nach dem Beginn liegen.")
            return
        }
        val issues = model.save(draft)
        if (issues.isEmpty()) onDismiss() else problem = describe(issues)
    }

    MeldoScreen(
        title = if (original == null) L("Neue Stunde") else L("Stunde bearbeiten"),
        largeTitle = false,
        onBack = onDismiss,
        actions = {
            TextButton(onClick = { save() }, enabled = model.subject(draft.subjectID) != null, shapes = ButtonDefaults.shapes()) { Text(L("Sichern")) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = DS.spacingL, end = DS.spacingL,
                top = padding.calculateTopPadding() + DS.spacingS,
                bottom = padding.calculateBottomPadding() + DS.spacingXL,
            ),
            verticalArrangement = Arrangement.spacedBy(DS.spacingL),
        ) {
            formSection {
                val subject = model.subject(draft.subjectID)
                PickerRow(
                    title = L("Fach"),
                    value = subject?.name ?: "",
                    options = model.subjects.map { it.id to it.name },
                    symbols = model.subjects.associate { it.id to it.symbolName },
                    onSelect = { id -> draft = draft.copy(subjectID = id) },
                )
                HorizontalDivider(Modifier.padding(start = DS.spacingL))
                PickerRow(
                    title = L("Tag"),
                    value = WeekdayText.full(draft.weekday),
                    options = (0 until 7).map { it to WeekdayText.full(it) },
                    onSelect = { day -> draft = draft.copy(weekday = day) },
                )
            }
            formSection {
                ValueRow(L("Beginn"), TimeText.clock(draft.startMinute)) { editingTime = true }
                HorizontalDivider(Modifier.padding(start = DS.spacingL))
                ValueRow(L("Ende"), TimeText.clock(draft.endMinute)) { editingTime = false }
            }
            formSection(
                footer = if (draft.parity != WeekParity.every && model.preferences.weekAReference == null) {
                    L("Lege in den Einstellungen fest, welche Woche eine A-Woche ist. Bis dahin findet die Stunde jede Woche statt.")
                } else {
                    null
                },
            ) {
                OutlinedTextField(
                    value = draft.room,
                    onValueChange = { draft = draft.copy(room = it) },
                    label = { Text(L("Raum")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingL, vertical = DS.spacingS),
                )
                HorizontalDivider(Modifier.padding(start = DS.spacingL))
                PickerRow(
                    title = L("Woche"),
                    value = parityTitle(draft.parity),
                    options = WeekParity.entries.map { it to parityTitle(it) },
                    onSelect = { p -> draft = draft.copy(parity = p) },
                )
            }
            problem?.let { text ->
                formSection {
                    val red = StateColors.red()
                    ListItem(
                        headlineContent = { Text(text, color = red) },
                        leadingContent = { Icon(sfSymbol("exclamationmark.triangle.fill"), null, tint = red) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            if (original != null) {
                formSection {
                    ListItem(
                        headlineContent = { Text(L("Stunde löschen"), color = MaterialTheme.colorScheme.error) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clickable(role = Role.Button) { confirmingDelete = true },
                    )
                }
            }
        }
    }

    editingTime?.let { start ->
        TimePickerDialog(
            minute = if (start) draft.startMinute else draft.endMinute,
            onDismiss = { editingTime = null },
            onPick = { minute ->
                draft = if (start) draft.copy(startMinute = minute) else draft.copy(endMinute = minute)
                problem = null
                editingTime = null
            },
        )
    }

    if (confirmingDelete) {
        MeldoAlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(L("Stunde löschen?")) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmingDelete = false
                    original?.let { model.deleteLesson(it.id) }
                    onDismiss()
                }) { Text(L("Löschen"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) } },
        )
    }
}

private fun parityTitle(parity: WeekParity): String = when (parity) {
    WeekParity.every -> L("Jede Woche")
    WeekParity.a -> L("Nur A-Woche")
    WeekParity.b -> L("Nur B-Woche")
}

/** A grouped form section (iOS `Form` `Section`) with an optional footer. */
internal fun LazyListScope.formSection(footer: String? = null, content: @Composable () -> Unit) {
    item {
        Column {
            // Same inset group as the Aufgaben form (FormSection).
            Surface(shape = DS.cardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column { content() }
            }
            if (footer != null) {
                Text(
                    footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingS),
                )
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** `Picker` in a form: the value on the right, an Expressive menu with the options. */
@Composable
private fun <T> PickerRow(
    title: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    symbols: Map<T, String> = emptyMap(),
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = {
                // The same value style as every other form row (FormParts).
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .clickable(role = Role.DropdownList) { open = true }
                .semantics(mergeDescendants = true) {},
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end = DS.spacingS)) {
            OptionsMenu<Pair<T, String>?>(
                expanded = open,
                onDismiss = { open = false },
                selection = options.firstOrNull { it.second == value },
                options = options,
                label = { it?.second ?: "" },
                onSelect = { option -> option?.let { onSelect(it.first) } },
                icon = { option -> symbols[option?.first]?.let { symbol -> { Icon(sfSymbol(symbol), null) } } },
            )
        }
    }
}

/** Material time picker in a dialog (iOS `DatePicker(.hourAndMinute)`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(minute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = minute / 60,
        initialMinute = minute % 60,
        is24Hour = app.meldeplanner.android.core.Localization.is24Hour(context),
    )
    MeldoAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }, shapes = ButtonDefaults.shapes()) { Text(L("Fertig")) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) } },
        text = { TimePicker(state = state) },
    )
}
