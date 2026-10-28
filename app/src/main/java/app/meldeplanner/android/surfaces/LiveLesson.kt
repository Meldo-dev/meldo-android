package app.meldeplanner.android.surfaces

import android.os.Build
import android.app.Notification
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.toArgb
import app.meldeplanner.android.R
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.SubjectColor
import app.meldeplanner.android.ui.theme.color
import java.time.Duration
import java.time.Instant

/**
 * The running (or next) lesson as an Android 16 Live Update: an ongoing
 * notification promoted to the status bar chip and the top of the shade, the
 * counterpart of the iOS Live Activity (meldo-ios/Shared/LessonActivity.swift,
 * meldo-ios/MeldeWidgets/LessonLiveActivity.swift): subject, time left, Meldungen
 * this lesson and this week, and a Melden button.
 */
internal object LiveLesson {
    const val NOTIFICATION_ID = 4100
    const val PREVIEW_NOTIFICATION_ID = 4101
    const val ACTION_MELDEN = "app.meldeplanner.android.LIVE_MELDEN"
    /** The preview's Melden button: shows that nothing was saved, records nothing. */
    const val ACTION_PREVIEW_MELDEN = "app.meldeplanner.android.LIVE_PREVIEW_MELDEN"

    /** `LessonActivityAttributes` + `ContentState` on iOS. */
    data class Content(
        val lessonID: String,
        val subjectName: String,
        val color: SubjectColor,
        val symbolName: String,
        val start: Instant,
        val end: Instant,
        /** Meldungen in this lesson so far. */
        val count: Int,
        val weekTotal: Int,
    )

    /** Extra on the preview's Melden button: how many demo taps it shows after this one. */
    const val EXTRA_PREVIEW_TAPS = "previewTaps"

    /**
     * [previewTaps] non-null: the Status screen's "Vorschau anzeigen", with that many
     * demo taps. Its Melden button must not record real Meldungen — the preview is
     * shown at any time, also outside lessons — so it only raises the shown count.
     * [note] replaces the subtext for a moment, e.g. the confirmation after a tap.
     */
    fun build(
        context: Context,
        content: Content,
        now: Instant,
        timeoutMillis: Long?,
        previewTaps: Int? = null,
        note: String? = null,
    ): Notification {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val color = content.color.color(dark).toArgb()
        val beforeStart = now.isBefore(content.start)
        val stale = !now.isBefore(content.end)

        // The lesson as one segment in the subject's color, filled as time passes.
        val total = Duration.between(content.start, content.end).toMinutes().toInt().coerceAtLeast(1)
        val elapsed = Duration.between(content.start, now).toMinutes().toInt().coerceIn(0, total)
        val progress = if (beforeStart) 0 else elapsed

        val melden = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_surfaces_hand),
            L("Melden"),
            if (previewTaps != null) {
                Alarms.pendingBroadcast(context, ACTION_PREVIEW_MELDEN, "preview") { putExtra(EXTRA_PREVIEW_TAPS, previewTaps + 1) }
            } else {
                Alarms.pendingBroadcast(context, ACTION_MELDEN, "live")
            },
        ).build()

        val builder = Notification.Builder(context, Notifications.CHANNEL_LIVE_LESSON)
            .setSmallIcon(R.drawable.ic_surfaces_hand)
            .setLargeIcon(Icon.createWithBitmap(SymbolBitmaps.get(content.symbolName, 144, color)))
            .setContentTitle(content.subjectName)
            .setContentText(L("%lld× gemeldet · %lld diese Woche", content.count, content.weekTotal))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(SurfaceIntents.openApp(context, null))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Android 16: a Live Update with a progress segment in the subject's color and a
            // status bar chip, like the compact Dynamic Island: Meldungen this lesson.
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total).setColor(color)))
                .setProgress(progress)
                .setProgressTrackerIcon(Icon.createWithBitmap(SymbolBitmaps.get("hand.raised.fill", 96, color)))
            builder.setStyle(style).setShortCriticalText(L("%lld×", content.count))
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) builder.setRequestPromotedOngoing(true)
        } else {
            // Older Android: an ongoing notification with a plain progress bar.
            builder.setProgress(total, progress, false).setColor(color)
        }
        // Melden only while the lesson runs: before it starts a tap would be saved without a
        // subject and the notification wouldn't change, so it looks like nothing happened.
        // The preview keeps the button to show what it looks like.
        if (previewTaps != null || (!beforeStart && !stale)) builder.addAction(melden)
        when {
            stale -> builder.setSubText(L("Stunde vorbei")).setShowWhen(false)
            // A lesson on a later day: its day and time instead of a days-long countdown.
            beforeStart && !sameDay(now, content.start) -> builder
                .setSubText(startText(content.start))
                .setShowWhen(false)
            beforeStart -> builder
                // Next lesson: count down to its start.
                .setSubText(L("Beginnt in"))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(content.start.toEpochMilli())
            else -> builder
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(content.end.toEpochMilli())
        }
        when {
            note != null -> builder.setSubText(note)
            previewTaps != null -> builder.setSubText(L("Vorschau – nichts gespeichert."))
        }
        if (timeoutMillis != null) builder.setTimeoutAfter(timeoutMillis)
        return builder.build()
    }

    private fun sameDay(a: Instant, b: Instant): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        return a.atZone(zone).toLocalDate() == b.atZone(zone).toLocalDate()
    }

    /** "Montag, 2. November · 09:35" in the app language. */
    private fun startText(start: Instant): String {
        val locale = app.meldeplanner.android.core.Localization.locale
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        val at = start.atZone(java.time.ZoneId.systemDefault())
        val day = at.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
        return "$day · ${"%02d:%02d".format(at.hour, at.minute)}"
    }
}
