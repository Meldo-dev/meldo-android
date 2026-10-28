@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.timetable

import app.meldeplanner.android.ui.theme.MeldoDatePickerDialog
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.model.hasSaturdayLessons
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.MeldoMenu
import app.meldeplanner.android.ui.components.MeldoMenuItem
import app.meldeplanner.android.ui.components.WeekdayText
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.LocalBottomChrome
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import app.meldeplanner.android.ui.timetable.isRegularWidth
import app.meldeplanner.android.ui.timetable.TimetableLegend
import app.meldeplanner.android.ui.timetable.EmptyStateCard
import app.meldeplanner.android.ui.navigation.Link
import app.meldeplanner.android.ui.navigation.rememberLink
import app.meldeplanner.android.ui.navigation.LocalFeatures

/** The views of the timetable menu. Raw values match iOS `@AppStorage("timetable.mode")`. */
enum class TimetableMode(val dayCount: Int, val symbol: String) {
    day(1, "square"),
    twoDays(2, "rectangle.split.2x1"),
    threeDays(3, "rectangle.split.3x1"),
    week(5, "calendar");

    val title: String
        get() = when (this) {
            day -> L("Tag")
            twoDays -> L("2 Tage")
            threeDays -> L("3 Tage")
            week -> L("Woche")
        }
}

private const val MODE_KEY = "timetable.mode"
private const val WEEK_MIN = -26
private const val WEEK_MAX = 52
private const val DAY_MIN = -200
private const val DAY_MAX = 400

/** One pager for a view: `start` is the page that shows `anchor` (day views). */
private class PagerSetup(val state: PagerState, val start: Int, val anchor: Int)

private fun Context.uiPrefs() = getSharedPreferences("meldo.ui", Context.MODE_PRIVATE)

private fun Context.reduceMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Timetable laid out like WebUntis, in Meldo's design: period numbers with bell
 * times, blocks as long as the lessons (teacher, subject, room), changes in Untis
 * colors, parallel groups folded into one block with a count beside it. The menu
 * (top right) switches between day, 2 days, 3 days and week and jumps to a date;
 * swiping moves through the days or weeks. Port of TimetableView.swift.
 */
