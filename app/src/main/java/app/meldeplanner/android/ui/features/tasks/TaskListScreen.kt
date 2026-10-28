@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.TaskPriority
import app.meldeplanner.android.data.TaskRecurrence
import app.meldeplanner.android.model.TaskSection
import app.meldeplanner.android.model.deleteTask
import app.meldeplanner.android.model.taskSections
import app.meldeplanner.android.model.toggleDone
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoMenu
import app.meldeplanner.android.ui.components.MeldoMenuItem
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.SectionHeader
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.color
import uniffi.melde_ffi.FfiTaskGroup
import java.time.Instant
import java.time.temporal.ChronoUnit
import app.meldeplanner.android.ui.form.rememberSelectionProgress
import app.meldeplanner.android.ui.form.rememberMorph
import app.meldeplanner.android.ui.form.rememberMinuteNow
import app.meldeplanner.android.ui.form.groupShape
import app.meldeplanner.android.ui.form.SelectionShapes
import app.meldeplanner.android.ui.form.SegmentedPicker
import app.meldeplanner.android.ui.form.MorphShape
import app.meldeplanner.android.ui.form.DayFormat
import app.meldeplanner.android.ui.rows.rowAnimation
import app.meldeplanner.android.ui.rows.TaskText
import app.meldeplanner.android.ui.rows.TaskRow
import app.meldeplanner.android.ui.rows.SwipeToDelete
import app.meldeplanner.android.ui.rows.EmptyState

/** Hausaufgaben and to-dos grouped by urgency (grouping comes from the Rust core). Port of TaskListView.swift. */
@Composable
fun TaskListScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    var filter by rememberSaveable { mutableStateOf<TaskKind?>(null) }
    val now = rememberMinuteNow(model)
    val sections = model.taskSections(now, filter)

    fun open(task: SchoolTask) = navigator.push { TaskEditorScreen(task = task, onDismiss = { navigator.pop() }) }
    fun create() {
        val kind = filter ?: TaskKind.homework
        navigator.push { TaskEditorScreen(task = null, kind = kind, onDismiss = { navigator.pop() }) }
    }

    MeldoScreen(
        L("Aufgaben"),
        actions = {
            IconButton(onClick = ::create, shapes = IconButtonDefaults.shapes()) { Icon(sfSymbol("plus"), L("Aufgabe hinzufügen")) }
        },
    ) { padding ->
        // No padding outside the scroller: the list runs under the translucent bars.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp && sections.isNotEmpty()) {
                TaskBoard(sections, now, filter, { filter = it }, ::open, padding)
            } else {
                TaskList(sections, now, filter, { filter = it }, ::open, ::create, padding)
            }
        }
    }
}

@Composable
internal fun TaskFilterPicker(filter: TaskKind?, onChange: (TaskKind?) -> Unit, modifier: Modifier = Modifier) {
    SegmentedPicker(
        selection = filter,
        options = listOf(null, TaskKind.homework, TaskKind.todo),
        label = {
            when (it) {
                null -> L("Alle")
                TaskKind.homework -> L("Hausaufgaben")
                TaskKind.todo -> L("To-dos")
            }
        },
        onSelect = onChange,
        modifier = modifier,
    )
}

@Composable
private fun TaskList(
    sections: List<TaskSection>,
    today: Instant,
    filter: TaskKind?,
    onFilter: (TaskKind?) -> Unit,
    onOpen: (SchoolTask) -> Unit,
    onCreate: () -> Unit,
    padding: PaddingValues,
) {
    val model = LocalAppModel.current
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection) + DS.spacingL,
            end = padding.calculateEndPadding(layoutDirection) + DS.spacingL,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + DS.spacingXL,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "filter") {
            TaskFilterPicker(filter, onFilter, Modifier.padding(vertical = DS.spacingS))
        }
        if (sections.isEmpty()) {
            item(key = "empty") {
                Box(rowAnimation()) {
                EmptyState(
                    symbol = "checkmark.circle",
                    title = L("Nichts zu tun"),
                    text = L("Trag Hausaufgaben direkt im Stundenplan ein: lange auf eine Stunde drücken."),
                    action = L("Aufgabe hinzufügen") to onCreate,
                )
                }
            }
        }
        for (section in sections) {
            item(key = "header-${section.id}") {
                SectionHeader(TaskText.title(section.group), rowAnimation())
            }
            val count = section.tasks.size
            // Same key in every section, so a ticked task slides into "Erledigt" instead of jumping.
            items(section.tasks.withIndex().toList(), key = { it.value.id }) { (index, task) ->
                SwipeToDelete(onDelete = { model.deleteTask(task.id) }, shape = groupShape(index, count),
                    // Above the section headers, so a row moving between sections slides over them.
                    modifier = rowAnimation().zIndex(1f)) {
                    Box(Modifier.padding(horizontal = DS.spacingS, vertical = DS.spacingXS)) {
                        TaskRow(task, today) { onOpen(task) }
                    }
                }
            }
        }
    }
}

/**
 * Wide screens (≥ 840 dp, the iPad layout): tasks as a board with three columns
 * (now, soon, later) and the done tasks below, so a whole week fits on screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskBoard(
    sections: List<TaskSection>,
    today: Instant,
    filter: TaskKind?,
    onFilter: (TaskKind?) -> Unit,
    onOpen: (SchoolTask) -> Unit,
    padding: PaddingValues,
) {
    val model = LocalAppModel.current
    val columns = listOf(
        Triple(L("Jetzt"), "exclamationmark.circle", listOf(FfiTaskGroup.OVERDUE, FfiTaskGroup.TODAY)),
        Triple(L("Bald"), "calendar", listOf(FfiTaskGroup.TOMORROW, FfiTaskGroup.THIS_WEEK)),
        Triple(L("Später"), "tray", listOf(FfiTaskGroup.LATER, FfiTaskGroup.NO_DATE)),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(DS.spacingL).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(DS.spacingL),
    ) {
        TaskFilterPicker(filter, onFilter, Modifier.widthIn(max = 420.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingL), verticalAlignment = Alignment.Top) {
            for ((title, symbol, groups) in columns) {
                val tasks = groups.flatMap { g -> sections.firstOrNull { it.group == g }?.tasks ?: emptyList() }
                Card(Modifier.weight(1f), title = title, symbol = symbol) {
                    if (tasks.isEmpty()) {
                        Text(L("Nichts zu tun"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (task in tasks) {
                        androidx.compose.runtime.key(task.id) {
                            var menu by remember { mutableStateOf(false) }
                            Box(Modifier.combinedClickable(onClick = { onOpen(task) }, onLongClick = { menu = true })) {
                                TaskRow(task, today) { onOpen(task) }
                                MeldoMenu(
                                    expanded = menu,
                                    onDismiss = { menu = false },
                                    groups = listOf(
                                        listOf(MeldoMenuItem(L("Löschen"), "trash", destructive = true) { model.deleteTask(task.id) }),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        val done = sections.firstOrNull { it.group == FfiTaskGroup.COMPLETED }
        if (done != null && done.tasks.isNotEmpty()) {
            Card(title = L("Erledigt"), symbol = "checkmark.circle") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // LazyVGrid(.adaptive(minimum: 280)).
                    val perRow = maxOf(1, (maxWidth / 280.dp).toInt())
                    Column(verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                        for (row in done.tasks.take(30).chunked(perRow)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                                for (task in row) {
                                    Box(Modifier.weight(1f)) { TaskRow(task, today) { onOpen(task) } }
                                }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
