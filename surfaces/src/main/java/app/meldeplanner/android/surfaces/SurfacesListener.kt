package app.meldeplanner.android.surfaces

import android.app.Activity
import android.content.Context
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.ParticipationEntry
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.model.AppModel
import java.time.Instant

/**
 * Keeps everything outside the app in step with the model: widgets and the tile,
 * after-lesson check-ins, the live lesson and task reminders.
 */
object SurfacesListener : AppModel.Listener {
    /** [mainActivity]: the app's activity, which widgets, the tile and notifications open. */
    fun install(model: AppModel, mainActivity: Class<out Activity>) {
        SurfaceIntents.mainActivity = mainActivity
        model.addListener(this)
    }

    override fun reloadWidgets(context: Context) = Surfaces.reload(context)

    override fun reschedule(context: Context, data: AppData, calendar: MeldoCalendar, at: Instant) {
        LessonCheckIns.reschedule(context, data, calendar, at)
        Surfaces.refreshLiveLesson(context, data, calendar, at)
    }

    override fun recorded(context: Context, entry: ParticipationEntry, calendar: MeldoCalendar) =
        LessonCheckIns.didRecord(context, entry, calendar)

    override fun scheduleReminder(context: Context, task: SchoolTask, subjectName: String?) =
        Reminders.schedule(context, task, subjectName)

    override fun cancelReminder(context: Context, taskID: String) = Reminders.cancel(context, taskID)
}
