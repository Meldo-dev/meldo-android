@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.dashboard

import app.meldeplanner.android.ui.theme.MeldoSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.CardSize
import app.meldeplanner.android.data.DashboardCard
import app.meldeplanner.android.data.UntisExamRecord
import app.meldeplanner.android.model.HolidayText
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.model.holiday
import app.meldeplanner.android.model.homeworkStreak
import app.meldeplanner.android.model.timetableBlocks
import app.meldeplanner.android.model.upcomingUntisExams
import app.meldeplanner.android.ui.components.Badge
import app.meldeplanner.android.ui.components.Card
import app.meldeplanner.android.ui.components.CustomizableCardGrid
import app.meldeplanner.android.ui.components.CustomizeButton
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.Mascot
import app.meldeplanner.android.ui.components.MascotMood
import app.meldeplanner.android.ui.components.ProgressRing
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import uniffi.melde_ffi.FfiOccurrence
import java.time.Instant
import app.meldeplanner.android.ui.rows.rememberTickingNow
import app.meldeplanner.android.ui.rows.DateText
import app.meldeplanner.android.ui.rows.CardLink
import app.meldeplanner.android.ui.rows.CardBlockStyle
import app.meldeplanner.android.ui.rows.digits
import app.meldeplanner.android.ui.rows.NumberText
import app.meldeplanner.android.ui.rows.UntisExamRow
import app.meldeplanner.android.ui.rows.BlockRow
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.rememberLink

// Port of meldo-ios/MeldePlanner/Features/Dashboard/DashboardView.swift.

/**
 * Home: cards the student arranges and resizes ("Anpassen"), from a day
 * preview to school messages. Adapts to what the student has set up.
 */
@Composable
fun DashboardScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val now = rememberTickingNow(model)
    var editing by rememberSaveable { mutableStateOf(false) }
    val setUp = model.subjects.isNotEmpty() || model.data.untis != null

    MeldoScreen(
        title = greeting(now),
        actions = { if (setUp) CustomizeButton(editing) { editing = it } },
    ) { padding ->
        // The bar padding goes inside the scroll, so cards scroll under the translucent
        // top bar and navigation bar. It already includes room for the Melden button.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(DS.spacingL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingL),
        ) {
            if (!setUp) {
                GettingStartedCard(
                    addSubject = rememberLink(Link.NewSubject()),
                    connectUntis = rememberLink(Link.Page("untis")),
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            } else {
                if (!editing) {
                    UnassignedCard(now)
                    CheckInPromptCard()
                }
                CustomizableCardGrid(
                    slots = model.preferences.homeLayout,
                    editing = editing,
                    onEditingChange = { editing = it },
                    title = ::dashboardCardTitle,
                    symbol = ::dashboardCardSymbol,
                    allKinds = DashboardCard.entries,
                    save = { model.setHomeLayout(it) },
                ) { card, size ->
                    DashboardCardView(card, size, now)
                }
            }
        }
    }
}

@Composable
private fun DashboardCardView(card: DashboardCard, size: CardSize, now: Instant) {
    val model = LocalAppModel.current
    when (card) {
        DashboardCard.today -> TodayCard(now, size, addLesson = rememberLink(Link.NewLesson(model.calendar.mondayIndex(model.currentDate))))
        DashboardCard.weekPreview -> WeekPreviewCard(now, size)
        DashboardCard.participation -> WeekParticipationCard(now, size)
        DashboardCard.streak -> StreakCard(now, size)
        DashboardCard.homework -> HomeworkCard(now, size)
        DashboardCard.exams -> UpcomingExamsCard(now, size)
        DashboardCard.news -> SchoolNewsCard(size)
        DashboardCard.absences -> AbsencesCard(size)
        DashboardCard.nextLesson -> NextLessonCard(now, size)
        DashboardCard.weekChart -> WeekChartMiniCard(now, size)
        DashboardCard.nextHoliday -> NextHolidayCard(now, size)
        DashboardCard.todos -> TodosCard(now, size)
        DashboardCard.grades -> GradesCard(size)
    }
}

fun dashboardCardTitle(card: DashboardCard): String = when (card) {
    DashboardCard.today -> L("Heute")
    DashboardCard.weekPreview -> L("Woche")
    DashboardCard.participation -> L("Meldungen diese Woche")
    DashboardCard.streak -> L("Serie")
    DashboardCard.homework -> L("Hausaufgaben")
    DashboardCard.exams -> L("Prüfungen")
    DashboardCard.news -> L("Nachrichten")
    DashboardCard.absences -> L("Abwesenheiten")
    DashboardCard.nextLesson -> L("Nächste Stunde")
    DashboardCard.weekChart -> L("Wochenverlauf")
    DashboardCard.nextHoliday -> L("Nächste Ferien")
    DashboardCard.todos -> L("To-dos")
    DashboardCard.grades -> L("Notenschnitt")
}

