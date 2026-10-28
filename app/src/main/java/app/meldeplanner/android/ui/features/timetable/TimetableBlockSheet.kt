@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.timetable

import app.meldeplanner.android.ui.theme.MeldoSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.Lesson
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.model.chooseCourse
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoSegmented
import app.meldeplanner.android.ui.components.SubjectIcon
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.sfSymbol

/**
 * Lesson details. Parallel groups get a tab each (like WebUntis); the student
 * marks their own with "Das ist mein Kurs".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableBlockSheet(
    block: TimetableBlock,
    onDismiss: () -> Unit,
    onHomework: (String?) -> Unit,
    onEdit: (Lesson) -> Unit,
) {
    val model = LocalAppModel.current
    val options = remember(block) { listOf(block) + block.alternatives }
    var tab by remember(block) { mutableIntStateOf(0) }
    val shown = options[minOf(tab, options.size - 1)]
    MeldoSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DS.spacingL)
                .navigationBarsPadding()
                .padding(bottom = DS.spacingL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shown.shortTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(L("Fertig")) }
            }
            if (options.size > 1) {
                // One tab per parallel group, like WebUntis.
                MeldoSegmented(
                    options = options.indices.toList(),
                    selected = minOf(tab, options.size - 1),
                    label = { index ->
                        val option = options[index]
                        if (option.teacher.isEmpty()) option.shortTitle else "${option.shortTitle} · ${option.teacher}"
                    },
                    onSelect = { tab = it },
                )
            }
            SheetSection {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                    SubjectIcon(model.subject(shown.subjectID), size = 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(shown.subjectName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            TimetableDates.weekdayDayMonth(shown.date, model.calendar),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BlockStyle.label(shown.kind)?.let { status ->
                    val style = BlockStyle.of(shown.kind)
                    SheetLabel("exclamationmark.circle.fill", status, style.border ?: UntisColors.change)
                }
            }
            SheetSection {
                LabeledValue(L("Zeit"), TimeText.range(shown.startMinute, shown.endMinute))
                if (shown.teacher.isNotEmpty()) LabeledValue(L("Lehrkraft"), shown.teacher)
                shown.room?.let { room ->
                    val original = shown.originalRoom
                    LabeledValue(L("Raum"), if (original != null) L("%@ statt %@", room, original) else room)
                }
                shown.info?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            if (options.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                    SheetSection {
                        if (tab == 0) {
                            SheetLabel("checkmark.circle.fill", L("Dein Kurs"), MaterialTheme.colorScheme.primary)
                        } else {
                            SheetButton("checkmark.circle", L("Das ist mein Kurs")) {
                                model.chooseCourse(shown, options)
                                onDismiss()
                            }
                        }
                    }
                    Text(
                        L("Meldo zeigt deinen Kurs ab jetzt jede Woche zu dieser Zeit."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = DS.spacingL),
                    )
                }
            }
            SheetSection {
                SheetButton("book", L("Hausaufgabe eintragen")) { onHomework(shown.subjectID) }
                val lesson = shown.lessonID?.let { id -> model.lessons.firstOrNull { it.id == id } }
                if (lesson != null) {
                    SheetButton("pencil", L("Bearbeiten")) { onEdit(lesson) }
                }
            }
        }
    }
}

/** A grouped list section (iOS inset grouped `Section`). */
@Composable
private fun SheetSection(content: @Composable () -> Unit) {
    Surface(shape = DS.cardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) { content() }
    }
}

@Composable
private fun LabeledValue(title: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(DS.spacingS))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetLabel(symbol: String, text: String, color: Color) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
    ) {
        Icon(sfSymbol(symbol), null, tint = color)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun SheetButton(symbol: String, text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            Icon(sfSymbol(symbol), null)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
