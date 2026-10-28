package app.meldeplanner.android.surfaces.widgets

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.meldeplanner.android.R
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.AppTheme
import app.meldeplanner.android.data.Preferences
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.surfaces.LogParticipation
import app.meldeplanner.android.surfaces.SurfaceIntents
import app.meldeplanner.android.surfaces.Surfaces
import app.meldeplanner.android.surfaces.SymbolBitmaps
import app.meldeplanner.android.surfaces.meldoModel
import app.meldeplanner.android.ui.theme.MeldoColors
import app.meldeplanner.android.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** All home screen widgets, `MeldeWidgetsBundle` on iOS. */
internal object MeldoWidgets {
    class Entry(val widget: GlanceAppWidget, val receiver: Class<out GlanceAppWidgetReceiver>)

    val all: List<Entry> by lazy {
        listOf(
            Entry(MeldoTodayWidget(), MeldoTodayWidgetReceiver::class.java),
            Entry(MeldoStatsWidget(), MeldoStatsWidgetReceiver::class.java),
            Entry(TodayScheduleWidget(), TodayScheduleWidgetReceiver::class.java),
            Entry(HomeworkWidget(), HomeworkWidgetReceiver::class.java),
            Entry(HolidayCountdownWidget(), HolidayCountdownWidgetReceiver::class.java),
        )
    }
}

/** iOS widget families, chosen from the size Glance lays out for (`SizeMode.Responsive`). */
internal enum class Family {
    small, medium, large;

    companion object {
        val SMALL = DpSize(120.dp, 120.dp)
        val MEDIUM = DpSize(260.dp, 120.dp)
        val LARGE = DpSize(260.dp, 260.dp)

        @Composable
        fun current(): Family {
            val size = LocalSize.current
            return when {
                size.width >= MEDIUM.width && size.height >= LARGE.height -> large
                size.width >= MEDIUM.width -> medium
                else -> small
            }
        }
    }
}

/**
 * Widget colors in Material 3 widget roles. With Material You on (the app's
 * `dynamicColor` preference, decided like MeldoTheme) they follow the wallpaper
 * through the system palette; otherwise they are the app's accent scheme. The
 * panel and the tiles are translucent (res/color/widget_*.xml for Material You):
 * widgets cannot blur the wallpaper, so it shows through softly instead.
 */
internal class WidgetStyle(
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    /** Tiles: tonal containers at about 90 %. */
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
    val tertiaryContainer: ColorProvider,
    val onTertiaryContainer: ColorProvider,
    /** The outer panel: widgetBackground at 82 % (light) / 86 % (dark). */
    val panel: ColorProvider,
    val text: ColorProvider,
    val secondary: ColorProvider,
    /** Empty part of bars and progress. */
    val track: ColorProvider,
    private val countdownColor: (RemoteViews, Int) -> Unit,
) {
    fun subject(color: SubjectColor): ColorProvider = ColorProvider(day = color.color(false), night = color.color(true))

    /** Colors a RemoteViews text (the live countdown) like [secondary]. */
    fun tintCountdown(views: RemoteViews, viewId: Int) = countdownColor(views, viewId)

    companion object {
        private const val TILE_ALPHA = 0.9f

        @Composable
        fun of(preferences: Preferences): WidgetStyle {
            // Wallpaper colors (and RemoteViews color resources) exist from Android 12.
            // Meldo is always orange (no theme or wallpaper colors).
            val day = AccentTones.of(AppTheme.orange, dark = false)
            val night = AccentTones.of(AppTheme.orange, dark = true)
            fun pair(alpha: Float = 1f, pick: (AccentTones) -> Long) =
                ColorProvider(day = Color(pick(day)).copy(alpha = alpha), night = Color(pick(night)).copy(alpha = alpha))
            return WidgetStyle(
                primary = pair { it.primary }, onPrimary = pair { it.onPrimary },
                primaryContainer = pair(TILE_ALPHA) { it.primaryContainer }, onPrimaryContainer = pair { it.onPrimaryContainer },
                secondaryContainer = pair(TILE_ALPHA) { it.secondaryContainer }, onSecondaryContainer = pair { it.onSecondaryContainer },
                tertiaryContainer = pair(TILE_ALPHA) { it.tertiaryContainer }, onTertiaryContainer = pair { it.onTertiaryContainer },
                panel = ColorProvider(day = Color(day.panel).copy(alpha = 0.82f), night = Color(night.panel).copy(alpha = 0.86f)),
                text = pair { it.onSurface }, secondary = pair { it.onSurfaceVariant },
                track = ColorProvider(day = Color(day.primary).copy(alpha = 0.16f), night = Color(night.primary).copy(alpha = 0.22f)),
                countdownColor = { views, id ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        views.setColorInt(id, "setTextColor", day.onSurfaceVariant.toInt(), night.onSurfaceVariant.toInt())
                    } else {
                        // Before Android 12 a widget can't switch colors with the system theme.
                        views.setTextColor(id, day.onSurfaceVariant.toInt())
                    }
                },
            )
        }
    }
}