fun dashboardCardSymbol(card: DashboardCard): String = when (card) {
    DashboardCard.today -> "sun.max"
    DashboardCard.weekPreview -> "calendar.day.timeline.left"
    DashboardCard.participation -> "hand.raised"
    DashboardCard.streak -> "flame"
    DashboardCard.homework -> "book"
    DashboardCard.exams -> "pencil.and.list.clipboard"
    DashboardCard.news -> "megaphone"
    DashboardCard.absences -> "person.crop.circle.badge.clock"
    DashboardCard.nextLesson -> "clock"
    DashboardCard.weekChart -> "chart.bar.xaxis"
    DashboardCard.nextHoliday -> "sun.horizon"
    DashboardCard.todos -> "checklist"
    DashboardCard.grades -> "graduationcap"
}

@Composable
private fun greeting(now: Instant): String {
    val model = LocalAppModel.current
    return when (now.atZone(model.calendar.zone).hour) {
        in 5 until 11 -> L("Guten Morgen")
        in 11 until 17 -> L("Hallo")
        in 17 until 22 -> L("Guten Abend")
        else -> L("Gute Nacht")
    }
}

@Composable
private fun GettingStartedCard(addSubject: (() -> Unit)?, connectUntis: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(modifier, title = L("Los geht's"), symbol = "sparkles") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingL)) {
            Mascot(MascotMood.wave, 88.dp)
            Text(
                L("Hi, ich bin Meldo! Lege deine Fächer und deinen Stundenplan an. Danach ordne ich jede Meldung automatisch der laufenden Stunde zu."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (addSubject != null) Button(onClick = addSubject, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(sfSymbol("plus"), null, Modifier.size(18.dp))
            Spacer(Modifier.width(DS.spacingS))
            Text(L("Erstes Fach anlegen"))
        }
        if (connectUntis != null) FilledTonalButton(onClick = connectUntis, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(sfSymbol("arrow.triangle.2.circlepath"), null, Modifier.size(18.dp))
            Spacer(Modifier.width(DS.spacingS))
            Text(L("Mit WebUntis verbinden"))
        }
    }
}

/**
 * A dashboard hero tile: same 28 dp shape, padding and quiet titleSmall header as
 * [Card], on a tonal [container] so the most important numbers stand out.
 */
@Composable
private fun HeroCard(
    title: String,
    symbol: String,
    container: Color,
    content: Color,
    symbolTint: Color = MaterialTheme.colorScheme.primary,
    body: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DS.cardShape,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                Icon(sfSymbol(symbol), null, Modifier.size(18.dp), tint = symbolTint)
                Text(title, style = MaterialTheme.typography.titleSmall, color = content.copy(alpha = 0.78f))
            }
            body()
        }
    }
}

