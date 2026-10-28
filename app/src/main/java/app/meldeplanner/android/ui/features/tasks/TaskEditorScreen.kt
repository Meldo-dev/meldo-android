@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.TaskPriority
import app.meldeplanner.android.data.TaskRecurrence
import app.meldeplanner.android.model.deleteTask
import app.meldeplanner.android.model.nextLessonDay
import app.meldeplanner.android.model.upsert
import app.meldeplanner.android.surfaces.Notifications
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.color
import java.time.Duration
import java.time.Instant

/**
 * Create or edit a Hausaufgabe or to-do. Port of TaskEditorView.swift.
 *
 * @param subjectID preselects the subject and sets the due date to its next lesson.
 */
@Composable
fun TaskEditorScreen(
    task: SchoolTask?,
    kind: TaskKind = TaskKind.homework,
    subjectID: String? = null,
    onDismiss: () -> Unit,
) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val isNew = task == null
    var draft by remember { mutableStateOf(task?.copy() ?: SchoolTask(title = "", kind = kind, subjectID = subjectID)) }
    var hasDue by rememberSaveable { mutableStateOf(if (task == null) kind == TaskKind.homework else task.dueDate != null) }
    var hasReminder by rememberSaveable { mutableStateOf(draft.reminderAt != null) }
    var notificationsDenied by remember { mutableStateOf(false) }
    val requestNotifications = Notifications.rememberPermissionRequester { granted -> notificationsDenied = !granted }

    /** 17:00 the day before the due date, or in one hour without one. */
    fun defaultReminder(): Instant {
        val now = model.currentDate
        draft.dueDate?.let { due ->
            val evening = calendar.at(calendar.addDays(due, -1), 17 * 60)
            if (evening > now) return evening
        }
        return now.plus(Duration.ofHours(1))
    }

    fun save() {
        val saved = draft.copy()
        if (!hasDue) saved.dueDate = null
        else if (saved.dueDate == null) saved.dueDate = calendar.startOfDay(model.currentDate)
        saved.reminderAt = if (hasReminder) (draft.reminderAt ?: defaultReminder()) else null
        model.upsert(saved)
        onDismiss()
    }

    // prefillDue
    LaunchedEffect(Unit) {
        if (!isNew || draft.dueDate != null || !hasDue) return@LaunchedEffect
        val sid = draft.subjectID
        val next = sid?.let { model.nextLessonDay(it, model.currentDate) }
        draft = draft.copy(dueDate = next ?: calendar.addDays(calendar.startOfDay(model.currentDate), 1))
    }

    MeldoScreen(
        if (isNew) L("Neue Aufgabe") else L("Aufgabe bearbeiten"),
        largeTitle = false,
        onBack = onDismiss,
        actions = {
            TextButton(onClick = ::save, enabled = draft.title.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text(L("Sichern"), fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                // Inside the scroll: the form slides under the translucent bars.
                .padding(padding)
                .padding(DS.spacingL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingXL),
        ) {
            FormSection {
                TextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = { Text(L("Was ist zu tun?")) },
                    textStyle = MaterialTheme.typography.titleMedium,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormDivider()
                SegmentedPicker(
                    selection = draft.kind,
                    options = listOf(TaskKind.homework, TaskKind.todo),
                    label = { if (it == TaskKind.homework) L("Hausaufgabe") else L("To-do") },
                    onSelect = { draft = draft.copy(kind = it) },
                    modifier = Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                )
                FormDivider()
                SubjectPickerRow(draft.subjectID) { draft = draft.copy(subjectID = it) }
            }

            FormSection {
                SwitchRow(L("Fällig am"), hasDue) { hasDue = it }
                AnimatedVisibility(hasDue) {
                    Column {
                        FormDivider()
                        DateRow(L("Datum"), draft.dueDate ?: calendar.startOfDay(model.currentDate), calendar) {
                            draft = draft.copy(dueDate = it)
                        }
                        val sid = draft.subjectID
                        val nextLesson = sid?.let { model.nextLessonDay(it, model.currentDate) }
                        if (nextLesson != null) {
                            FormDivider()
                            ActionRow(
                                L("Bis zur nächsten Stunde (%@)", DueText.label(nextLesson, model.currentDate, calendar)),
                                sfSymbol("calendar.badge.clock"),
                            ) { draft = draft.copy(dueDate = nextLesson) }
                        }
                    }
                }
                FormDivider()
                PickerRow(
                    L("Wiederholen"),
                    draft.recurrence,
                    TaskRecurrence.entries,
                    label = {
                        when (it) {
                            TaskRecurrence.never -> L("Nie")
                            TaskRecurrence.daily -> L("Täglich")
                            TaskRecurrence.weekdays -> L("Schultage (Mo–Fr)")
                            TaskRecurrence.weekly -> L("Wöchentlich")
                            TaskRecurrence.biweekly -> L("Alle zwei Wochen")
                        }
                    },
                    onSelect = { draft = draft.copy(recurrence = it) },
                )
                FormDivider()
                PickerRow(
                    L("Wichtigkeit"),
                    draft.priority,
                    TaskPriority.entries,
                    label = {
                        when (it) {
                            TaskPriority.low -> L("Niedrig")
                            TaskPriority.normal -> L("Normal")
                            TaskPriority.high -> L("Hoch")
                        }
                    },
                    onSelect = { draft = draft.copy(priority = it) },
                )
            }

            FormSection(
                footer = if (notificationsDenied) L("Mitteilungen sind aus. Erlaube sie in den Einstellungen, damit Erinnerungen ankommen.") else null,
            ) {
                SwitchRow(L("Erinnerung"), hasReminder) { on ->
                    hasReminder = on
                    if (on) requestNotifications()
                }
                AnimatedVisibility(hasReminder) {
                    Column {
                        FormDivider()
                        DateTimeRow(L("Um"), draft.reminderAt ?: defaultReminder(), calendar) {
                            draft = draft.copy(reminderAt = it)
                        }
                    }
                }
            }

            FormSection(header = L("Notizen")) {
                TextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    placeholder = { Text(L("Seite, Aufgabe, Details …")) },
                    minLines = 3,
                    maxLines = 8,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!isNew) {
                FormSection {
                    ActionRow(L("Aufgabe löschen"), color = MaterialTheme.colorScheme.error) {
                        model.deleteTask(draft.id)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** `Picker(L("Fach"))` with "Kein Fach" and every subject with its symbol. */
@Composable
internal fun SubjectPickerRow(selection: String?, onSelect: (String?) -> Unit) {
    val model = LocalAppModel.current
    val subjects = model.subjects
    PickerRow(
        L("Fach"),
        selection,
        listOf<String?>(null) + subjects.map { it.id },
        label = { id -> if (id == null) L("Kein Fach") else model.subject(id)?.name ?: L("Kein Fach") },
        onSelect = onSelect,
        icon = { id ->
            model.subject(id)?.let { subject ->
                { Icon(sfSymbol(subject.symbolName), null, Modifier.size(20.dp), tint = subject.color.color) }
            }
        },
    )
}

@Composable
internal fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