/**
 * The roles widgets use from MeldoTheme's accent schemes (`accentScheme` +
 * `withSurfaces`, private there). Keep in sync with ui/theme/MeldoTheme.kt.
 */
private class AccentTones(
    val primary: Long, val onPrimary: Long,
    val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiaryContainer: Long, val onTertiaryContainer: Long,
    val panel: Long, val onSurface: Long, val onSurfaceVariant: Long,
) {
    companion object {
        private val orangeLight = AccentTones(0xFF9C4400, 0xFFFFFFFF, 0xFFFFDBC9, 0xFF331200, 0xFFFFDBC9, 0xFF2B160A, 0xFFE0E0FF, 0xFF00006E, 0xFFF5EEEA, 0xFF221A15, 0xFF54453D)
        private val orangeDark = AccentTones(0xFFFFB68E, 0xFF542100, 0xFF773300, 0xFFFFDBC9, 0xFF5C4132, 0xFFFFDBC9, 0xFF2B2DC3, 0xFFE0E0FF, 0xFF221C18, 0xFFF0DFD7, 0xFFD9C4B9)
        private val indigoLight = AccentTones(0xFF4044DB, 0xFFFFFFFF, 0xFFE0E0FF, 0xFF00006E, 0xFFE1E0F9, 0xFF191A2C, 0xFFFFDBC9, 0xFF331200, 0xFFF1F0F7, 0xFF1B1B21, 0xFF474651)
        private val indigoDark = AccentTones(0xFFBFC2FF, 0xFF1A1BA8, 0xFF2B2DC3, 0xFFE0E0FF, 0xFF444559, 0xFFE1E0F9, 0xFF773300, 0xFFFFDBC9, 0xFF1E1C27, 0xFFE4E1EC, 0xFFC8C5D2)
        private val greenLight = AccentTones(0xFF006E26, 0xFFFFFFFF, 0xFF9BF6A0, 0xFF002107, 0xFFD4E8CF, 0xFF101F10, 0xFFBDEAF4, 0xFF001F24, 0xFFEFF3EC, 0xFF191C19, 0xFF414A40)
        private val greenDark = AccentTones(0xFF7FDA87, 0xFF003911, 0xFF00531A, 0xFF9BF6A0, 0xFF3A4B39, 0xFFD4E8CF, 0xFF1F4D55, 0xFFBDEAF4, 0xFF1A201A, 0xFFE0E4DC, 0xFFC1C9BC)

        fun of(theme: AppTheme, dark: Boolean): AccentTones = when (theme) {
            AppTheme.orange -> if (dark) orangeDark else orangeLight
            AppTheme.indigo -> if (dark) indigoDark else indigoLight
            AppTheme.green -> if (dark) greenDark else greenLight
        }
    }
}

/** The fixed colors iOS uses in widgets. */
internal object WidgetColors {
    val meldoOrange = ColorProvider(MeldoColors.orange)
    val holiday = ColorProvider(Color(0xFF6B85C4))
    val orange = ColorProvider(day = SubjectColor.orange.color(false), night = SubjectColor.orange.color(true))
    val red = ColorProvider(day = SubjectColor.red.color(false), night = SubjectColor.red.color(true))
    val green = ColorProvider(day = SubjectColor.green.color(false), night = SubjectColor.green.color(true))
}

/**
 * Widget type scale (Material 3 Expressive): one display numeral as the hero,
 * otherwise few sizes. Glance offers Normal/Medium/Bold and no letter spacing.
 */
internal object WidgetText {
    val caption2: TextUnit = 11.sp
    val caption: TextUnit = 12.sp
    val body: TextUnit = 14.sp
    val subheadline: TextUnit = 15.sp
    val headline: TextUnit = 17.sp
    val hero: TextUnit = 44.sp
}

/** Spacing grid: 14 dp around the panel, 8 dp between blocks, 10 dp inside tiles. */
internal object WidgetSpace {
    val outer: Dp = 14.dp
    val gap: Dp = 8.dp
    val tile: Dp = 10.dp
}

