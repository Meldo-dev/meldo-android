@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.ParticipationEntry
import app.meldeplanner.android.model.absences
import app.meldeplanner.android.model.holiday
import app.meldeplanner.android.model.meldungDays
import app.meldeplanner.android.model.tasks
import app.meldeplanner.android.model.timetableBlocks
import app.meldeplanner.android.model.untisExams
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoMenu
import app.meldeplanner.android.ui.components.MeldoMenuItem
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.features.tasks.MorphShape
import app.meldeplanner.android.ui.features.tasks.SelectionShapes
import app.meldeplanner.android.ui.features.tasks.rememberMorph
import app.meldeplanner.android.ui.features.tasks.rememberSelectionProgress
import app.meldeplanner.android.ui.components.SubjectIcon
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.WeekdayText
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.dashboard.BlockRow
import app.meldeplanner.android.ui.features.dashboard.UntisExamRow
import app.meldeplanner.android.ui.features.tasks.DayFormat
import app.meldeplanner.android.ui.features.timetable.HolidayBanner
import app.meldeplanner.android.ui.features.untis.AbsenceRow
import app.meldeplanner.android.ui.features.tasks.TaskRow
import app.meldeplanner.android.ui.features.tasks.rememberMinuteNow
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import java.time.Instant
import java.time.YearMonth

/**
 * Month view with markers (flame on days with Meldungen, exams, homework,
 * absences, holidays in blue) and the picked day's lessons, exams, homework,
 * absences and Meldungen. On wide screens (≥ 840 dp, the iPad layout) the month
 * and the day sit side by side. Port of CalendarView.swift.
 */
@Composable
fun CalendarScreen() {
    val model = LocalAppModel.current
    val now = rememberMinuteNow(model)
    /** The day the student picked; today (by the model's clock) until they pick one. */
    var picked by remember { mutableStateOf<Instant?>(null) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val date = picked ?: now
    val month = YearMonth.from(model.calendar.localDate(now)).plusMonths(monthOffset.toLong())

    MeldoScreen(
        L("Kalender"),
        actions = {
            TextButton(
                onClick = {
                    picked = null
                    monthOffset = 0
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(L("Heute")) }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val grid: @Composable (Modifier) -> Unit = { modifier ->
                Card(modifier) {
                    MonthGrid(month, date, now, onSelect = { picked = it }, onShift = { monthOffset += it })
                }
            }
            // Padding inside the scroll: the month and the day cards slide under the translucent bars.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(DS.spacingL)) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingL), verticalAlignment = Alignment.Top) {
                        grid(Modifier.width(440.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.spacingL)) { DayDetails(date, now) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DS.spacingL)) {
                        grid(Modifier)
                        DayDetails(date, now)
                    }
                }
            }
        }
    }
}

/** The blue WebUntis uses for holidays (iOS `Color.untisHoliday`). */
private val UntisHoliday = Color(0xFF6B85C4)

