package app.meldeplanner.android.surfaces

import app.meldeplanner.android.ui.R as UiR
import android.os.Build
import android.app.NotificationManager
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import androidx.compose.runtime.mutableStateOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import app.meldeplanner.android.surfaces.R
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.surfaces.widgets.MeldoWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.melde_ffi.FfiOccurrence
import uniffi.melde_ffi.timetableOccurrencesOn
import java.time.Duration
import java.time.Instant
import app.meldeplanner.android.model.meldoModel
import app.meldeplanner.android.core.Notifications

/** Result of [Surfaces.previewLiveLesson], `LessonActivity.PreviewResult` on iOS. */
sealed interface LivePreviewResult {
    data object shown : LivePreviewResult

    /** Notifications (or the live lesson channel) are switched off for Meldo. */
    data object disabledInSettings : LivePreviewResult
    data object noLessons : LivePreviewResult
    data class failed(val message: String) : LivePreviewResult
}

/** Everything outside the app that shows Meldo data (widgets, tile, live lesson notification). */
object Surfaces {
    internal const val ACTION_TRANSITION = "app.meldeplanner.android.SURFACES_TRANSITION"

    /** While a live lesson is shown its progress bar moves on this often. */
    private val LIVE_TICK: Duration = Duration.ofMinutes(5)

    private const val PREFS = "meldo.surfaces"
    private const val KEY_PREVIEWS_VERSION = "widgets.previewsKey"

    /**
     * Bumped on every [reload]. Widget compositions read it with the model's data,
     * so running widget sessions recompose for time-based changes (a lesson
     * started) as well as data changes.
     */
    internal val tick = mutableStateOf(Instant.now())

    /** Refreshes widgets and the Quick Settings tile after data changed. */
    fun reload(context: Context) {
        val app = context.applicationContext
        tick.value = Instant.now()
        surfaceScope.launch { updateWidgets(app) }
        runCatching { TileService.requestListeningState(app, ComponentName(app, MeldenTileService::class.java)) }
    }

    /**
     * Reloads the data written outside this process's model, then refreshes
     * widgets and the tile and plans check-ins, the live lesson and the next
     * transition, waiting for all of it (receivers finish afterwards).
     * Call on the main thread.
     */
    internal suspend fun replanNow(context: Context) {
        val app = context.applicationContext
        val model = app.meldoModel
        model.reloadFromDisk()
        val data = model.data
        val now = Instant.now()
        tick.value = now
        runCatching { TileService.requestListeningState(app, ComponentName(app, MeldenTileService::class.java)) }
        withContext(Dispatchers.Default) {
            LessonCheckIns.reschedule(app, data, model.calendar, now)
            refreshLiveLesson(app, data, model.calendar, now)
            updateWidgets(app)
        }
    }

    internal suspend fun updateWidgets(context: Context) {
        for (widget in MeldoWidgets.all) runCatching { widget.widget.updateAll(context) }
    }

    /** Number of Meldo widgets on the home screen (Settings shows it as a status). */
    fun placedWidgetCount(context: Context): Int {
        val manager = AppWidgetManager.getInstance(context) ?: return 0
        return MeldoWidgets.all.sumOf { manager.getAppWidgetIds(ComponentName(context, it.receiver)).size }
    }

