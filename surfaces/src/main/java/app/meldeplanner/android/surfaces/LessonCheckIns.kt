package app.meldeplanner.android.surfaces

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.meldeplanner.android.surfaces.R
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.ParticipationEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import uniffi.melde_ffi.timetableOccurrencesOn
import java.time.Instant
import java.time.LocalDate
import app.meldeplanner.android.model.meldoModel
import app.meldeplanner.android.core.Notifications

/**
 * "Hast du dich gemeldet?" after each lesson, answerable from the notification
 * without opening the app. Port of meldo-ios/Shared/LessonCheckIns.swift.
 * Cancelled lessons, holidays and lessons with a logged Meldung are skipped.
 *
 * iOS queues one notification request per lesson. Android has no queue for
 * future notifications, so the plan (same 7 days, same limit, same skipping
 * rules) is kept in private preferences and one alarm wakes [SurfacesReceiver]
 * at the next bell, which posts the due check-ins and arms the next one.
 */
object LessonCheckIns {
    internal const val ACTION_DUE = "app.meldeplanner.android.CHECKIN_DUE"
    internal const val ACTION_ANSWER = "app.meldeplanner.android.CHECKIN_ANSWER"
    internal const val EXTRA_ANSWER = "answer"
    internal const val EXTRA_ID = "id"
    internal const val EXTRA_LESSON = "lessonID"
    internal const val EXTRA_SUBJECT = "subjectID"
    internal const val EXTRA_RECORDED_AT = "recordedAt"

    private const val ONCE = "CHECKIN_ONCE"
    private const val TWICE = "CHECKIN_TWICE"
    private const val SKIP = "CHECKIN_SKIP"

    private const val PREFIX = "checkin-"

    /** Same limit as iOS (which keeps at most 64 pending notifications). */
    private const val LIMIT = 40
    private const val DAYS_AHEAD = 7L

    /** A check-in posted late (phone was off at the bell) is still useful for this long. */
    private const val LATE_MILLIS = 2 * 60 * 60 * 1000L

    private const val PREFS = "meldo.surfaces"
    private const val KEY_PLAN = "checkins.plan"

    @Serializable
    internal data class Planned(
        val id: String,
        val lessonID: String,
        val subjectID: String,
        val subjectName: String,
        /** One minute after the bell, epoch millis. */
        val fireAt: Long,
        /** Logged two minutes before the bell so it belongs to that lesson, epoch millis. */
        val recordedAt: Long,
    )

    private val planSerializer = ListSerializer(Planned.serializer())

    private fun id(lessonID: String, day: LocalDate) =
        "$PREFIX$lessonID-${day.year}-${day.monthValue}-${day.dayOfMonth}"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadPlan(context: Context): List<Planned> =
        prefs(context).getString(KEY_PLAN, null)
            ?.let { runCatching { Json.decodeFromString(planSerializer, it) }.getOrNull() }
            ?: emptyList()

    private fun savePlan(context: Context, plan: List<Planned>) {
        prefs(context).edit().putString(KEY_PLAN, Json.encodeToString(planSerializer, plan)).apply()
    }

    private fun alarm(context: Context) = Alarms.pendingBroadcast(context, ACTION_DUE, "next")

    /** Replaces all pending check-ins with the next week's lessons. */
    @Synchronized
    fun reschedule(context: Context, data: AppData, calendar: MeldoCalendar, now: Instant) {
        if (!data.preferences.askAfterLesson) {
            savePlan(context, emptyList())
            Alarms.cancel(context, alarm(context))
            return
        }
        val timetable = MeldeEngine.timetable(data, calendar)
        // Due but not posted yet (inexact alarms can come minutes after the bell): keep them,
        // as iOS keeps a delivered-late request in its queue.
        val nowMillis = now.toEpochMilli()
        val plan = loadPlan(context).filter {
            it.fireAt <= nowMillis && nowMillis - it.fireAt <= LATE_MILLIS &&
                MeldeEngine.count(data, it.lessonID, Instant.ofEpochMilli(it.recordedAt), calendar) == 0
        }.toMutableList()
        val today = calendar.startOfDay(now)
        days@ for (offset in 0 until DAYS_AHEAD) {
            val day = calendar.addDays(today, offset)
            val occurrences = runCatching { timetableOccurrencesOn(timetable, calendar.ffiDate(day)) }.getOrDefault(emptyList())
            for (occurrence in occurrences) {
                if (occurrence.cancelled) continue
                if (plan.size >= LIMIT) break@days
                val end = calendar.at(day, occurrence.endMinute.toInt() + 1)
                if (!end.isAfter(now)) continue
                if (MeldeEngine.count(data, occurrence.lessonId, day, calendar) > 0) continue
                val id = id(occurrence.lessonId, calendar.localDate(day))
                if (plan.any { it.id == id }) continue
                val subject = MeldeEngine.subject(data, occurrence.subjectId)
                plan += Planned(
                    id = id,
                    lessonID = occurrence.lessonId,
                    subjectID = occurrence.subjectId,
                    subjectName = subject?.name ?: L("Die Stunde"),
                    fireAt = end.toEpochMilli(),
                    recordedAt = end.toEpochMilli() - 120_000,
                )
            }
        }
        savePlan(context, plan)
        arm(context, plan)
    }

