package app.meldeplanner.android.surfaces

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.meldeplanner.android.model.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import app.meldeplanner.android.core.Notifications

/**
 * AlarmManager helpers. iOS schedules local notifications with a date trigger;
 * Android has no such queue, so every timed surface (check-ins, reminders,
 * lesson start/end) is an alarm that wakes [SurfacesReceiver].
 */
internal object Alarms {
    /** Window for inexact alarms when exact alarms are not allowed (Android's minimum is about 10 minutes). */
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    /** A broadcast to [SurfacesReceiver]; [key] makes it distinct from other alarms of the same [action]. */
    fun pendingBroadcast(context: Context, action: String, key: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, SurfacesReceiver::class.java)
            .setAction(action)
            .setData(Uri.Builder().scheme("meldo-surface").authority(action).appendPath(key).build())
            .apply(extras)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Sets a wake-up alarm, exact when the student allowed it. */
    fun set(context: Context, at: Instant, operation: PendingIntent, wakeUp: Boolean = true) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val type = if (wakeUp) AlarmManager.RTC_WAKEUP else AlarmManager.RTC
        val millis = at.toEpochMilli()
        if (wakeUp && Notifications.canScheduleExactAlarms(context)) {
            runCatching { manager.setExactAndAllowWhileIdle(type, millis, operation) }
                .onFailure { manager.setWindow(type, millis, WINDOW_MILLIS, operation) }
        } else if (wakeUp) {
            manager.setWindow(type, millis, WINDOW_MILLIS, operation)
        } else {
            manager.set(type, millis, operation)
        }
    }

    fun cancel(context: Context, operation: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(operation)
        operation.cancel()
    }
}

/** Background work outside any screen (widget updates, alarms). */
internal val surfaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Runs [work] on the main thread (the model's Compose state lives there) and
 * keeps the receiver alive until it finished.
 */
internal fun BroadcastReceiver.runAsync(work: suspend () -> Unit) {
    val pending = goAsync()
    surfaceScope.launch {
        try {
            withContext(Dispatchers.Main) { work() }
        } finally {
            pending.finish()
        }
    }
}
