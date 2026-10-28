package app.meldeplanner.android.surfaces.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.surfaces.SurfaceIntents
import uniffi.melde_ffi.FfiOccurrence
import uniffi.melde_ffi.timetableOccurrencesOn
import java.time.Instant
import app.meldeplanner.android.model.meldoModel

/**
 * Home screen widget: current or next lesson, this week's Meldungen against
 * the goal, and a one-tap Melden button during lessons. Port of
 * meldo-ios/MeldeWidgets/MeldoTodayWidget.swift (small and medium).
 */
class MeldoTodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Family.SMALL, Family.MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        prepareWidgetData(context)
        provideContent {
            MeldoGlanceTheme {
                val snapshot = rememberSnapshot()
                TodayWidgetView(
                    if (snapshot.available) TodayEntry.of(snapshot.model, snapshot.data, snapshot.now) else TodayEntry.unavailable,
                    WidgetStyle.of(snapshot.data.preferences),
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            MeldoGlanceTheme {
                TodayWidgetView(TodayEntry.placeholder(), WidgetStyle.of(context.meldoPreferences()))
            }
        }
    }
}

class MeldoTodayWidgetReceiver : MeldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeldoTodayWidget()
}

internal fun Context.meldoPreferences() = meldoModel.data.preferences

internal data class TodayEntry(
    val isAvailable: Boolean,
    val current: LessonInfo?,
    val next: LessonInfo?,
    val lessonCount: Int,
    val weekTotal: Int,
    val goal: Int?,
    val homeworkDue: Int,
    val laterToday: List<LessonInfo>,
    val now: Instant,
) {
    data class LessonInfo(val name: String, val color: SubjectColor, val symbol: String, val start: Instant, val end: Instant)

    companion object {
        val unavailable = TodayEntry(false, null, null, 0, 0, null, 0, emptyList(), Instant.now())

        /** The widget gallery preview, as on iOS. */
        fun placeholder(): TodayEntry {
            val now = Instant.now()
            return TodayEntry(
                isAvailable = true,
                current = LessonInfo("Mathe", SubjectColor.blue, "function", now, now.plusSeconds(1800)),
                next = null, lessonCount = 2, weekTotal = 9, goal = 12, homeworkDue = 1, laterToday = emptyList(), now = now,
            )
        }

        fun of(model: AppModel, data: AppData, date: Instant): TodayEntry {
            val calendar = model.calendar
            val day = calendar.startOfDay(date)
            fun info(o: FfiOccurrence): LessonInfo {
                val subject = MeldeEngine.subject(data, o.subjectId)
                val lessonDay = calendar.instant(o.date)
                return LessonInfo(
                    name = subject?.name ?: L("Unterricht"),
                    color = subject?.color ?: SubjectColor.blue,
                    symbol = subject?.symbolName ?: "book.closed",
                    start = calendar.at(lessonDay, o.startMinute.toInt()),
                    end = calendar.at(lessonDay, o.endMinute.toInt()),
                )
            }
            val current = MeldeEngine.participationLesson(data, date, calendar)
            val next = MeldeEngine.nextLesson(data, date, calendar)
            val todays = runCatching {
                timetableOccurrencesOn(MeldeEngine.timetable(data, calendar), calendar.ffiDate(date))
            }.getOrDefault(emptyList())
            val minute = calendar.minuteOfDay(date)
            val later = todays.filter { !it.cancelled && it.startMinute.toInt() > minute }.take(3).map(::info)
            val tomorrow = calendar.addDays(day, 1)
            val homework = data.tasks.count { t ->
                t.kind == TaskKind.homework && !t.isDone && (t.dueDate?.let { it <= tomorrow } ?: false)
            }
            return TodayEntry(
                isAvailable = true,
                current = current?.let(::info),
                next = next?.let(::info),
                lessonCount = current?.let { MeldeEngine.count(data, it.lessonId, date, calendar) } ?: 0,
                weekTotal = (MeldeEngine.weekSummary(data, date, calendar)?.total ?: 0u).toInt(),
                goal = data.preferences.weeklyGoal,
                homeworkDue = homework,
                laterToday = later,
                now = date,
            )
        }
    }
}


@Composable
private fun TodayWidgetView(entry: TodayEntry, style: WidgetStyle) {
    WidgetContainer(style, glow = true) {
        when {
            !entry.isAvailable -> Unavailable(style)
            Family.current() == Family.small -> SmallView(entry, style)
            else -> MediumView(entry, style)
        }
    }
}

