package app.meldeplanner.android.surfaces

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import app.meldeplanner.android.surfaces.R
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.SchoolTask
import java.time.Instant
import app.meldeplanner.android.model.meldoModel
import app.meldeplanner.android.core.Notifications

/**
 * Local notifications for task reminders, port of
 * meldo-ios/MeldePlanner/Data/Reminders.swift. iOS hands the date to the
 * notification center; here an alarm posts the notification at [SchoolTask.reminderAt].
 */
object Reminders {
    internal const val ACTION_FIRE = "app.meldeplanner.android.REMINDER"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_BODY = "body"
    internal const val EXTRA_TASK_ID = "taskID"

    private fun id(taskID: String) = "task-$taskID"

    private fun alarm(context: Context, taskID: String, title: String = "", body: String = "") =
        Alarms.pendingBroadcast(context, ACTION_FIRE, id(taskID)) {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_TASK_ID, taskID)
        }

    fun schedule(context: Context, task: SchoolTask, subjectName: String?) {
        cancel(context, task.id)
        val at = task.reminderAt ?: return
        if (!at.isAfter(Instant.now()) || task.isDone) return
        val body = subjectName?.let { L("%@ · noch offen", it) } ?: L("Noch offen")
        Alarms.set(context, at, alarm(context, task.id, task.title, body))
    }

    fun cancel(context: Context, taskID: String) {
        Alarms.cancel(context, alarm(context, taskID))
        // A reminder already on screen goes too.
        context.getSystemService(NotificationManager::class.java)?.cancel(id(taskID).hashCode())
    }

    /** Alarms don't survive a reboot or a clock change; iOS keeps them, so re-plan all. */
    internal fun rescheduleAll(context: Context, data: AppData) {
        for (task in data.tasks) {
            schedule(context, task, data.subjects.firstOrNull { it.id == task.subjectID }?.name)
        }
    }

    /** The alarm went off: show the reminder. */
    internal fun post(context: Context, intent: Intent) {
        val taskID = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val task = context.meldoModel.data.tasks.firstOrNull { it.id == taskID }
        // Completed or deleted meanwhile (e.g. on another device after a sync): nothing to remind.
        if (task == null || task.isDone) return
        if (!Notifications.hasPermission(context)) return
        Notifications.ensureChannels(context)
        val notification = Notification.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_surfaces_hand)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE) ?: task.title)
            .setContentText(intent.getStringExtra(EXTRA_BODY) ?: L("Noch offen"))
            .setGroup("tasks")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(SurfaceIntents.openApp(context, null))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id(taskID).hashCode(), notification)
    }
}
