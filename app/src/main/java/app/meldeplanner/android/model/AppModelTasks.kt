package app.meldeplanner.android.model

import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.TaskPriority
import app.meldeplanner.android.data.TaskRecurrence
import app.meldeplanner.android.surfaces.Reminders
import uniffi.melde_ffi.FfiPriority
import uniffi.melde_ffi.FfiRecurrence
import uniffi.melde_ffi.FfiTask
import uniffi.melde_ffi.FfiTaskGroup
import uniffi.melde_ffi.FfiTaskKind
import uniffi.melde_ffi.tasksGrouped
import uniffi.melde_ffi.tasksHomeworkStreak
import uniffi.melde_ffi.tasksNextDue
import uniffi.melde_ffi.tasksNextLessonDate
import uniffi.melde_ffi.tasksOnTimeRate
import java.time.Duration
import java.time.Instant

// Hausaufgaben and to-dos. Grouping, recurrence, streaks and due-date suggestions
// come from the Rust core (melde-tasks). Port of AppModel+Tasks.swift.

data class TaskSection(val group: FfiTaskGroup, val tasks: List<SchoolTask>) {
    val id: String get() = group.name
}

val AppModel.tasks: List<SchoolTask> get() = data.tasks

fun AppModel.task(id: String): SchoolTask? = data.tasks.firstOrNull { it.id == id }

fun AppModel.upsert(task: SchoolTask) {
    val t = task.copy(title = task.title.trim())
    if (t.title.isEmpty()) return
    t.dueDate = t.dueDate?.let(calendar::startOfDay)
    t.updatedAt = currentDate
    mutate { d ->
        val i = d.tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) d.tasks[i] = t else d.tasks.add(t)
    }
    context?.let { Reminders.schedule(it, t, subject(t.subjectID)?.name) }
}

fun AppModel.deleteTask(id: String) {
    mutate { d -> d.tasks.removeAll { it.id == id } }
    context?.let { Reminders.cancel(it, id) }
}

/**
 * Checks a task off (or reopens it). Completing a recurring task creates its
 * next instance with the due date computed in Rust.
 */
fun AppModel.toggleDone(id: String) {
    val original = task(id) ?: return
    val now = currentDate
    var next: SchoolTask? = null
    if (!original.isDone && original.recurrence != TaskRecurrence.never) {
        val nextDue = runCatching { tasksNextDue(ffiTask(original), calendar.ffiDate(now)) }.getOrNull()
        if (nextDue != null) {
            val dueDate = calendar.instant(nextDue)
            next = SchoolTask(
                title = original.title, notes = original.notes, kind = original.kind,
                subjectID = original.subjectID, dueDate = dueDate, priority = original.priority,
                recurrence = original.recurrence,
                reminderAt = original.reminderAt?.let { shiftReminder(it, original.dueDate, dueDate) },
            )
        }
    }
    mutate { d ->
        d.tasks.firstOrNull { it.id == id }?.let {
            it.completedAt = if (original.isDone) null else now
            it.updatedAt = now
        }
        next?.let { d.tasks.add(it) }
    }
    context?.let { ctx ->
        if (original.isDone) Reminders.schedule(ctx, original, subject(original.subjectID)?.name)
        else Reminders.cancel(ctx, id)
        next?.let { Reminders.schedule(ctx, it, subject(it.subjectID)?.name) }
    }
}

/** Keeps a reminder at the same offset from the due date for the next instance. */
private fun shiftReminder(reminder: Instant, oldDue: Instant?, newDue: Instant): Instant? =
    oldDue?.let { reminder.plus(Duration.between(it, newDue)) }

fun AppModel.taskSections(today: Instant, kind: TaskKind? = null): List<TaskSection> {
    val filtered = data.tasks.filter { kind == null || it.kind == kind }
    val byID = filtered.associateBy { it.id }
    val sections = runCatching { tasksGrouped(filtered.map(::ffiTask), calendar.ffiDate(today)) }.getOrDefault(emptyList())
    return sections.map { s -> TaskSection(s.group, s.taskIds.mapNotNull { byID[it] }) }
}

/** Open homework due today or tomorrow, most urgent first. */
fun AppModel.upcomingHomework(today: Instant, limit: Int = 3): List<SchoolTask> =
    taskSections(today, TaskKind.homework)
        .filter { it.group in setOf(FfiTaskGroup.OVERDUE, FfiTaskGroup.TODAY, FfiTaskGroup.TOMORROW) }
        .flatMap { it.tasks }
        .take(limit)

fun AppModel.homeworkStreak(asOf: Instant): Int =
    runCatching { tasksHomeworkStreak(data.tasks.map(::ffiTask), calendar.ffiDate(asOf)).toInt() }.getOrDefault(0)

/** Share of this week's homework done on time, `null` if none was due yet. */
fun AppModel.homeworkOnTimeRate(weekContaining: Instant): Double? {
    val start = calendar.weekStart(weekContaining)
    val last = minOf(calendar.addDays(start, 6), weekContaining)
    return runCatching {
        tasksOnTimeRate(data.tasks.map(::ffiTask), calendar.ffiDate(start), calendar.ffiDate(last))
    }.getOrNull()
}

/** The subject's next lesson day after [date]: the usual due date for homework. */
fun AppModel.nextLessonDay(subjectID: String, after: Instant): Instant? =
    runCatching { tasksNextLessonDate(ffiTimetable, subjectID, calendar.ffiDate(after), 28u) }
        .getOrNull()?.let(calendar::instant)

fun AppModel.tasks(dueOn: Instant): List<SchoolTask> =
    data.tasks.filter { t -> t.dueDate?.let { calendar.isSameDay(it, dueOn) } == true }

private fun AppModel.ffiTask(t: SchoolTask): FfiTask = FfiTask(
    id = t.id,
    title = t.title,
    kind = if (t.kind == TaskKind.homework) FfiTaskKind.HOMEWORK else FfiTaskKind.TODO,
    due = t.dueDate?.let(calendar::ffiDate),
    completedOn = t.completedAt?.let(calendar::ffiDate),
    priority = when (t.priority) {
        TaskPriority.low -> FfiPriority.LOW
        TaskPriority.normal -> FfiPriority.NORMAL
        TaskPriority.high -> FfiPriority.HIGH
    },
    recurrence = when (t.recurrence) {
        TaskRecurrence.never -> FfiRecurrence.NEVER
        TaskRecurrence.daily -> FfiRecurrence.DAILY
        TaskRecurrence.weekdays -> FfiRecurrence.WEEKDAYS
        TaskRecurrence.weekly -> FfiRecurrence.WEEKLY
        TaskRecurrence.biweekly -> FfiRecurrence.BIWEEKLY
    },
)