@Composable
fun TimetableScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val regular = isRegularWidth()

    var mode by remember {
        mutableStateOf(
            context.uiPrefs().getString(MODE_KEY, null)
                ?.let { raw -> TimetableMode.entries.firstOrNull { it.name == raw } } ?: TimetableMode.week,
        )
    }
    var weekPage by rememberSaveable { mutableIntStateOf(0) }
    /** School days from today to the first shown day (day, 2 and 3 day views). */
    var dayPage by rememberSaveable { mutableIntStateOf(0) }
    var dayAnchor by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<TimetableBlock?>(null) }
    var choosingDate by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showingColors by remember { mutableStateOf(false) }

    val hasSaturday = model.hasSaturdayLessons
    val n = mode.dayCount
    val setup = remember(mode, if (mode == TimetableMode.week) 0 else dayAnchor) {
        if (mode == TimetableMode.week) {
            PagerSetup(PagerState(currentPage = weekPage.coerceIn(WEEK_MIN, WEEK_MAX) - WEEK_MIN) { WEEK_MAX - WEEK_MIN + 1 }, -WEEK_MIN, 0)
        } else {
            val anchor = dayAnchor.coerceIn(DAY_MIN, DAY_MAX)
            val start = (anchor - DAY_MIN) / n
            val count = start + (DAY_MAX - anchor) / n + 1
            PagerSetup(PagerState(currentPage = start) { count }, start, anchor)
        }
    }
    LaunchedEffect(setup) {
        snapshotFlow { setup.state.currentPage }.collect { page ->
            if (mode == TimetableMode.week) weekPage = page - setup.start else dayPage = setup.anchor + (page - setup.start) * n
        }
    }

    fun days(forPage: Int): List<Instant> = if (mode == TimetableMode.week) {
        val monday = monday(model, forPage - setup.start)
        (0 until if (hasSaturday) 6 else 5).map { model.calendar.addDays(monday, it.toLong()) }
    } else {
        val first = setup.anchor + (forPage - setup.start) * n
        (0 until n).map { schoolDay(model, first + it, hasSaturday) }
    }

    val shownDays: List<Instant> = if (mode == TimetableMode.week) {
        val monday = monday(model, weekPage)
        (0 until if (hasSaturday) 6 else 5).map { model.calendar.addDays(monday, it.toLong()) }
    } else {
        (0 until n).map { schoolDay(model, dayPage + it, hasSaturday) }
    }

    val detail = when (mode) {
        TimetableMode.day -> BlockDetail.full
        TimetableMode.twoDays, TimetableMode.threeDays -> BlockDetail.medium
        TimetableMode.week -> if (regular) BlockDetail.medium else BlockDetail.compact
    }

    /** Shows [week] / [day] (school days from today), sliding when the page is reachable. */
    fun jump(week: Int, day: Int) {
        val animate = !context.reduceMotion()
        if (mode == TimetableMode.week) {
            val target = week.coerceIn(WEEK_MIN, WEEK_MAX) - WEEK_MIN
            scope.launch { if (animate) setup.state.animateScrollToPage(target) else setup.state.scrollToPage(target) }
            dayPage = day
        } else {
            weekPage = week.coerceIn(WEEK_MIN, WEEK_MAX)
            val diff = day - setup.anchor
            val target = setup.start + diff / n
            if (diff % n == 0 && target in 0 until setup.state.pageCount) {
                scope.launch { if (animate) setup.state.animateScrollToPage(target) else setup.state.scrollToPage(target) }
            } else {
                dayPage = day.coerceIn(DAY_MIN, DAY_MAX)
                dayAnchor = dayPage
            }
        }
    }

    fun goTo(date: Instant) {
        val calendar = model.calendar
        val target = calendar.startOfDay(date)
        val thisMonday = monday(model, 0)
        val targetMonday = calendar.weekStart(target)
        val weeks = (ChronoUnit.DAYS.between(calendar.localDate(thisMonday), calendar.localDate(targetMonday)) / 7).toInt()
        jump(weeks, schoolDayOffset(model, target, hasSaturday))
    }

    fun createLesson() {
        val weekday = model.calendar.mondayIndex(shownDays.firstOrNull() ?: model.currentDate)
        navigator.push { LessonEditorScreen(lesson = null, weekday = weekday, onDismiss = { navigator.pop() }) }
    }

    val manageSubjects = rememberLink(Link.Page("subjects"))
    val features = LocalFeatures.current

    MeldoScreen(
        title = L("Stundenplan"),
        largeTitle = false,
        actions = {
            // Like Untis and the iOS menu: navigation, the view, and the rarer actions.
            Box {
                IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) { Icon(sfSymbol("ellipsis"), L("Mehr")) }
                MeldoMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    groups = listOf(
                        listOf(
                            MeldoMenuItem(L("Gehe zu Datum"), "calendar.badge.clock") { choosingDate = true },
                            MeldoMenuItem(L("Heute"), "smallcircle.filled.circle") { jump(0, 0) },
                        ),
                        TimetableMode.entries.map { option ->
                            MeldoMenuItem(option.title, option.symbol, checked = option == mode) {
                                if (option != mode) {
                                    dayAnchor = dayPage
                                    mode = option
                                    context.uiPrefs().edit().putString(MODE_KEY, option.name).apply()
                                }
                            }
                        },
                        listOfNotNull(
                            MeldoMenuItem(L("Stunde hinzufügen"), "plus", enabled = model.subjects.isNotEmpty()) { createLesson() },
                            manageSubjects?.let { MeldoMenuItem(L("Fächer"), "books.vertical") { it() } },
                            // The colors are explained here and in the tour, not in a bar over the grid.
                            MeldoMenuItem(L("Was bedeuten die Farben?"), "paintpalette") { showingColors = true },
                        ),
                    ),
                    labels = listOf(null, L("Ansicht"), null),
                )
            }
        },
    ) { padding ->
        // The grid scrolls under the translucent bars: each page gets the padding as its
        // scroll padding (the KW/day header stays pinned under the top bar).
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = setup.state,
                modifier = Modifier.fillMaxSize(),
                key = { it },
            ) { page ->
                TimetableDaysPage(days = days(page), detail = detail, onSelect = { selected = it }, contentPadding = padding)
            }
            if (model.lessons.isEmpty() && model.data.untisTimetable == null) {
                Box(Modifier.padding(padding)) {
                    // First steps when there is nothing to show yet.
                    EmptyStateCard(
                        symbol = "tablecells",
                        title = L("Keine Stunden"),
                        text = if (model.subjects.isEmpty()) {
                            L("Lege zuerst unter „Fächer“ deine Fächer an.")
                        } else {
                            val day = WeekdayText.full(model.calendar.mondayIndex(shownDays.firstOrNull() ?: model.currentDate) % 7)
                            L("Füge die Stunden für %@ hinzu.", day)
                        },
                        action = {
                            if (model.subjects.isEmpty() && manageSubjects != null) {
                                Button(onClick = { manageSubjects() }, shapes = ButtonDefaults.shapes()) {
                                    Icon(sfSymbol("books.vertical"), null)
                                    Text(L("Fächer"), Modifier.padding(start = DS.spacingS))
                                }
                            } else {
                                Button(onClick = { createLesson() }, shapes = ButtonDefaults.shapes()) {
                                    Icon(sfSymbol("plus"), null)
                                    Text(L("Stunde hinzufügen"), Modifier.padding(start = DS.spacingS))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showingColors) {
        MeldoAlertDialog(
            onDismissRequest = { showingColors = false },
            title = { Text(L("Was bedeuten die Farben?")) },
            text = { TimetableLegend(vertical = true) },
            confirmButton = { TextButton(onClick = { showingColors = false }) { Text(L("Fertig")) } },
        )
    }

    selected?.let { block ->
        TimetableBlockSheet(
            block = block,
            onDismiss = { selected = null },
            onHomework = if (features.screen(Link.NewTask(TaskKind.homework)) == null) null else { subjectID ->
                selected = null
                features.screen(Link.NewTask(TaskKind.homework, subjectID))?.let(navigator::push)
            },
            onEdit = { lesson ->
                selected = null
                navigator.push { LessonEditorScreen(lesson = lesson, weekday = lesson.weekday, onDismiss = { navigator.pop() }) }
            },
        )
    }

    if (choosingDate) {
        GoToDateDialog(
            start = shownDays.firstOrNull() ?: model.currentDate,
            model = model,
            onDismiss = { choosingDate = false },
            onGo = { goTo(it) },
        )
    }
}

/** Jump to any date, like "Go to Date" in Untis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoToDateDialog(start: Instant, model: AppModel, onDismiss: () -> Unit, onGo: (Instant) -> Unit) {
    val initial = model.calendar.localDate(start).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = remember { DatePickerState(locale = Localization.locale, initialSelectedDateMillis = initial) }
    MeldoDatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onGo(model.calendar.instant(date))
                }
                onDismiss()
            }) { Text(L("Anzeigen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) } },
    ) {
        DatePicker(
            state = state,
            title = { Text(L("Gehe zu Datum"), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

// region Paging helpers (TimetableView.swift)

/** Monday of the week [offset] weeks from this one. */
private fun monday(model: AppModel, offset: Int): Instant {
    val thisMonday = model.calendar.weekStart(model.currentDate)
    return model.calendar.addDays(thisMonday, offset * 7L)
}

private fun isSchoolDay(model: AppModel, date: Instant, hasSaturday: Boolean): Boolean {
    val index = model.calendar.mondayIndex(date)
    return index < 5 || (index == 5 && hasSaturday)
}

/** The school day [offset] school days from today (weekends skipped). */
private fun schoolDay(model: AppModel, offset: Int, hasSaturday: Boolean): Instant {
    val calendar = model.calendar
    var day = calendar.startOfDay(model.currentDate)
    while (!isSchoolDay(model, day, hasSaturday)) day = calendar.addDays(day, 1)
    var remaining = kotlin.math.abs(offset)
    val direction = if (offset < 0) -1L else 1L
    while (remaining > 0) {
        day = calendar.addDays(day, direction)
        if (isSchoolDay(model, day, hasSaturday)) remaining -= 1
    }
    return day
}

/** School days between today and [date] (negative for the past). */
private fun schoolDayOffset(model: AppModel, date: Instant, hasSaturday: Boolean): Int {
    val calendar = model.calendar
    var day = calendar.startOfDay(model.currentDate)
    while (!isSchoolDay(model, day, hasSaturday)) day = calendar.addDays(day, 1)
    val direction = if (date < day) -1 else 1
    var count = 0
    while (!calendar.isSameDay(day, date) && kotlin.math.abs(count) < 400) {
        day = calendar.addDays(day, direction.toLong())
        if (isSchoolDay(model, day, hasSaturday)) count += direction
        if (if (direction > 0) day > date else day < date) break
    }
    return count
}

// endregion
