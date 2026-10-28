package app.meldeplanner.android.surfaces

import kotlinx.coroutines.delay
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms and notification actions of every surface: lesson transitions,
 * check-ins at the bell and their answers, task reminders, and the Melden
 * button of the live lesson. Runs in the app process with the shared model.
 * Not exported: only Meldo's own PendingIntents reach it.
 */
class SurfacesReceiver : BroadcastReceiver() {
    private companion object {
        /** How long the confirmation stays on the live lesson after a tap. */
        const val CONFIRMATION_MILLIS = 4_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        runAsync {
            when (intent.action) {
                Surfaces.ACTION_TRANSITION -> Surfaces.replanNow(context)
                LessonCheckIns.ACTION_DUE -> LessonCheckIns.postDue(context)
                LessonCheckIns.ACTION_ANSWER -> LessonCheckIns.handle(context, intent)
                Reminders.ACTION_FIRE -> {
                    context.meldoModel.reloadFromDisk()
                    Reminders.post(context, intent)
                }
                LiveLesson.ACTION_MELDEN -> {
                    // Visible confirmation on the notification itself: the new count and
                    // "Gemeldet in …" for a few seconds, so one tap is clearly enough.
                    val message = LogParticipation.perform(context)
                    val model = context.meldoModel
                    delay(300)
                    Surfaces.refreshLiveLesson(context, model.data, model.calendar, model.currentDate, note = "✓ $message")
                    delay(CONFIRMATION_MILLIS)
                    Surfaces.refreshLiveLesson(context, model.data, model.calendar, model.currentDate)
                }
                LiveLesson.ACTION_PREVIEW_MELDEN -> {
                    val model = context.meldoModel
                    val taps = intent.getIntExtra(LiveLesson.EXTRA_PREVIEW_TAPS, 1)
                    Surfaces.previewLiveLesson(context, model.data, model.calendar, model.currentDate, taps)
                }
            }
        }
    }
}

/**
 * Alarms are cleared by a reboot, an app update and clock changes; iOS keeps
 * its pending notifications, so everything is planned again here.
 */
class SurfacesBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> runAsync {
                val model = context.meldoModel
                model.reloadFromDisk()
                Reminders.rescheduleAll(context, model.data)
                Surfaces.replanNow(context)
            }
        }
    }
}
