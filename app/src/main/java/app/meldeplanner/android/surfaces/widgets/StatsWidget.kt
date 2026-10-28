package app.meldeplanner.android.surfaces.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.model.AppModel
import uniffi.melde_ffi.participationStreak
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle as DayTextStyle

/**
 * This week's Meldungen: against the goal (small) or per day with the most
 * active subject (medium). Real entries only; an empty week shows zeros.
 * Port of meldo-ios/MeldeWidgets/MeldoStatsWidget.swift.
 */
class MeldoStatsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Family.SMALL, Family.MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        prepareWidgetData(context)
        provideContent {
            MeldoGlanceTheme {
                val snapshot = rememberSnapshot()
                StatsWidgetView(
                    if (snapshot.available) StatsEntry.of(snapshot.model, snapshot.data, snapshot.now) else StatsEntry.unavailable,
                    WidgetStyle.of(snapshot.data.preferences),
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            MeldoGlanceTheme { StatsWidgetView(StatsEntry.placeholder, WidgetStyle.of(context.meldoPreferences())) }
        }
    }
}

class MeldoStatsWidgetReceiver : MeldoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeldoStatsWidget()
}

internal data class StatsEntry(
    val isAvailable: Boolean,
    /** Monday to Friday. */
    val perDay: List<Int>,
    val total: Int,
    val goal: Int?,
    val streak: Int,
    val topSubject: String?,
    val topColor: SubjectColor,
) {
    companion object {
        val unavailable = StatsEntry(false, emptyList(), 0, null, 0, null, SubjectColor.gray)

        /** The widget gallery preview, as on iOS. */
        val placeholder = StatsEntry(true, listOf(3, 1, 4, 2, 0), 10, 12, 4, "Mathe", SubjectColor.blue)

        fun of(model: AppModel, data: AppData, date: Instant): StatsEntry {
            val calendar = model.calendar
            val summary = MeldeEngine.weekSummary(data, date, calendar)
            val top = summary?.perSubject?.maxByOrNull { it.count }
            val subject = top?.let { MeldeEngine.subject(data, it.subjectId) }
            val streak = runCatching {
                participationStreak(MeldeEngine.entries(data, calendar), MeldeEngine.timetable(data, calendar), calendar.ffiDate(date))
            }.getOrDefault(0u)
            return StatsEntry(
                isAvailable = true,
                perDay = summary?.perDay?.take(5)?.map { it.toInt() } ?: listOf(0, 0, 0, 0, 0),
                total = (summary?.total ?: 0u).toInt(),
                goal = data.preferences.weeklyGoal,
                streak = streak.toInt(),
                topSubject = if ((top?.count ?: 0u) > 0u) subject?.name else null,
                topColor = subject?.color ?: SubjectColor.gray,
            )
        }
    }
}

@Composable
private fun StatsWidgetView(entry: StatsEntry, style: WidgetStyle) {
    WidgetContainer(style, glow = true) {
        when {
            !entry.isAvailable -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { NotConnected(style) }
            Family.current() == Family.small -> Summary(entry, style, GlanceModifier.fillMaxSize())
            else -> Row(GlanceModifier.fillMaxSize()) {
                Summary(entry, style, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(WidgetSpace.gap + 2.dp))
                Tile(style.secondaryContainer, GlanceModifier.defaultWeight().fillMaxHeight()) {
                    Chart(entry, style)
                }
            }
        }
    }
}

/**
 * The week's count as the hero in a "cookie", the goal (or "Diese Woche") beside it,
 * the streak below. Free height is split above and below the hero, so the tile never
 * has an empty band under the header.
 */
@Composable
private fun Summary(entry: StatsEntry, style: WidgetStyle, modifier: GlanceModifier) {
    val goal = entry.goal
    Column(modifier) {
        WidgetHeader(L("Meldungen"), "hand.raised.fill", style.primary, style)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeroBadge("${entry.total}", WidgetShape.cookie, style.primary, style.onPrimary, size = 84.dp, fontSize = 44.sp)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                if (goal != null) {
                    Caption(L("von %lld", goal), style.text, size = 15.sp, weight = FontWeight.Bold)
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = entry.total.coerceAtMost(goal).toFloat() / goal.coerceAtLeast(1),
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                        color = style.primary,
                        backgroundColor = style.track,
                    )
                } else {
                    Caption(L("Diese Woche"), style.text, size = 15.sp, weight = FontWeight.Bold, maxLines = 2)
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Streak(entry, style)
    }
}

/** The streak: the flame in a "sunny" badge on a warm tonal pill (the accent family, not a clashing third color). */
@Composable
private fun Streak(entry: StatsEntry, style: WidgetStyle) {
    val active = entry.streak > 0
    Row(
        GlanceModifier
            .cornerRadius(100.dp)
            .background(style.secondaryContainer)
            .padding(start = 4.dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolBadge(
            "flame.fill", WidgetShape.sunny,
            fill = if (active) WidgetColors.orange else style.track,
            iconColor = if (active) ColorProvider(Color.White) else style.onSecondaryContainer,
            size = 20.dp,
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            L("%lld Schultage in Folge", entry.streak),
            style = TextStyle(color = style.onSecondaryContainer, fontSize = WidgetText.caption2, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** Monday to Friday as rounded bars on faint tracks, the top subject below. */
@Composable
private fun Chart(entry: StatsEntry, style: WidgetStyle) {
    val maxValue = (entry.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val barArea = 56
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Bottom) {
            entry.perDay.forEachIndexed { index, value ->
                Column(
                    GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val height = (barArea * value / maxValue)
                    Box(
                        GlanceModifier.fillMaxWidth().height(barArea.dp).cornerRadius(6.dp).background(style.track),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (height > 0) {
                            Box(GlanceModifier.fillMaxWidth().height(height.coerceAtLeast(6).dp).cornerRadius(6.dp).background(style.primary)) {}
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        weekday(index),
                        style = TextStyle(color = style.onSecondaryContainer, fontSize = WidgetText.caption2, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                }
            }
        }
        val top = entry.topSubject
        if (top != null) {
            Spacer(GlanceModifier.height(6.dp))
            SymbolLabel(top, "star.fill", style.onSecondaryContainer, WidgetText.caption, FontWeight.Medium, iconColor = style.subject(entry.topColor))
        }
    }
}

/** Short weekday in the app language, Monday first. */
private fun weekday(index: Int): String =
    DayOfWeek.of(index + 1).getDisplayName(DayTextStyle.SHORT_STANDALONE, Localization.locale)
