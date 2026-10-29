package app.meldeplanner.android.ui.features.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.BellPeriod
import app.meldeplanner.android.data.Holiday
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.HolidayText
import app.meldeplanner.android.model.TimetableBlock
import app.meldeplanner.android.model.bellSchedule
import app.meldeplanner.android.model.holiday
import app.meldeplanner.android.model.timetableBlocks
import app.meldeplanner.android.model.untisVisibleUntil
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.sfSymbol
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.IsoFields
import app.meldeplanner.android.ui.timetable.UntisColors
import app.meldeplanner.android.ui.timetable.TimetableDates
import app.meldeplanner.android.ui.timetable.FitText
import app.meldeplanner.android.ui.timetable.BlockStyle
import app.meldeplanner.android.ui.timetable.isRegularWidth
import app.meldeplanner.android.ui.timetable.EmptyStateCard

/** How much a block shows: week on a phone, week on a tablet / 2–3 days, one day. */
enum class BlockDetail { compact, medium, full }

private val tabularDigits = TextStyle(fontFeatureSettings = "tnum")

/** Vertical scale of the grid: which minutes are shown and how tall they are. */
internal data class GridMetrics(val start: Int, val end: Int, val dpPerMinute: Float) {
    fun y(minute: Int): Dp = ((minute.coerceIn(start, end) - start) * dpPerMinute).dp
    val height: Dp get() = y(end)

    companion object {
        /** From the first bell to the last block of the shown days (at least six periods). */
        fun make(bells: List<BellPeriod>, blocks: List<TimetableBlock>, dpPerMinute: Float): GridMetrics {
            val first = minOf(bells.firstOrNull()?.startMinute ?: (8 * 60), blocks.minOfOrNull { it.startMinute } ?: (8 * 60))
            val sixth = bells.take(6).lastOrNull()?.endMinute ?: (13 * 60)
            val last = maxOf(sixth, blocks.maxOfOrNull { it.endMinute } ?: sixth)
            // Minutes come from other devices too. Kept within one day, so a nonsense value can't make
            // the grid taller than a layout can measure.
            val day = 24 * 60
            val top = first.coerceIn(0, day)
            return GridMetrics(top, last.coerceIn(top, day), dpPerMinute)
        }
    }
}

/** Ticks every minute, like SwiftUI's `TimelineView(.everyMinute)`. */
@Composable
private fun rememberMinuteTick(model: AppModel): Instant {
    var now by remember { mutableStateOf(model.currentDate) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = model.currentDate
            now = current
            delay((60 - current.epochSecond % 60) * 1000L)
        }
    }
    return now
}

/**
 * One page: the week number in the corner (like "W 45 / Nov" in Untis), a
 * header per day, and the scrolling grid.
 */