private data class MeldungGroup(val subjectID: String?, val entries: List<ParticipationEntry>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayDetails(date: Instant, now: Instant) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    model.holiday(date)?.let { HolidayBanner(it) }

    val blocks = model.timetableBlocks(date)
    Card(title = DayFormat.format(date, "EEEEdMMMM", calendar), symbol = "tablecells") {
        if (blocks.isEmpty()) {
            Text(L("Kein Unterricht an diesem Tag."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                for (block in blocks) BlockRow(block, showInfo = true)
            }
        }
    }

    val exams = model.untisExams(date)
    if (exams.isNotEmpty()) {
        Card(title = L("Prüfungen"), symbol = "pencil.and.list.clipboard") {
            for (exam in exams) UntisExamRow(exam, now)
        }
    }

    val due = model.tasks(dueOn = date)
    if (due.isNotEmpty()) {
        Card(title = L("Fällig"), symbol = "book") {
            for (task in due) TaskRow(task, now) {}
        }
    }

    val absences = model.absences(date)
    if (absences.isNotEmpty()) {
        Card(title = L("Abwesenheiten"), symbol = "person.crop.circle.badge.clock") {
            // AbsenceRow brings its own list padding; cancel it here so it lines up with the other card rows.
            for (absence in absences) AbsenceRow(absence, calendar)
        }
    }

    val entries = model.entries(date)
    if (entries.isNotEmpty()) {
        Card(title = L("Meldungen"), symbol = "hand.raised") {
            // One row per subject with its count; long lists of single entries are hard to read.
            val groups = entries.groupBy { it.subjectID }
                .map { (id, list) -> MeldungGroup(id, list.sortedBy { it.recordedAt }) }
                .sortedBy { it.entries.firstOrNull()?.recordedAt ?: Instant.MIN }
            for (group in groups) key(group.subjectID ?: "none") {
                var menu by remember { mutableStateOf(false) }
                val subject = model.subject(group.subjectID)
                Box {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DS.spacingS),
                    ) {
                        SubjectIcon(subject, size = 28.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(subject?.name ?: L("Ohne Fach"))
                            Text(
                                group.entries.joinToString(", ") { TimeText.clock(calendar.minuteOfDay(it.recordedAt)) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Text("${group.entries.size}×", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    MeldoMenu(
                        expanded = menu,
                        onDismiss = { menu = false },
                        groups = listOf(
                            listOfNotNull(
                                group.entries.lastOrNull()?.let { last ->
                                    MeldoMenuItem(L("Letzte Meldung löschen"), "trash", destructive = true) {
                                        model.deleteParticipation(last.id)
                                    }
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/** A month of days, Monday first, with markers per day. Swipe sideways to change the month. */
@Composable
private fun MonthGrid(month: YearMonth, selected: Instant, now: Instant, onSelect: (Instant) -> Unit, onShift: (Int) -> Unit) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val streakDays = model.meldungDays
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val days: List<Instant?> = List(lead) { null } + (1..month.lengthOfMonth()).map { calendar.instant(month.atDay(it)) }
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }

    Column(
        Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total < -threshold) onShift(1)
                    if (total > threshold) onShift(-1)
                },
                onHorizontalDrag = { _, amount -> total += amount },
            )
        },
        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
    ) {
        val reduceMotion = rememberReduceMotion()
        val motion = MaterialTheme.motionScheme
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                month,
                label = "month",
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        // Later months come in from the end, earlier ones from the start.
                        val forward = targetState > initialState
                        (slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { if (forward) it / 3 else -it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                            .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { if (forward) -it / 3 else it / 3 } + fadeOut(motion.fastEffectsSpec()))
                    }
                },
            ) { m ->
                Text(
                    DayFormat.format(calendar.instant(m.atDay(1)), "MMMMy", calendar),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = { onShift(-1) }, shapes = IconButtonDefaults.shapes()) { Icon(sfSymbol("chevron.left"), L("Zurück")) }
            IconButton(onClick = { onShift(1) }, shapes = IconButtonDefaults.shapes()) { Icon(sfSymbol("chevron.right"), L("Weiter")) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (index in 0 until 7) {
                Text(
                    WeekdayText.short(index),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (week in days.chunked(7)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (day in week) {
                        Box(Modifier.weight(1f)) {
                            // Keyed by day, so a cell never carries the selection animation of the
                            // day that sat in the same grid slot last month.
                            if (day != null) key(day) {
                                DayCell(
                                    day = day,
                                    isSelected = calendar.isSameDay(day, selected),
                                    isToday = calendar.isSameDay(day, now),
                                    hasMeldung = calendar.startOfDay(day) in streakDays,
                                    onClick = { onSelect(day) },
                                )
                            } else {
                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        CalendarLegend()
    }
}

/** Rounder cells for the Expressive month grid. */
private val DayCellShape = RoundedCornerShape(14.dp)

@Composable
private fun DayCell(day: Instant, isSelected: Boolean, isToday: Boolean, hasMeldung: Boolean, onClick: () -> Unit) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val scheme = MaterialTheme.colorScheme
    val holiday = model.holiday(day) != null
    val exam = model.untisExams(day).isNotEmpty()
    val homework = model.tasks(dueOn = day).any { !it.isDone }
    val absent = model.absences(day).isNotEmpty()
    val markers = buildList {
        if (hasMeldung) add(L("Meldungen"))
        if (holiday) add(L("Ferien"))
        if (exam) add(L("Prüfung"))
        if (homework) add(L("Hausaufgabe fällig"))
        if (absent) add(L("Abwesend"))
    }.joinToString(", ")
    val label = DayFormat.format(day, "EEEEdMMMM", calendar)
    // The selected day fills with the accent and morphs from a circle into a rounded
    // square; today keeps a tonal circle. Colors and shape follow the theme's motion.
    val reduceMotion = rememberReduceMotion()
    val effects = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val progress = rememberSelectionProgress(isSelected, "day")
    val morph = rememberMorph(SelectionShapes.circle, SelectionShapes.square)
    val container by animateColorAsState(
        when {
            isSelected -> scheme.primary
            isToday -> scheme.primaryContainer
            else -> Color.Transparent
        },
        effects,
        label = "dayContainer",
    )
    val content by animateColorAsState(
        when {
            isSelected -> scheme.onPrimary
            isToday -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        },
        effects,
        label = "dayContent",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(DayCellShape)
            .background(if (holiday) UntisHoliday.copy(alpha = 0.25f) else Color.Transparent, DayCellShape)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = markers
                selected = isSelected
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(container, MorphShape(morph, progress)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                calendar.localDate(day).dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = content,
            )
        }
        Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasMeldung) Icon(sfSymbol("flame.fill"), null, Modifier.size(9.dp), tint = StateColors.orange())
            if (exam) Dot(StateColors.yellow())
            if (homework) Dot(StateColors.orange())
            if (absent) Dot(StateColors.red())
        }
    }
}

@Composable
private fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 5.dp) =
    Box(Modifier.size(size).background(color, CircleShape))

/** What the markers mean. Wraps onto more lines when narrow (ViewThatFits). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    val items = listOf(
        Triple(L("Meldungen"), true, StateColors.orange()),
        Triple(L("Prüfung"), false, StateColors.yellow()),
        Triple(L("Hausaufgabe fällig"), false, StateColors.orange()),
        Triple(L("Abwesend"), false, StateColors.red()),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DS.spacingM), verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
        for ((title, flame, color) in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (flame) Icon(sfSymbol("flame.fill"), null, Modifier.size(8.dp), tint = color) else Dot(color, 7.dp)
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