/** What every widget reads: the shared model's data, re-read on each [Surfaces.reload]. */
internal class WidgetSnapshot(val model: AppModel, val data: AppData, val now: Instant, val available: Boolean)

@Composable
internal fun rememberSnapshot(): WidgetSnapshot {
    val model = LocalContext.current.meldoModel
    // Reading both states subscribes the widget to data changes and to transitions.
    Surfaces.tick.value
    val data = model.data
    return WidgetSnapshot(model, data, Instant.now(), available = model.storageProblem == null)
}

/** Loads changes made outside the model (another process never writes, but the file is the truth). */
internal suspend fun prepareWidgetData(context: Context) {
    withContext(Dispatchers.Main) { context.meldoModel.reloadFromDisk() }
}

/**
 * The widget's translucent panel with the system corner radius. [glow] adds a
 * soft primary radial gradient in the top-end corner (a drawable, not a blur).
 */
@Composable
internal fun WidgetContainer(style: WidgetStyle, glow: Boolean = false, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(
                // The system widget radius exists from Android 12 (Glance rounds nothing before).
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius) else GlanceModifier,
            )
            .background(style.panel),
    ) {
        if (glow) {
            Box(
                GlanceModifier.fillMaxSize().background(
                    ImageProvider(R.drawable.widget_glow),
                    colorFilter = ColorFilter.tint(style.primary),
                ),
            ) {}
        }
        Box(GlanceModifier.fillMaxSize().padding(WidgetSpace.outer).then(modifier)) { content() }
    }
}

/** An inner tile: a tonal container with the system inner widget radius. */
@Composable
internal fun Tile(
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    padding: Dp = WidgetSpace.tile,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .then(
                // The system widget radius exists from Android 12 (Glance rounds nothing before).
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_inner_radius) else GlanceModifier,
            )
            .background(color)
            .padding(padding),
    ) { content() }
}

/** Expressive shapes drawn as tinted vectors (res/drawable/widget_shape_*.xml). */
internal enum class WidgetShape(val drawable: Int) {
    cookie(R.drawable.widget_shape_cookie),
    sunny(R.drawable.widget_shape_sunny),
}

/** A hero numeral inside a soft Expressive shape ("cookie" / "sunny"). */
@Composable
internal fun HeroBadge(
    text: String,
    shape: WidgetShape,
    fill: ColorProvider,
    content: ColorProvider,
    size: Dp = 76.dp,
    fontSize: TextUnit = 38.sp,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(shape.drawable), colorFilter = ColorFilter.tint(fill)),
        contentAlignment = Alignment.Center,
    ) {
        // Three digits and more shrink so they stay inside the shape.
        val scaled = if (text.length >= 3) (fontSize.value * 0.78f).sp else fontSize
        Text(
            text,
            style = TextStyle(color = content, fontSize = scaled, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** A symbol inside a soft Expressive shape, e.g. the streak flame in a "sunny". */
@Composable
internal fun SymbolBadge(symbol: String, shape: WidgetShape, fill: ColorProvider, iconColor: ColorProvider, size: Dp = 22.dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(shape.drawable), colorFilter = ColorFilter.tint(fill)),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, iconColor, size = size * 0.55f)
    }
}