@Composable
internal fun TimetableDaysPage(
    days: List<Instant>,
    detail: BlockDetail,
    onSelect: (TimetableBlock) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val model = LocalAppModel.current
    val calendar = model.calendar
    val regular = isRegularWidth()
    val scale = if (detail == BlockDetail.full) 1.7f else if (regular) 1.5f else 1.3f
    val data = model.data
    val bells = remember(data) { model.bellSchedule }
    val blocksByDay = remember(data, days) {
        days.map { day -> model.timetableBlocks(day) }
    }
    val holidays = remember(data, days) { days.map { model.holiday(it) } }
    val metrics = remember(bells, blocksByDay, scale) {
        GridMetrics.make(bells, blocksByDay.flatten(), scale)
    }
    val first = days.firstOrNull() ?: model.currentDate
    val week = calendar.localDate(first).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val layoutDirection = LocalLayoutDirection.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + DS.spacingS,
                end = contentPadding.calculateEndPadding(layoutDirection) + DS.spacingS,
            ),
    ) {
        // The header stays pinned under the top bar; only the grid below scrolls.
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = contentPadding.calculateTopPadding() + DS.spacingXS, bottom = DS.spacingS),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.width(TimeAxisWidth)) {
                Text(
                    L("KW %lld", week),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(TimetableDates.monthShort(first, model.calendar), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            for (day in days) {
                DayHeader(day, wide = detail == BlockDetail.full, Modifier.weight(1f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On a tall screen the day is stretched to fill the height: no empty band under
            // the last lesson (the Melden button floats over the grid).
            val minutes = (metrics.end - metrics.start).coerceAtLeast(0)
            val room = contentPadding.calculateBottomPadding() + DS.spacingS
            val fill = if (minutes > 0) (maxHeight - room).value / minutes else 0f
            val metrics = if (fill > metrics.dpPerMinute) metrics.copy(dpPerMinute = fill) else metrics
            // Blocks have a fixed height per minute; the detail sheet shows everything larger.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.35f))) {
                // Bottom padding inside the scroll: lessons pass under the navigation bar and the
                // Melden button, and the last period still scrolls clear of them.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
                        TimeAxis(bells, metrics)
                        days.forEachIndexed { index, day ->
                            DayGrid(day, blocksByDay[index], holidays[index], bells, metrics, detail, onSelect, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(DS.spacingS))
                }
            }
            val limit = model.untisVisibleUntil
            if (limit != null && first > limit) {
                Box(Modifier.padding(bottom = contentPadding.calculateBottomPadding())) { LimitNotice(limit) }
            }
        }
    }
}

/** The school only lets students see the timetable up to a certain day. */
@Composable
private fun LimitNotice(limit: Instant) {
    val model = LocalAppModel.current
    EmptyStateCard(
        symbol = "lock",
        title = L("Noch nicht freigegeben"),
        text = L("Deine Schule zeigt den Stundenplan nur bis %@.", TimetableDates.dayMonth(limit, model.calendar)),
    )
}

@Composable
private fun DayHeader(date: Instant, wide: Boolean, modifier: Modifier = Modifier) {
    val model = LocalAppModel.current
    val isToday = model.calendar.isSameDay(date, model.currentDate)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier.semantics(mergeDescendants = true) { selected = isToday },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            TimetableDates.weekday(date, model.calendar, wide),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.SemiBold else null,
            color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        // Today: the number on a filled accent circle, so it reads at a glance.
        Box(
            Modifier
                .size(34.dp)
                .background(if (isToday) accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                TimetableDates.dayNumber(date, model.calendar),
                style = MaterialTheme.typography.titleLarge.merge(tabularDigits),
                fontWeight = FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

internal val TimeAxisWidth = 46.dp

/** Period numbers with their bell times, like the left column in Untis. */
@Composable
private fun TimeAxis(bells: List<BellPeriod>, metrics: GridMetrics) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    // Every block already says its time.
    Box(Modifier.width(TimeAxisWidth).height(metrics.height).clearAndSetSemantics {}) {
        bells.forEachIndexed { index, bell ->
            val next = bells.getOrNull(index + 1)?.startMinute
            if (bell.startMinute >= metrics.start && bell.endMinute <= metrics.end) {
                Column(
                    Modifier
                        .offset(y = metrics.y(bell.startMinute))
                        .width(TimeAxisWidth)
                        .height(metrics.y(bell.endMinute) - metrics.y(bell.startMinute)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(TimeText.clock(bell.startMinute), style = MaterialTheme.typography.labelSmall.merge(tabularDigits), color = secondary, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(bell.name, style = MaterialTheme.typography.titleLarge.merge(tabularDigits), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (next != bell.endMinute) {
                        Text(TimeText.clock(bell.endMinute), style = MaterialTheme.typography.labelSmall.merge(tabularDigits), color = secondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * One day as a column: period lines, lesson blocks, holidays in blue and a line
 * for the current time.
 */
@Composable
private fun DayGrid(
    date: Instant,
    blocks: List<TimetableBlock>,
    holiday: Holiday?,
    bells: List<BellPeriod>,
    metrics: GridMetrics,
    detail: BlockDetail,
    onSelect: (TimetableBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = LocalAppModel.current
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val gridLines = remember(bells, metrics) {
        bells.flatMap { listOf(it.startMinute, it.endMinute) }.toSet()
            .filter { it >= metrics.start && it <= metrics.end }.sorted()
    }
    // Gaps between consecutive bells that are within the shown range.
    val breaks = remember(bells, metrics) {
        bells.zipWithNext().mapNotNull { (a, b) ->
            val gap = b.startMinute - a.endMinute
            if (gap >= 10 && a.endMinute >= metrics.start && b.startMinute <= metrics.end) a.endMinute to b.startMinute else null
        }
    }
    val now = rememberMinuteTick(model)
    val dayLabel = TimetableDates.weekdayDayMonth(date, model.calendar)
    BoxWithConstraints(
        modifier
            .height(metrics.height)
            .semantics {
                isTraversalGroup = true
                contentDescription = dayLabel
            },
    ) {
        val width = maxWidth
        for (minute in gridLines) {
            Box(Modifier.offset(y = metrics.y(minute)).fillMaxWidth().height(0.5.dp).background(lineColor))
        }
        if (detail == BlockDetail.full) {
            for ((start, end) in breaks) {
                Box(
                    Modifier.offset(y = metrics.y(start)).fillMaxWidth().height(metrics.y(end) - metrics.y(start)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        L("Pause · %lld min", end - start),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (holiday != null) {
            HolidayColumn(holiday, detail)
        } else {
            for (block in blocks) {
                val blockWidth = width * block.widthShare.toFloat()
                val narrow = block.widthShare < 0.99 && detail != BlockDetail.full
                val hint = if (block.alternatives.isEmpty()) L("Zum Bearbeiten tippen") else L("Tippen, um deinen Kurs zu wählen")
                val description = blockAccessibilityText(block)
                Box(
                    Modifier
                        .offset(x = width * block.xShare.toFloat(), y = metrics.y(block.startMinute) + 1.5.dp)
                        .size(
                            width = maxOf(blockWidth - 3.dp, 10.dp),
                            height = maxOf(metrics.y(block.endMinute) - metrics.y(block.startMinute) - 3.dp, 18.dp),
                        )
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                            onClick(label = hint) { onSelect(block); true }
                        }
                        .clickable { onSelect(block) },
                ) {
                    TimetableBlockView(block, detail, narrow = narrow, tiny = blockWidth < 30.dp)
                }
            }
        }
        val minute = model.calendar.minuteOfDay(now)
        if (model.calendar.isSameDay(now, date) && minute >= metrics.start && minute <= metrics.end) {
            Box(
                Modifier
                    .offset(y = metrics.y(minute))
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/** Holidays fill the whole column in blue, like WebUntis. */
@Composable
private fun HolidayColumn(holiday: Holiday, detail: BlockDetail) {
    val model = LocalAppModel.current
    Box(
        Modifier
            .fillMaxSize()
            .background(UntisColors.holiday, RoundedCornerShape(BlockRadius))
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(DS.spacingXS),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DS.spacingXS),
        ) {
            Icon(sfSymbol("lock.fill"), null, tint = Color.White)
            FitText(
                holiday.name,
                style = if (detail == BlockDetail.compact) {
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                } else {
                    MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
                },
                color = Color.White,
                // One line per word, so narrow columns shrink a word instead of breaking it ("Herbstferie-n").
                maxLines = holiday.name.trim().split(' ').count { it.isNotEmpty() }.coerceIn(1, 3),
                minScale = 0.5f,
            )
            FitText(
                HolidayText.range(holiday, model.calendar),
                style = MaterialTheme.typography.labelSmall.merge(tabularDigits).copy(textAlign = TextAlign.Center),
                color = Color.White,
                minScale = 0.5f,
            )
        }
    }
}

private fun blockAccessibilityText(block: TimetableBlock): String {
    val parts = mutableListOf(block.subjectName, TimeText.range(block.startMinute, block.endMinute))
    if (block.teacher.isNotEmpty()) parts.add(block.teacher)
    block.room?.let { parts.add(it) }
    BlockStyle.label(block.kind)?.let { parts.add(it) }
    if (block.roomChanged && block.kind != TimetableBlock.Kind.roomChange) parts.add(L("Raumänderung"))
    if (block.hasHomework) parts.add(L("Hausaufgabe fällig"))
    if (block.alternatives.isNotEmpty()) parts.add(L("Parallele Kurse zur Auswahl"))
    return parts.joinToString(", ")
}

/**
 * A lesson block as WebUntis draws it: teacher, subject, room; changed room or
 * teacher on a green label; exams brown-orange with a yellow frame; cancelled
 * crossed out with a red frame. Small icons for homework and notes, and a
 * narrow column with the number of parallel groups.
 */
@Composable
private fun TimetableBlockView(block: TimetableBlock, detail: BlockDetail, narrow: Boolean = false, tiny: Boolean = false) {
    val style = BlockStyle.of(block.kind)
    val compact = detail == BlockDetail.compact
    val shape = RoundedCornerShape(if (compact) BlockRadiusCompact else BlockRadius)
    val primary = MaterialTheme.colorScheme.onSurface
    val cancelled = block.kind == TimetableBlock.Kind.cancelled
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(style.fill, shape)
                .then(style.border?.let { Modifier.border(2.dp, it, shape) } ?: Modifier)
                // Large text must not run into the next lesson.
                .clip(shape),
        ) {
            Row(Modifier.fillMaxSize()) {
                // The thin bar inside the left edge, like WebUntis (no room for it in a narrow strip).
                if (!tiny) {
                    Box(
                        Modifier
                            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(style.stripe, CircleShape),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = if (tiny) 2.dp else if (compact) 4.dp else 6.dp, end = if (tiny) 2.dp else 3.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (detail == BlockDetail.full) {
                        Text(
                            block.subjectName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            color = primary,
                        )
                    }
                    // The subject leads in every block, so the week scans by subject (as on iOS);
                    // the teacher (green when substituted) and the room follow.
                    if (detail != BlockDetail.full) {
                        BlockLabel(
                            block.shortTitle,
                            style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                                .copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                            highlighted = false,
                            cancelled = cancelled,
                            // Events carry their name, not a subject code: give it room to wrap.
                            maxLines = if (block.isEvent || tiny) 4 else 1,
                        )
                    }
                    if (block.teacher.isNotEmpty() && !tiny && (!narrow || block.teacherChanged)) {
                        BlockLabel(
                            block.teacher,
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            highlighted = block.teacherChanged,
                            cancelled = cancelled,
                        )
                    }
                    block.room?.takeIf { !tiny || block.roomChanged }?.let { room ->
                        BlockLabel(
                            room,
                            style = (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
                                .merge(tabularDigits),
                            highlighted = block.roomChanged,
                            cancelled = cancelled,
                        )
                    }
                    if (detail == BlockDetail.full) {
                        block.info?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = primary.copy(alpha = 0.8f), maxLines = 2)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val iconTint = primary.copy(alpha = 0.75f)
                    val iconSize = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.fontSize.toDp() }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (block.kind == TimetableBlock.Kind.exam) {
                            Icon(sfSymbol("exclamationmark.triangle.fill"), null, Modifier.size(iconSize), tint = iconTint)
                        }
                        if (block.hasHomework) {
                            Icon(sfSymbol("house.fill"), null, Modifier.size(iconSize), tint = iconTint)
                        }
                        Spacer(Modifier.weight(1f))
                        if (block.info != null && detail != BlockDetail.full) {
                            Icon(sfSymbol("info.circle.fill"), null, Modifier.size(iconSize), tint = iconTint)
                        }
                    }
                }
            }
        }
        if (block.alternatives.isNotEmpty()) {
            Box(
                Modifier
                    .width(if (compact) 12.dp else 16.dp)
                    .fillMaxHeight()
                    .background(UntisColors.lesson.copy(alpha = 0.7f), RoundedCornerShape(if (compact) BlockRadiusCompact else BlockRadius)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // "+1": more courses at this time (a bare "1" read like a lesson number).
                    "+${block.alternatives.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Corner radius of lesson blocks: rounder than WebUntis, a bit tighter in the narrow week view. */
private val BlockRadius = 10.dp
private val BlockRadiusCompact = 8.dp

/** One line of the block; changed values sit on a green label like in Untis. */
@Composable
private fun BlockLabel(
    text: String,
    style: TextStyle,
    highlighted: Boolean,
    cancelled: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    FitText(
        text,
        style = style,
        color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface,
        strikethrough = cancelled,
        maxLines = maxLines,
        minScale = if (maxLines > 1) 0.6f else 0.7f,
        modifier = modifier
            .then(if (highlighted) Modifier.background(UntisColors.change, RoundedCornerShape(4.dp)) else Modifier)
            .padding(horizontal = if (highlighted) 3.dp else 0.dp),
    )
}