@Composable
private fun Unavailable(style: WidgetStyle) {
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        SymbolBadge("hand.raised.fill", WidgetShape.sunny, style.primary, style.onPrimary, size = 40.dp)
        Spacer(GlanceModifier.height(WidgetSpace.gap))
        NotConnected(style)
    }
}

/** The lesson's symbol in a "sunny" badge of its subject color. */
@Composable
private fun LessonBadge(lesson: TodayEntry.LessonInfo, style: WidgetStyle) {
    SymbolBadge(lesson.symbol, WidgetShape.sunny, style.subject(lesson.color), onSubject, size = 32.dp)
}

private val onSubject = ColorProvider(Color.White)

@Composable
private fun LessonName(name: String, style: WidgetStyle) {
    Text(name, style = TextStyle(color = style.text, fontSize = WidgetText.headline, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
private fun LessonHeader(entry: TodayEntry, style: WidgetStyle) {
    val current = entry.current
    val next = entry.next
    when {
        current != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            LessonBadge(current, style)
            Spacer(GlanceModifier.width(WidgetSpace.gap))
            Column {
                LessonName(current.name, style)
                Caption(L("%lld× gemeldet", entry.lessonCount), style.secondary, weight = FontWeight.Medium)
            }
        }
        next != null -> Column {
            Caption(L("Als Nächstes"), style.secondary, weight = FontWeight.Medium)
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LessonBadge(next, style)
                Spacer(GlanceModifier.width(WidgetSpace.gap))
                Column {
                    LessonName(next.name, style)
                    Countdown(next.start, entry.now, style)
                }
            }
        }
        else -> Text(
            L("Heute kein Unterricht."),
            style = TextStyle(color = style.text, fontSize = WidgetText.headline, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
    }
}

/** This week's Meldungen against the goal, bold, with the Meldo hand. */
@Composable
private fun WeekLine(entry: TodayEntry, style: WidgetStyle) {
    val goal = entry.goal
    SymbolLabel(
        text = if (goal != null) L("%lld von %lld", entry.weekTotal, goal) else L("%lld diese Woche", entry.weekTotal),
        symbol = "hand.raised.fill",
        color = style.text,
        fontSize = WidgetText.subheadline,
        weight = FontWeight.Bold,
        iconColor = WidgetColors.meldoOrange,
    )
}

/**
 * Small: the lesson as the hero, the week and the Melden circle along the bottom.
 * Free height is split evenly above and below the lesson, so no empty band.
 */
@Composable
private fun SmallView(entry: TodayEntry, style: WidgetStyle, modifier: GlanceModifier = GlanceModifier.fillMaxSize()) {
    Column(modifier) {
        Spacer(GlanceModifier.defaultWeight())
        LessonHeader(entry, style)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { WeekLine(entry, style) }
            Spacer(GlanceModifier.width(WidgetSpace.gap))
            MeldenWidgetButton(entry.current != null, style)
        }
    }
}

/** Medium: the small layout plus a tonal tile with the rest of today and homework due. */
@Composable
private fun MediumView(entry: TodayEntry, style: WidgetStyle) {
    Row(GlanceModifier.fillMaxSize()) {
        SmallView(entry, style, GlanceModifier.defaultWeight().fillMaxHeight())
        Spacer(GlanceModifier.width(WidgetSpace.gap + 2.dp))
        Tile(style.secondaryContainer, GlanceModifier.defaultWeight().fillMaxHeight()) {
            Caption(L("Später heute"), style.onSecondaryContainer, weight = FontWeight.Medium)
            Spacer(GlanceModifier.defaultWeight())
            if (entry.laterToday.isEmpty()) {
                Caption(L("Keine weiteren Stunden."), style.onSecondaryContainer, size = 13.sp, weight = FontWeight.Medium, maxLines = 2)
            }
            for (lesson in entry.laterToday) {
                Row(GlanceModifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(style.subject(lesson.color))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        lesson.name,
                        style = TextStyle(color = style.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Caption(timeText(lesson.start), style.onSecondaryContainer)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (entry.homeworkDue > 0) {
                val context = LocalContext.current
                Chip(
                    L("%lld Hausaufgaben bald fällig", entry.homeworkDue),
                    // Primary: the orange scheme's containers are all the same peach as the tile.
                    background = style.primary,
                    color = style.onPrimary,
                    symbol = "book",
                    modifier = GlanceModifier.clickable(actionStartActivity(SurfaceIntents.intent(context, SurfaceIntents.HOMEWORK))),
                )
            }
        }
    }
}