/** A small pill with an optional symbol, for supporting facts. */
@Composable
internal fun Chip(
    text: String,
    background: ColorProvider,
    color: ColorProvider,
    symbol: String? = null,
    iconColor: ColorProvider = color,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier
            .cornerRadius(100.dp)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) {
            Symbol(symbol, iconColor, size = 13.dp)
            Spacer(GlanceModifier.width(5.dp))
        }
        Text(text, style = TextStyle(color = color, fontSize = WidgetText.caption, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** A small round marker in a subject's color. */
@Composable
internal fun Dot(color: ColorProvider, size: Dp = 8.dp) {
    Box(GlanceModifier.size(size).cornerRadius(size / 2).background(color)) {}
}

@Composable
internal fun Symbol(name: String, color: ColorProvider, size: Dp = 14.dp, description: String? = null) {
    Image(
        provider = ImageProvider(SymbolBitmaps.get(name)),
        contentDescription = description,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

/** `Label(text, systemImage:)`. */
@Composable
internal fun SymbolLabel(
    text: String,
    symbol: String,
    color: ColorProvider,
    fontSize: TextUnit,
    weight: FontWeight = FontWeight.Medium,
    iconColor: ColorProvider = color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Symbol(symbol, iconColor, size = (fontSize.value + 1).dp)
        Spacer(GlanceModifier.width(5.dp))
        Text(text, style = TextStyle(color = color, fontSize = fontSize, fontWeight = weight), maxLines = 1)
    }
}

/** A widget's title row: the symbol in its accent, the label medium-weight in onSurfaceVariant. */
@Composable
internal fun WidgetHeader(text: String, symbol: String, iconColor: ColorProvider, style: WidgetStyle) {
    SymbolLabel(text, symbol, style.secondary, WidgetText.caption, FontWeight.Medium, iconColor = iconColor)
}

@Composable
internal fun Caption(text: String, color: ColorProvider, size: TextUnit = WidgetText.caption, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1) {
    Text(text, style = TextStyle(color = color, fontSize = size, fontWeight = weight), maxLines = maxLines)
}

@Composable
internal fun NotConnected(style: WidgetStyle) {
    Text(
        L("Öffne Meldo einmal, um das Widget zu verbinden."),
        style = TextStyle(color = style.secondary, fontSize = WidgetText.caption, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

/**
 * A live countdown to [target] (`Text(timerInterval:countsDown:)` /
 * `Text(date, style: .relative)` on iOS): a Chronometer the launcher ticks
 * without waking Meldo. Beyond today it shows a relative day instead.
 */
@Composable
internal fun Countdown(target: Instant, now: Instant, style: WidgetStyle, fontSize: TextUnit = WidgetText.caption) {
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), target.atZone(zone).toLocalDate())
    if (days >= 1) {
        val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(Localization.locale))
        val text = if (days == 1L) {
            formatter.format(RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
        } else {
            formatter.format(days.toDouble(), RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS)
        }
        Caption(text, style.secondary, size = fontSize, weight = FontWeight.Medium)
        return
    }
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.surfaces_widget_countdown).apply {
        val base = SystemClock.elapsedRealtime() + Duration.between(now, target).toMillis()
        setChronometer(R.id.surfaces_countdown, base, null, true)
        setChronometerCountDown(R.id.surfaces_countdown, true)
        setTextViewTextSize(R.id.surfaces_countdown, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.value)
        // Follows dark mode and Material You like the rest of the widget.
        style.tintCountdown(this, R.id.surfaces_countdown)
    }
    AndroidRemoteViews(views)
}

/** Short time in the app language ("08:15", "8:15 AM"). */
internal fun timeText(instant: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Localization.locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)

/** A date with a skeleton like "EEEEdMMMM", in the app language. */
internal fun dateText(instant: Instant, skeleton: String): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Localization.locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, Localization.locale).withZone(ZoneId.systemDefault()).format(instant)
}

/**
 * Base receiver of every Meldo widget. The first widget placed arms the
 * transition alarm (lesson start/end, midnight) that keeps widgets current.
 */
abstract class MeldoWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.meldoModel.refreshSchedules()
    }
}

/** One-tap Melden from a widget (`LogParticipationIntent`). */
class MeldenWidgetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.Main) { LogParticipation.perform(context.applicationContext) }
    }
}

/**
 * The Melden button: during a lesson a filled primary circle that logs a
 * Meldung; outside lessons a tonal circle that opens the subject picker.
 * [size] is also the touch target (never under 48 dp).
 */
@Composable
internal fun MeldenWidgetButton(inLesson: Boolean, style: WidgetStyle, size: Dp = 52.dp) {
    val context = LocalContext.current
    val action = if (inLesson) {
        actionRunCallback<MeldenWidgetCallback>()
    } else {
        actionStartActivity(SurfaceIntents.intent(context, SurfaceIntents.MELDEN))
    }
    val target = if (size < 48.dp) 48.dp else size
    Box(
        modifier = GlanceModifier
            .size(target)
            .cornerRadius(target / 2)
            .background(if (inLesson) style.primary else style.primaryContainer)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(
            "hand.raised.fill",
            if (inLesson) style.onPrimary else style.onPrimaryContainer,
            size = target * 0.46f,
            description = L("Melden"),
        )
    }
}

/** Strikethrough for cancelled lessons. */
internal fun strike(cancelled: Boolean): TextDecoration? = if (cancelled) TextDecoration.LineThrough else null

/** Wraps a widget in GlanceTheme so dynamic colors resolve. */
@Composable
internal fun MeldoGlanceTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = DynamicThemeColorProviders, content = content)
}
