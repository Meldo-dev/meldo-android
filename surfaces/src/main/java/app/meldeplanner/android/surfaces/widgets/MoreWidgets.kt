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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.surfaces.SurfaceIntents
import uniffi.melde_ffi.timetableOccurrencesOn
import java.time.Instant

// Widgets that only display stored data. Port of meldo-ios/MeldeWidgets/MoreWidgets.swift.

/** Shared body of the display-only widgets: real data for the content and the gallery preview. */
abstract class MeldoDataWidget : GlanceAppWidget() {
    @Composable
    internal abstract fun Content(snapshot: WidgetSnapshot, style: WidgetStyle)

    /** A soft primary glow in the panel corner (widgets with a hero numeral). */
    internal open val glow: Boolean = false

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        prepareWidgetData(context)
        provideContent { Body() }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        prepareWidgetData(context)
        provideContent { Body() }
    }

    @Composable
    private fun Body() {
        MeldoGlanceTheme {
            val snapshot = rememberSnapshot()
            val style = WidgetStyle.of(snapshot.data.preferences)
            WidgetContainer(style, glow = glow) {
                if (snapshot.available) {
                    Content(snapshot, style)
                } else {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { NotConnected(style) }
                }
            }
        }
    }
}

// region Today's timetable

/** "Stundenplan heute": today's lessons, cancellations struck through (medium, large). */
class TodayScheduleWidget : MeldoDataWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Family.MEDIUM, Family.LARGE))

    @Composable
    override fun Content(snapshot: WidgetSnapshot, style: WidgetStyle) = TodayScheduleView(snapshot, style)
}

class TodayScheduleWidgetReceiver : MeldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayScheduleWidget()
}

/** Neutral marker of a regular lesson (cancelled is red, substituted green). */
private val regularMarker = ColorProvider(day = Color(0x803C3C43), night = Color(0x80EBEBF5))

