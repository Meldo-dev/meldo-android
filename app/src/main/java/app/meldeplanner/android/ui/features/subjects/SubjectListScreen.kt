@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.subjects

import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.SubjectIcon
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.tasks.MorphShape
import app.meldeplanner.android.ui.features.tasks.SelectionShapes
import app.meldeplanner.android.ui.features.tasks.groupShape
import app.meldeplanner.android.ui.features.tasks.rememberMorph
import app.meldeplanner.android.ui.features.tasks.rememberSelectionProgress
import app.meldeplanner.android.ui.features.tasks.rowAnimation
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.color

/** The student's subjects (Fächer). Port of SubjectListView. */
@Composable
fun SubjectListScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    var pendingDelete by remember { mutableStateOf<Subject?>(null) }

    fun edit(subject: Subject?) = navigator.push { SubjectEditorScreen(subject = subject, onDismiss = { navigator.pop() }) }

    MeldoScreen(
        title = L("Fächer"),
        actions = {
            IconButton(onClick = { edit(null) }, shapes = IconButtonDefaults.shapes()) { Icon(sfSymbol("plus"), L("Fach anlegen")) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = DS.spacingL, end = DS.spacingL,
                top = padding.calculateTopPadding() + DS.spacingS,
                bottom = padding.calculateBottomPadding() + DS.spacingXL,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (model.subjects.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = DS.spacingXL * 2, horizontal = DS.spacingL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
                    ) {
                        Icon(sfSymbol("books.vertical"), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            L("Noch keine Fächer"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            L("Fächer bündeln Stunden, Meldungen und später deine Noten."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { edit(null) }, modifier = Modifier.padding(top = DS.spacingXS), shapes = ButtonDefaults.shapes()) { Text(L("Fach anlegen")) }
                    }
                }
            }
            val subjects = model.subjects
            itemsIndexed(subjects, key = { _, subject -> subject.id }) { index, subject ->
                SubjectRow(
                    subject = subject,
                    shape = groupShape(index, subjects.size),
                    onClick = { edit(subject) },
                    onDelete = { pendingDelete = subject },
                    modifier = rowAnimation(),
                )
            }
        }
    }

    pendingDelete?.let { subject ->
        MeldoAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(L("Fach löschen?")) },
            text = { Text(L("Bisherige Meldungen bleiben erhalten, aber ohne Fach.")) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    model.deleteSubject(subject.id)
                }) { Text(L("„%@“ und seine Stunden löschen", subject.name), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(L("Abbrechen")) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectRow(
    subject: Subject,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState()
    // The row snaps back; deleting waits for the confirmation, like the iOS swipe action.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            state.reset()
        }
    }
    val deleteLabel = L("Löschen")
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(StateColors.red(), shape).padding(horizontal = DS.spacingXL),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                    Icon(sfSymbol("trash"), null, tint = Color.White)
                    Text(deleteLabel, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        ListItem(
            headlineContent = { Text(subject.name) },
            supportingContent = if (subject.teacher.isNotEmpty()) ({ Text(subject.teacher) }) else null,
            leadingContent = { SubjectIcon(subject) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                },
        )
    }
}

/** Create or edit a subject: name, short name, teacher, color and symbol. Port of SubjectEditorView(subject:). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectEditorScreen(subject: Subject?, onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val isNew = subject == null
    var draft by remember { mutableStateOf(subject?.copy() ?: Subject(name = "")) }
    MeldoScreen(
        title = if (isNew) L("Neues Fach") else L("Fach bearbeiten"),
        largeTitle = false,
        onBack = onDismiss,
        actions = {
            TextButton(
                onClick = {
                    model.upsert(draft)
                    onDismiss()
                },
                enabled = draft.name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) { Text(L("Sichern")) }
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
            section(null) {
                Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text(L("Name, z. B. Mathematik")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.shortName,
                        onValueChange = { draft = draft.copy(shortName = it) },
                        label = { Text(L("Kürzel, z. B. M")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.teacher,
                        onValueChange = { draft = draft.copy(teacher = it) },
                        label = { Text(L("Lehrkraft (optional)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            section(L("Farbe")) {
                EvenGrid(cell = 44.dp) {
                    for (color in SubjectColor.entries) {
                        val isSelected = draft.color == color
                        // The chosen color morphs from a circle into a cookie shape (Expressive selection).
                        val progress = rememberSelectionProgress(isSelected, "color")
                        val morph = rememberMorph(SelectionShapes.circle, SelectionShapes.cookie)
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { draft = draft.copy(color = color) }
                                .semantics {
                                    contentDescription = color.name
                                    role = Role.RadioButton
                                    selected = isSelected
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp + 6.dp * progress)
                                    .background(color.color, MorphShape(morph, progress)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (progress > 0.01f) {
                                    Icon(
                                        sfSymbol("checkmark"), null,
                                        Modifier.size(16.dp).graphicsLayer {
                                            alpha = progress.coerceIn(0f, 1f)
                                            scaleX = progress
                                            scaleY = progress
                                        },
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            section(L("Symbol")) {
                EvenGrid(cell = 48.dp) {
                    val tint = draft.color.color
                    for (symbol in DS.subjectSymbols) {
                        val isSelected = draft.symbolName == symbol
                        // The chosen symbol sits on a tinted shape that morphs from a circle into a four-sided cookie.
                        val progress = rememberSelectionProgress(isSelected, "symbol")
                        val morph = rememberMorph(SelectionShapes.circle, SelectionShapes.cookie4)
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(tint.copy(alpha = 0.18f * progress.coerceIn(0f, 1f)), MorphShape(morph, progress))
                                    .clip(CircleShape)
                                    .clickable { draft = draft.copy(symbolName = symbol) }
                                    .semantics {
                                        contentDescription = symbol
                                        role = Role.RadioButton
                                        selected = isSelected
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    sfSymbol(symbol), null,
                                    tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Picker grid that spreads its columns over the full width, like the iOS adaptive
 * grid: as many cells as fit with at least [DS.spacingM] between them, and the last
 * row stays on the same columns.
 */
@Composable
private fun EvenGrid(cell: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(DS.spacingL)) {
        val columns = ((maxWidth + DS.spacingM) / (cell + DS.spacingM)).toInt().coerceAtLeast(1)
        // Half a dp less than exact, so rounding never pushes the last cell to a new row.
        val gap = if (columns > 1) ((maxWidth - cell * columns) / (columns - 1) - 0.5.dp).coerceAtLeast(0.dp) else 0.dp
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(DS.spacingM),
            maxItemsInEachRow = columns,
        ) { content() }
    }
}

private fun LazyListScope.section(title: String?, content: @Composable () -> Unit) {
    item {
        Column {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = DS.spacingL, bottom = DS.spacingS).semantics { heading() },
                )
            }
            Surface(shape = DS.rowShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