    /**
     * Asks the system to add the Melden tile to Quick Settings (the counterpart
     * of adding the iOS Control). Call from a visible activity.
     * [onResult] is `true` when the tile is (now) in Quick Settings.
     */
    fun requestAddTile(context: Context, onResult: (Boolean) -> Unit) {
        // Asking to add a tile exists from Android 13; before that the student adds it by hand.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(false)
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            onResult(false)
            return
        }
        manager.requestAddTileService(
            ComponentName(context, MeldenTileService::class.java),
            L("Melden"),
            Icon.createWithResource(context, UiR.drawable.ic_meldo_hand),
            context.mainExecutor,
        ) { result ->
            onResult(
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
            )
        }
    }

    // region Live lesson

    /** The current lesson, or else the next lesson later today (`LessonActivity.refresh`). */
    private fun liveLesson(data: AppData, calendar: MeldoCalendar, now: Instant): FfiOccurrence? =
        MeldeEngine.currentLesson(data, now, calendar)
            ?: MeldeEngine.nextLesson(data, now, calendar)?.takeIf { calendar.instant(it.date) == calendar.startOfDay(now) }

    private fun content(data: AppData, lesson: FfiOccurrence, day: Instant, calendar: MeldoCalendar, now: Instant): LiveLesson.Content {
        val subject = MeldeEngine.subject(data, lesson.subjectId)
        return LiveLesson.Content(
            lessonID = lesson.lessonId,
            subjectName = subject?.name ?: L("Unterricht"),
            color = subject?.color ?: SubjectColor.blue,
            symbolName = subject?.symbolName ?: "book.closed",
            start = calendar.at(day, lesson.startMinute.toInt()),
            end = calendar.at(day, lesson.endMinute.toInt()),
            count = MeldeEngine.count(data, lesson.lessonId, day, calendar),
            weekTotal = (MeldeEngine.weekSummary(data, now, calendar)?.total ?: 0u).toInt(),
        )
    }

    /**
     * Shows the current lesson, or else the next lesson later today, as a Live
     * Update (counting down to its start, then to its end), and removes it when
     * no lesson is left. Called whenever the app becomes active and after every
     * Meldung, and by an alarm at each lesson start and end, so it moves on to
     * the next lesson without the app.
     */
    fun refreshLiveLesson(context: Context, data: AppData, calendar: MeldoCalendar, now: Instant, note: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val lesson = liveLesson(data, calendar, now)
        var shown = false
        if (lesson == null || !data.preferences.liveActivities || !Notifications.isChannelEnabled(context, Notifications.CHANNEL_LIVE_LESSON)) {
            manager.cancel(LiveLesson.NOTIFICATION_ID)
        } else {
            val content = content(data, lesson, calendar.startOfDay(now), calendar, now)
            val timeout = Duration.between(now, content.end).toMillis().coerceAtLeast(1_000)
            runCatching {
                manager.notify(LiveLesson.NOTIFICATION_ID, LiveLesson.build(context, content, now, timeout, note = note))
                shown = true
            }
        }
        scheduleTransition(context, data, calendar, now, liveRunning = shown && lesson?.let {
            calendar.at(calendar.startOfDay(now), it.startMinute.toInt()) <= now
        } == true)
        publishWidgetPreviewsOnce(context)
    }

    /**
     * Shows the current or next lesson as a Live Update for two minutes, so
     * students can see it without waiting for a lesson.
     */
    fun previewLiveLesson(context: Context, data: AppData, calendar: MeldoCalendar, now: Instant, taps: Int = 0): LivePreviewResult {
        if (!Notifications.isChannelEnabled(context, Notifications.CHANNEL_LIVE_LESSON)) return LivePreviewResult.disabledInSettings
        val lesson = MeldeEngine.currentLesson(data, now, calendar)
            ?: MeldeEngine.nextLesson(data, now, calendar)
            ?: return LivePreviewResult.noLessons
        return try {
            val base = content(data, lesson, calendar.instant(lesson.date), calendar, now)
            val content = base.copy(count = base.count + taps, weekTotal = base.weekTotal + taps)
            context.getSystemService(NotificationManager::class.java)
                ?.notify(LiveLesson.PREVIEW_NOTIFICATION_ID, LiveLesson.build(context, content, now, 120_000, previewTaps = taps))
            LivePreviewResult.shown
        } catch (e: Exception) {
            LivePreviewResult.failed(e.localizedMessage ?: e.toString())
        }
    }

    /**
     * One alarm at the next moment a surface changes on its own: a lesson start,
     * its end (the live lesson moves on), the end of the Melde grace period (the
     * widgets' Melden button), midnight, or the next progress step of a running
     * live lesson. iOS widgets get the same moments as timeline entries.
     */
    private fun scheduleTransition(context: Context, data: AppData, calendar: MeldoCalendar, now: Instant, liveRunning: Boolean) {
        val operation = Alarms.pendingBroadcast(context, ACTION_TRANSITION, "next")
        if (placedWidgetCount(context) == 0 && !data.preferences.liveActivities) {
            Alarms.cancel(context, operation)
            return
        }
        val day = calendar.startOfDay(now)
        val occurrences = runCatching {
            timetableOccurrencesOn(MeldeEngine.timetable(data, calendar), calendar.ffiDate(now))
        }.getOrDefault(emptyList())
        val grace = MeldeEngine.PARTICIPATION_GRACE_MINUTES.toInt()
        val boundary = occurrences
            .flatMap { listOf(it.startMinute.toInt(), it.endMinute.toInt(), it.endMinute.toInt() + grace) }
            .map { calendar.at(day, it) }
            .filter { it.isAfter(now) }
            .minOrNull()
            ?: calendar.addDays(day, 1)
        val tickAt = now.plus(LIVE_TICK)
        if (liveRunning && tickAt.isBefore(boundary)) {
            // Only the progress bar moves; no need to wake the phone for it.
            Alarms.set(context, tickAt, operation, wakeUp = false)
        } else {
            Alarms.set(context, boundary, operation)
        }
    }

    // endregion

    /** Generated widget previews for the picker (Android 15+), published once per app version. */
    private fun publishWidgetPreviewsOnce(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Generated previews exist from Android 15; older pickers show the static preview layouts.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode
        }.getOrDefault(0L)
        // Previews are rendered text, so a language change needs new ones too.
        val key = "$version/${app.resources.configuration.locales[0].toLanguageTag()}"
        if (prefs.getString(KEY_PREVIEWS_VERSION, null) == key) return
        surfaceScope.launch {
            val manager = GlanceAppWidgetManager(app)
            val allPublished = MeldoWidgets.all.map { entry ->
                val result = runCatching { manager.setWidgetPreviews(entry.receiver.kotlin) }
                if (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    android.util.Log.i("MeldoWidgets", "preview ${entry.receiver.simpleName}: ${result.getOrNull() ?: result.exceptionOrNull()}")
                }
                result.getOrNull() == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
            }.all { it }
            // Rate limited: try again on the next refresh.
            if (allPublished) prefs.edit().putString(KEY_PREVIEWS_VERSION, key).apply()
        }
    }
}