@Composable
private fun TodayScheduleView(snapshot: WidgetSnapshot, style: WidgetStyle) {
    val data = snapshot.data
    val calendar = snapshot.model.calendar
    val day = calendar.startOfDay(snapshot.now)
    val minute = calendar.minuteOfDay(snapshot.now)
    val occurrences = runCatching {
        timetableOccurrencesOn(MeldeEngine.timetable(data, calendar), calendar.ffiDate(day))
    }.getOrDefault(emptyList())
    val holiday = data.holidays.firstOrNull { calendar.startOfDay(it.firstDay) <= day && day <= calendar.startOfDay(it.lastDay) }
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dateText(snapshot.now, "EEEEdMMMM"),
                style = TextStyle(color = style.text, fontSize = WidgetText.body, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            SymbolBadge("tablecells", WidgetShape.cookie, style.primary, style.onPrimary, size = 28.dp)
        }
        // Free height is split evenly above and below the content (no empty band).
        Spacer(GlanceModifier.defaultWeight())
        when {
            holiday != null -> Tile(style.secondaryContainer, GlanceModifier.fillMaxWidth()) {
                SymbolLabel(holiday.name, "sun.horizon.fill", style.onSecondaryContainer, WidgetText.headline, FontWeight.Bold, iconColor = WidgetColors.holiday)
            }
            occurrences.isEmpty() -> Tile(style.secondaryContainer, GlanceModifier.fillMaxWidth()) {
                Caption(L("Heute kein Unterricht."), style.onSecondaryContainer, size = WidgetText.subheadline, weight = FontWeight.Medium)
            }
            else -> {
                val upcoming = occurrences.filter { it.endMinute.toInt() > minute }
                val limit = if (Family.current() == Family.large) 9 else 4
                val shown = upcoming.ifEmpty { occurrences }.take(limit)
                for (o in shown) {
                    val subject = MeldeEngine.subject(data, o.subjectId)
                    val running = o.startMinute.toInt() <= minute && minute < o.endMinute.toInt()
                    val marker = when {
                        o.cancelled -> WidgetColors.red
                        o.substituted -> WidgetColors.green
                        else -> regularMarker
                    }
                    val textColor = if (running) style.onPrimaryContainer else style.text
                    val timeColor = if (running) style.onPrimaryContainer else style.secondary
                    var row = GlanceModifier.fillMaxWidth().height(22.dp)
                    if (running) row = row.cornerRadius(11.dp).background(style.primaryContainer)
                    Row(row.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.width(3.dp).height(14.dp).cornerRadius(2.dp).background(marker)) {}
                        Spacer(GlanceModifier.width(6.dp))
                        Caption(
                            "%02d:%02d".format(o.startMinute.toInt() / 60, o.startMinute.toInt() % 60),
                            timeColor, weight = FontWeight.Medium,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Symbol(subject?.symbolName ?: "book.closed", style.subject(subject?.color ?: app.meldeplanner.android.data.SubjectColor.blue), size = 13.dp)
                        Spacer(GlanceModifier.width(5.dp))
                        Text(
                            subject?.name ?: L("Unterricht"),
                            style = TextStyle(
                                color = textColor,
                                fontSize = WidgetText.body,
                                fontWeight = if (running) FontWeight.Bold else FontWeight.Medium,
                                textDecoration = strike(o.cancelled),
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        o.room?.let { Caption(it, timeColor, weight = FontWeight.Medium) }
                    }
                    Spacer(GlanceModifier.height(2.dp))
                }
                if (upcoming.size <= limit) {
                    // The rest of the day is shown: say so instead of leaving space.
                    Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(style.track, size = 6.dp)
                        Spacer(GlanceModifier.width(8.dp))
                        Caption(L("Keine weiteren Stunden."), style.secondary, weight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

// endregion

// region Homework

/** "Hausaufgaben": what is due soon (small, medium). Opens the homework list. */
class HomeworkWidget : MeldoDataWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Family.SMALL, Family.MEDIUM))

    @Composable
    override fun Content(snapshot: WidgetSnapshot, style: WidgetStyle) = HomeworkWidgetView(snapshot, style)
}

class HomeworkWidgetReceiver : MeldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeworkWidget()
}

@Composable
private fun HomeworkWidgetView(snapshot: WidgetSnapshot, style: WidgetStyle) {
    val context = LocalContext.current
    val calendar = snapshot.model.calendar
    val open = snapshot.data.tasks
        .filter { it.kind == TaskKind.homework && !it.isDone }
        .sortedBy { it.dueDate ?: Instant.MAX }
    val small = Family.current() == Family.small
    Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(SurfaceIntents.intent(context, SurfaceIntents.HOMEWORK)))) {
        WidgetHeader(L("Hausaufgaben"), "book.fill", WidgetColors.orange, style)
        // Free height is split evenly above and below the content (no empty band).
        Spacer(GlanceModifier.defaultWeight())
        when {
            open.isEmpty() -> {
                Text(L("Alles erledigt."), style = TextStyle(color = style.text, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            }
            small -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeroBadge("${open.size}", WidgetShape.cookie, style.primary, style.onPrimary, size = 76.dp, fontSize = 42.sp)
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        open.first().title,
                        style = TextStyle(color = style.text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 3,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
            else -> {
                val today = calendar.startOfDay(snapshot.now)
                for (task in open.take(4)) {
                    Row(GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Symbol("circle", style.secondary, size = 14.dp)
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            task.title,
                            style = TextStyle(color = style.text, fontSize = WidgetText.body, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        task.dueDate?.let { due ->
                            Spacer(GlanceModifier.width(6.dp))
                            DueChip(dateText(due, "EEE"), overdue = due < today, style)
                        }
                    }
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

/** The due weekday as a small tonal pill; overdue in red. */
@Composable
private fun DueChip(text: String, overdue: Boolean, style: WidgetStyle) {
    Box(
        GlanceModifier
            .cornerRadius(100.dp)
            .background(style.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = TextStyle(
                color = if (overdue) WidgetColors.red else style.onSecondaryContainer,
                fontSize = WidgetText.caption2,
                fontWeight = if (overdue) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

// endregion
