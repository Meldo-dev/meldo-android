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
 * Rows slide between sections and fade in and out with the theme's motion (Expressive
 * spatial spring). No movement with Remove animations.
 */
@Composable
internal fun LazyItemScope.rowAnimation(): Modifier {
    if (rememberReduceMotion()) return Modifier
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec<IntOffset>(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

/** `.swipeActions { Button(role: .destructive) }`: swipe from the trailing edge deletes. */
@Composable
internal fun SwipeToDelete(
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, shape).padding(horizontal = DS.spacingL),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(sfSymbol("trash"), L("Löschen"), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/** `ContentUnavailableView`: icon, title, text and an optional button. */
@Composable
internal fun EmptyState(symbol: String, title: String, text: String? = null, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = DS.spacingXL * 2, horizontal = DS.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
    ) {
        Icon(sfSymbol(symbol), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.size(DS.spacingS))
            Button(onClick = action.second, shapes = ButtonDefaults.shapes()) { Text(action.first) }
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

/** One task with a check circle. Checking off gives haptic feedback. Used by Start, Stundenplan and Kalender too. */
@Composable
fun TaskRow(task: SchoolTask, today: Instant, onOpen: () -> Unit) {
    val model = LocalAppModel.current
    val haptics = LocalHapticFeedback.current
    val subject = model.subject(task.subjectID)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingM)) {
        IconButton(
            onClick = {
                if (model.preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                model.toggleDone(task.id)
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(44.dp).semantics {
                contentDescription = if (task.isDone) L("Als offen markieren") else L("Als erledigt markieren")
            },
        ) {
            CheckCircle(task.isDone, subject?.color?.color ?: secondary)
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isDone) secondary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            )
            val due = task.dueDate
            if (subject != null || due != null || task.recurrence != TaskRecurrence.never || (task.reminderAt != null && !task.isDone)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.CenterVertically) {
                    val style = MaterialTheme.typography.bodyMedium
                    if (subject != null) Text(subject.name, style = style, color = subject.color.color, maxLines = 1)
                    if (due != null) {
                        val overdue = !task.isDone && due < model.calendar.startOfDay(today)
                        Text(
                            DueText.label(due, today, model.calendar),
                            style = style,
                            color = if (overdue) StateColors.red() else secondary,
                            maxLines = 1,
                        )
                    }
                    if (task.recurrence != TaskRecurrence.never) {
                        Icon(sfSymbol("repeat"), L("Wiederholt sich"), Modifier.size(16.dp), tint = secondary)
                    }
                    if (task.reminderAt != null && !task.isDone) {
                        Icon(sfSymbol("bell"), L("Erinnerung"), Modifier.size(16.dp), tint = secondary)
                    }
                }
            }
        }
        if (task.priority == TaskPriority.high && !task.isDone) {
            Icon(sfSymbol("exclamationmark.circle.fill"), L("Wichtig"), tint = StateColors.orange())
        }
    }
}

/**
 * The check circle: the ring fills green and morphs into a soft cookie shape with
 * the check mark, on the theme's spatial spring (instant with Remove animations).
 */
@Composable
private fun CheckCircle(done: Boolean, ring: Color) {
    val progress = rememberSelectionProgress(done, "check")
    val morph = rememberMorph(SelectionShapes.circle, SelectionShapes.cookie)
    val fill = StateColors.green()
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(23.dp)
                .graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) }
                .border(2.dp, ring, CircleShape),
        )
        if (progress > 0.01f) {
            Box(
                Modifier
                    .size(27.dp)
                    .graphicsLayer {
                        val scale = (0.4f + 0.6f * progress).coerceAtLeast(0f)
                        scaleX = scale
                        scaleY = scale
                        alpha = progress.coerceIn(0f, 1f)
                    }
                    .background(fill, MorphShape(morph, progress)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sfSymbol("checkmark"), null, Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

/** Section titles of the task groups. */
object TaskText {
    fun title(group: FfiTaskGroup): String = when (group) {
        FfiTaskGroup.OVERDUE -> L("Überfällig")
        FfiTaskGroup.TODAY -> L("Heute")
        FfiTaskGroup.TOMORROW -> L("Morgen")
        FfiTaskGroup.THIS_WEEK -> L("Diese Woche")
        FfiTaskGroup.LATER -> L("Später")
        FfiTaskGroup.NO_DATE -> L("Ohne Datum")
        FfiTaskGroup.COMPLETED -> L("Erledigt")
    }
}

/** "heute", "morgen", "gestern", a weekday this week, or "12. Okt.". */
object DueText {
    fun label(due: Instant, today: Instant, calendar: MeldoCalendar): String {
        val days = ChronoUnit.DAYS.between(calendar.localDate(today), calendar.localDate(due))
        return when (days) {
            0L -> L("heute")
            1L -> L("morgen")
            -1L -> L("gestern")
            in 2L..6L -> DayFormat.format(due, "EEEE", calendar)
            else -> DayFormat.format(due, "dMMM", calendar)
        }
    }
}