    private fun arm(context: Context, plan: List<Planned>) {
        val next = plan.minOfOrNull { it.fireAt }
        if (next == null) {
            Alarms.cancel(context, alarm(context))
        } else {
            Alarms.set(context, Instant.ofEpochMilli(next), alarm(context))
        }
    }

    /** A Meldung was logged during the lesson: no need to ask afterwards. */
    @Synchronized
    fun didRecord(context: Context, entry: ParticipationEntry, calendar: MeldoCalendar) {
        val lessonID = entry.lessonID ?: return
        val removed = id(lessonID, calendar.localDate(entry.recordedAt))
        val plan = loadPlan(context)
        val remaining = plan.filterNot { it.id == removed }
        if (remaining.size == plan.size) return
        savePlan(context, remaining)
        arm(context, remaining)
    }

    /** The alarm at the bell: posts the check-ins that are due, then plans the rest. */
    internal fun postDue(context: Context) {
        val model = context.meldoModel
        model.reloadFromDisk()
        val data = model.data
        val calendar = model.calendar
        val now = System.currentTimeMillis()
        val due = synchronized(this) {
            val plan = loadPlan(context)
            val (ready, later) = plan.partition { it.fireAt <= now + 30_000 }
            savePlan(context, later)
            arm(context, later)
            ready
        }
        if (!data.preferences.askAfterLesson || !Notifications.hasPermission(context)) return
        Notifications.ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (item in due) {
            if (now - item.fireAt > LATE_MILLIS) continue
            // Logged meanwhile (widget, tile, app): skip, as iOS removes the pending request.
            if (MeldeEngine.count(data, item.lessonID, Instant.ofEpochMilli(item.recordedAt), calendar) > 0) continue
            manager.notify(item.id.hashCode(), notification(context, item))
        }
    }

    private fun notification(context: Context, item: Planned): Notification {
        fun action(answer: String, title: String) = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_surfaces_hand), title, answerIntent(context, item, answer),
        ).build()
        return Notification.Builder(context, Notifications.CHANNEL_CHECK_INS)
            .setSmallIcon(R.drawable.ic_surfaces_hand)
            .setContentTitle(L("%@ ist vorbei", item.subjectName))
            .setContentText(L("Hast du dich gemeldet?"))
            .setGroup("checkins")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(SurfaceIntents.openApp(context, null))
            .setAutoCancel(true)
            .addAction(action(ONCE, L("Ja, einmal")))
            .addAction(action(TWICE, L("Ja, zweimal")))
            .addAction(action(SKIP, L("Diesmal nicht")))
            .build()
    }

    private fun answerIntent(context: Context, item: Planned, answer: String): PendingIntent =
        Alarms.pendingBroadcast(context, ACTION_ANSWER, "${item.id}/$answer") {
            putExtra(EXTRA_ANSWER, answer)
            putExtra(EXTRA_ID, item.id)
            putExtra(EXTRA_LESSON, item.lessonID)
            putExtra(EXTRA_SUBJECT, item.subjectID)
            putExtra(EXTRA_RECORDED_AT, item.recordedAt)
        }

    /** Handles "Ja, einmal" / "Ja, zweimal" / "Diesmal nicht". Returns whether data changed. */
    internal fun handle(context: Context, intent: Intent): Boolean {
        intent.getStringExtra(EXTRA_ID)?.let {
            context.getSystemService(NotificationManager::class.java)?.cancel(it.hashCode())
        }
        val count = when (intent.getStringExtra(EXTRA_ANSWER)) {
            ONCE -> 1
            TWICE -> 2
            else -> return false
        }
        val lessonID = intent.getStringExtra(EXTRA_LESSON) ?: return false
        val subjectID = intent.getStringExtra(EXTRA_SUBJECT) ?: return false
        val recordedAt = intent.getLongExtra(EXTRA_RECORDED_AT, -1L).takeIf { it > 0 } ?: return false
        val day = Instant.ofEpochMilli(recordedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        var added = false
        // Through the shared model: one store lock, widgets and the tile reload afterwards.
        // The notification can be answered long after it was posted: count a lesson once,
        // and don't bring back a subject deleted meanwhile.
        context.meldoModel.mutate { d ->
            val counted = d.participation.any {
                it.lessonID == lessonID && it.recordedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == day
            }
            if (counted) return@mutate
            val subject = subjectID.takeIf { id -> d.subjects.any { it.id == id } }
            d.participation.addAll((0 until count).map { i ->
                ParticipationEntry(subjectID = subject, lessonID = lessonID, recordedAt = Instant.ofEpochMilli(recordedAt + i * 1000L))
            })
            added = true
        }
        return added
    }
}