/** Small tonal button inside a card (iOS: `.buttonStyle(.glass).controlSize(.small)`). */
@Composable
internal fun CardButton(text: String, symbol: String? = null, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(horizontal = DS.spacingL, vertical = DS.spacingXS),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        if (symbol != null) {
            Icon(sfSymbol(symbol), null, Modifier.size(16.dp))
            Spacer(Modifier.width(DS.spacingXS))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// region Day preview

/** Today's lessons with Untis changes in color; small shows only what's next. */
@Composable
private fun TodayCard(now: Instant, size: CardSize, addLesson: (() -> Unit)?) {
    val model = LocalAppModel.current
    val minuteNow = model.calendar.minuteOfDay(now)
    val blocks = model.timetableBlocks(now)
    val title = if (size == CardSize.small) {
        DateText.skeleton(now, "EEEdMMM", model.calendar)
    } else {
        DateText.skeleton(now, "EEEEdMMMM", model.calendar)
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val holiday = if (blocks.isEmpty()) model.holiday(now) else null
    Card(title = title, symbol = "sun.max") {
        when {
            holiday != null -> Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingM), verticalAlignment = Alignment.CenterVertically) {
                if (size != CardSize.small) Mascot(MascotMood.sleep, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                    Text(holiday.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(HolidayText.range(holiday, model.calendar), style = MaterialTheme.typography.bodyLarge.digits, color = secondary)
                    if (size != CardSize.small) NextLessonText(now)
                }
            }
            blocks.isEmpty() -> if (model.lessons.isEmpty() && model.data.untisTimetable == null) {
                Text(L("Noch keine Stunden im Stundenplan."), color = secondary)
                addLesson?.let { CardButton(L("Stunde hinzufügen"), "plus", it) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingM), verticalAlignment = Alignment.CenterVertically) {
                    if (size != CardSize.small) Mascot(MascotMood.sleep, 56.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                        Text(L("Heute kein Unterricht."), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        NextLessonText(now)
                    }
                }
            }
            size == CardSize.small -> {
                val upcoming = blocks.firstOrNull { it.endMinute > minuteNow && it.kind != TimetableBlock.Kind.cancelled }
                if (upcoming != null) {
                    Text(
                        if (upcoming.startMinute <= minuteNow) L("Jetzt") else L("Als Nächstes"),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary,
                    )
                    BlockRow(upcoming, isCurrent = upcoming.startMinute <= minuteNow, compact = true)
                } else {
                    Text(L("Schulschluss für heute."), color = secondary)
                }
            }
            else -> {
                val shown = if (size == CardSize.large) blocks else blocks.take(6)
                Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                    shown.forEach { block ->
                        BlockRow(
                            block,
                            isCurrent = block.startMinute <= minuteNow && minuteNow < block.endMinute,
                            compact = false,
                            showInfo = size == CardSize.large,
                            modifier = Modifier.alpha(if (block.endMinute <= minuteNow) 0.5f else 1f),
                        )
                    }
                }
                val more = blocks.size - shown.size
                if (more > 0) {
                    Text(L("+%lld weitere", more), style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
        }
    }
}

@Composable
private fun NextLessonText(now: Instant) {
    val model = LocalAppModel.current
    val next = model.nextLesson(now) ?: return
    val name = model.subject(next)?.name ?: L("Unbekanntes Fach")
    val day = nextDayText(next, now)
    val time = TimeText.clock(next.startMinute.toInt())
    Text(
        L("Nächste Stunde: %@, %@ %@", name, day, time),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun nextDayText(next: FfiOccurrence, now: Instant): String {
    val model = LocalAppModel.current
    val date = model.calendar.instant(next.date)
    if (model.calendar.localDate(date) == model.calendar.localDate(now).plusDays(1)) return L("morgen")
    return DateText.skeleton(date, "EEEE", model.calendar)
}

/** The school week at a glance: one mini column per day, changes in color. */
@Composable
private fun WeekPreviewCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    // This week; from Saturday on, the next one.
    val today = calendar.startOfDay(now)
    val index = calendar.mondayIndex(today)
    val monday = calendar.addDays(today, if (index >= 5) (7 - index).toLong() else (-index).toLong())
    val week = (0 until 5).map { calendar.addDays(monday, it.toLong()) }.map { it to model.timetableBlocks(it) }
    val all = week.flatMap { it.second }
    Card(title = L("Woche"), symbol = "calendar.day.timeline.left") {
        if (size == CardSize.small) {
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingXS)) {
                SummaryLine(all, TimetableBlock.Kind.exam, L("Prüfungen"))
                SummaryLine(all, TimetableBlock.Kind.roomChange, L("Raumänderungen"))
                SummaryLine(all, TimetableBlock.Kind.cancelled, L("Ausfälle"))
                if (all.none { it.kind != TimetableBlock.Kind.regular }) {
                    Text(L("Keine Änderungen."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXS), verticalAlignment = Alignment.Top) {
                week.forEach { (day, blocks) -> DayColumn(day, blocks, now, size, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SummaryLine(blocks: List<TimetableBlock>, kind: TimetableBlock.Kind, label: String) {
    val count = blocks.count { it.kind == kind }
    if (count == 0) return
    val style = CardBlockStyle.of(kind)
    Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXS), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(style.accent, CircleShape))
        Text("$count $label", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DayColumn(day: Instant, blocks: List<TimetableBlock>, now: Instant, size: CardSize, modifier: Modifier) {
    val model = LocalAppModel.current
    val isToday = model.calendar.isSameDay(day, now)
    val primary = MaterialTheme.colorScheme.primary
    val holiday = model.holiday(day)
    Column(
        modifier
            .background(if (isToday) primary.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .clearAndSetSemantics {
                contentDescription = DateText.skeleton(day, "EEEE", model.calendar)
                stateDescription = blocks.joinToString(", ") { it.subjectName }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            DateText.skeleton(day, "EEE", model.calendar),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        when {
            holiday != null -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (size == CardSize.large) 120.dp else 60.dp)
                    .background(CardBlockStyle.untisHoliday, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (size == CardSize.large) {
                    Text(
                        holiday.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentSize(unbounded = true).rotate(-90f),
                    )
                }
            }
            blocks.isEmpty() -> Text(
                L("frei"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            else -> blocks.forEach { block ->
                val style = CardBlockStyle.of(block.kind)
                if (size == CardSize.large) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 20.dp)
                            .background(style.fill, RoundedCornerShape(4.dp))
                            .then(
                                style.border?.let { Modifier.border(1.5.dp, it, RoundedCornerShape(4.dp)) } ?: Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            block.shortTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (block.kind == TimetableBlock.Kind.cancelled) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(style.accent, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

// endregion

// region Participation

@Composable
private fun WeekParticipationCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    var editingGoal by remember { mutableStateOf(false) }
    val total = (model.weekSummary(now)?.total ?: 0u).toInt()
    val goal = model.preferences.weeklyGoal
    // Hero tile: the accent's tonal container, a big ring and a display-size number.
    val secondary = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
    HeroCard(
        title = if (size == CardSize.small) L("Meldungen") else L("Meldungen diese Woche"),
        symbol = "hand.raised",
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXL), verticalAlignment = Alignment.CenterVertically) {
            val ring = if (size == CardSize.small) 96.dp else 120.dp
            Box(
                if (size == CardSize.small) Modifier.weight(1f) else Modifier,
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
                    ProgressRing(
                        progress = goal?.let { total.toDouble() / maxOf(it, 1) } ?: 0.0,
                        modifier = Modifier.fillMaxSize(),
                        lineWidth = if (size == CardSize.small) 10.dp else 12.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberText(
                            total,
                            if (size == CardSize.small) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                        )
                        if (goal != null) {
                            Text(L("von %lld", goal), style = MaterialTheme.typography.bodySmall, color = secondary)
                        }
                    }
                }
            }
            if (size != CardSize.small) {
                Column(verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                    if (goal != null) {
                        val remaining = maxOf(goal - total, 0)
                        Text(
                            if (remaining == 0) L("Wochenziel erreicht.") else L("Noch %lld bis zum Wochenziel.", remaining),
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondary,
                        )
                        if (remaining == 0) Mascot(MascotMood.cheer, 52.dp)
                    }
                    CardButton(if (goal == null) L("Wochenziel festlegen") else L("Ziel ändern")) { editingGoal = true }
                }
            }
        }
        if (size == CardSize.large) {
            Text(
                L("Meldungen zählen deine Beteiligung. Die mündliche Note vergibt deine Lehrkraft."),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
        }
    }
    if (editingGoal) WeeklyGoalSheet(now = now, onDismiss = { editingGoal = false })
}

/** School days in a row with a Meldung, and on-time homework. */
@Composable
private fun StreakCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    val streak = model.streak(now)
    val homework = model.homeworkStreak(now)
    val reduceMotion = rememberReduceMotion()
    val bounce = remember { Animatable(1f) }
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val defaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(streak) {
        if (first) { first = false; return@LaunchedEffect }
        if (reduceMotion) return@LaunchedEffect
        bounce.animateTo(1.25f, fastSpatial)
        bounce.animateTo(1f, defaultSpatial)
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val orange = StateColors.orange()
    val flameTint = if (streak > 0) orange else secondary
    // Hero tile: a warm tint of the flame over the card surface.
    HeroCard(
        title = L("Serie"),
        symbol = "flame",
        container = (if (streak > 0) orange.copy(alpha = 0.14f) else Color.Transparent)
            .compositeOver(MaterialTheme.colorScheme.surfaceContainer),
        content = MaterialTheme.colorScheme.onSurface,
        symbolTint = flameTint,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingL), verticalAlignment = Alignment.CenterVertically) {
            val badge = if (size == CardSize.small) 56.dp else 72.dp
            // The flame sits in an Expressive cookie shape.
            Box(
                Modifier
                    .size(badge)
                    .scale(bounce.value)
                    .background(flameTint.copy(alpha = 0.18f), MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sfSymbol("flame.fill"), null, Modifier.size(badge * 0.55f), tint = flameTint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NumberText(
                    streak,
                    if (size == CardSize.small) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                )
                Text(L("Schultage in Folge"), style = MaterialTheme.typography.bodyMedium, color = secondary)
            }
        }
        if (size != CardSize.small) {
            val tint = if (homework > 0) StateColors.orange() else secondary
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXS), verticalAlignment = Alignment.CenterVertically) {
                Icon(sfSymbol("book"), null, Modifier.size(16.dp), tint = tint)
                Text(L("%lld Tage pünktlich in Folge", homework), style = MaterialTheme.typography.bodyMedium, color = tint)
            }
        }
    }
}

// endregion

// region Exams, news, absences

@Composable
private fun UpcomingExamsCard(now: Instant, size: CardSize) {
    val model = LocalAppModel.current
    val exams = model.upcomingUntisExams(now)
    Card(title = L("Prüfungen"), symbol = "pencil.and.list.clipboard") {
        if (exams.isEmpty()) {
            Text(
                if (model.data.untis == null) L("Verbinde WebUntis, dann erscheinen hier deine Prüfungstermine.")
                else L("Keine Prüfungen eingetragen."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val limit = when (size) {
                CardSize.small -> 1
                CardSize.medium -> 3
                CardSize.large -> 8
            }
            Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                exams.take(limit).forEach { UntisExamRow(it, now, compact = size == CardSize.small) }
            }
        }
    }
}

@Composable
private fun SchoolNewsCard(size: CardSize) {
    val model = LocalAppModel.current
    val news = model.data.untisExtras?.news ?: emptyList()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Card(title = L("Nachrichten"), symbol = "megaphone") {
        if (news.isEmpty()) {
            Text(
                if (model.data.untis == null) L("Verbinde WebUntis, um Nachrichten deiner Schule zu sehen.")
                else L("Heute keine Nachrichten von der Schule."),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
        } else {
            val limit = when (size) {
                CardSize.large -> 6
                CardSize.medium -> 2
                CardSize.small -> 1
            }
            news.take(limit).forEach { message ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (message.subject.isNotEmpty()) {
                        Text(message.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (size != CardSize.small) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondary,
                            maxLines = if (size == CardSize.large) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsencesCard(@Suppress("UNUSED_PARAMETER") size: CardSize) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val absences = model.data.untisExtras?.absences ?: emptyList()
    val open = absences.count { !it.excused }
    Card(title = L("Abwesenheiten"), symbol = "person.crop.circle.badge.clock") {
        if (model.data.untis == null) {
            Text(
                L("Verbinde WebUntis, um deine Fehlzeiten zu sehen."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.Bottom) {
                NumberText(absences.size, MaterialTheme.typography.headlineLarge)
                Text(
                    L("in diesem Schuljahr"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (open > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingXS), verticalAlignment = Alignment.CenterVertically) {
                    Icon(sfSymbol("exclamationmark.circle"), null, Modifier.size(16.dp), tint = StateColors.red())
                    Text(L("%lld nicht entschuldigt", open), style = MaterialTheme.typography.bodyMedium, color = StateColors.red())
                }
            }
            rememberLink(Link.Page("absences"))?.let { CardLink(L("Alle"), onClick = it) }
        }
    }
}

// endregion

// region Weekly goal

/** Sets the weekly Melde target, with a suggestion from the Rust core. Also opened from Einstellungen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyGoalSheet(now: Instant, onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val suggestion = remember(now) { model.suggestedWeeklyGoal(now) }
    var goal by remember { mutableIntStateOf(model.preferences.weeklyGoal ?: suggestion ?: 10) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MeldoSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = DS.spacingL).padding(bottom = DS.spacingXL),
            verticalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) }
                Text(
                    L("Wochenziel"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    model.setWeeklyGoal(goal)
                    onDismiss()
                }, shapes = ButtonDefaults.shapes()) { Text(L("Sichern"), fontWeight = FontWeight.SemiBold) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, DS.rowShape)
                    .padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                horizontalArrangement = Arrangement.spacedBy(DS.spacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(L("%lld Meldungen pro Woche", goal), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                FilledTonalIconButton(onClick = { if (goal > 1) goal -= 1 }, shapes = IconButtonDefaults.shapes(), enabled = goal > 1) {
                    Icon(sfSymbol("minus"), L("Weniger"))
                }
                FilledTonalIconButton(onClick = { if (goal < 200) goal += 1 }, shapes = IconButtonDefaults.shapes(), enabled = goal < 200) {
                    Icon(sfSymbol("plus"), L("Mehr"))
                }
            }
            Text(
                if (suggestion != null) L("Vorschlag aus deinen letzten Wochen: %lld", suggestion)
                else L("Nach einer Woche mit Meldungen schlagen wir dir ein realistisches Ziel vor."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DS.spacingL),
            )
            if (suggestion != null && suggestion != goal) {
                TextButton(onClick = { goal = suggestion }, shapes = ButtonDefaults.shapes()) { Text(L("Vorschlag übernehmen")) }
            }
            if (model.preferences.weeklyGoal != null) {
                TextButton(
                    onClick = {
                        model.setWeeklyGoal(null)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(L("Ziel entfernen")) }
            }
        }
    }
}

// endregion
