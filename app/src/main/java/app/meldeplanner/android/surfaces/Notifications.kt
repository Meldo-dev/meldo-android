package app.meldeplanner.android.surfaces

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.meldeplanner.android.core.L

/**
 * Notification channels and permissions. The counterpart of
 * `UNUserNotificationCenter` authorization on iOS: Android 13+ asks for
 * POST_NOTIFICATIONS, and exact alarms (lesson end, reminders) need
 * SCHEDULE_EXACT_ALARM, which the student grants in the system settings.
 */
object Notifications {
    /** "Hast du dich gemeldet?" after each lesson. */
    const val CHANNEL_CHECK_INS = "checkins"

    /** Task reminders. */
    const val CHANNEL_REMINDERS = "reminders"

    /** The running lesson as a Live Update (iOS: Live Activity). */
    const val CHANNEL_LIVE_LESSON = "live_lesson"

    /**
     * Creates (or renames, after a language change) the channels. Names follow
     * the app language like every other string; cheap to call before each post.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CHECK_INS, L("Nach jeder Stunde nachfragen"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = L("Fragt kurz nach dem Klingeln „Hast du dich gemeldet?“. Antworte direkt in der Mitteilung. Stunden mit Meldung werden übersprungen.")
                },
                NotificationChannel(CHANNEL_REMINDERS, L("Erinnerungen"), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_LIVE_LESSON, L("Laufende Stunde"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    // Updated every few minutes: never make a sound.
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            ),
        )
    }

    /** Whether notifications can be shown (permission granted and not switched off). */
    fun hasPermission(context: Context): Boolean {
        // Before Android 13 there is no runtime permission: only the switch in the settings.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return granted && manager.areNotificationsEnabled()
    }

    /** Whether one channel is allowed to show (the student can mute single channels). */
    fun isChannelEnabled(context: Context, channel: String): Boolean {
        if (!hasPermission(context)) return false
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return (manager.getNotificationChannel(channel)?.importance ?: NotificationManager.IMPORTANCE_NONE) !=
            NotificationManager.IMPORTANCE_NONE
    }

    /** Exact alarms (check-ins at the bell, reminders on the minute). Otherwise they come within ~10 minutes. */
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Whether the live lesson notification can be promoted to a Live Update chip. */
    fun canPostPromoted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true

    /** Opens the system page where the student allows exact alarms for Meldo. */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Opens Meldo's notification settings (after the permission was denied twice). */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Returns a function that asks for POST_NOTIFICATIONS (like
     * `Reminders.requestAuthorization()` on iOS). Calls [onResult] right away
     * with `true` when notifications are already allowed.
     */
    @Composable
    fun rememberPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
        val context = LocalContext.current
        val callback = rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            callback.value(granted && hasPermission(context))
        }
        return {
            if (hasPermission(context)) {
                callback.value(true)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Nothing to ask for: notifications are switched off in the settings.
                callback.value(false)
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
