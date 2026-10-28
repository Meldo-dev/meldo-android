package app.meldeplanner.android.model

import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.BellPeriod
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.UntisExamRecord
import app.meldeplanner.android.data.UntisPeriodRecord
import app.meldeplanner.android.data.UntisTimetable
import java.time.Instant

/**
 * One block in the Untis-style timetable: a period, or a double period merged
 * across a short break. Built from the dated WebUntis periods where they exist,
 * otherwise from the student's own weekly lessons. Port of AppModel+Week.swift.
 */
data class TimetableBlock(
    val id: String,
    val date: Instant,
    val startMinute: Int,
    val endMinute: Int,
    /** Short label for narrow columns, e.g. "MAT". */
    val shortTitle: String,
    val subjectName: String,
    val teacher: String,
    val room: String? = null,
    val originalRoom: String? = null,
    val kind: Kind,
    val info: String? = null,
    val subjectID: String? = null,
    /** The student's own lesson, when the block comes from their timetable. */
    val lessonID: String? = null,
    /** Side-by-side layout when blocks overlap (e.g. a course and an event). */
    val lane: Int = 0,
    val lanes: Int = 1,
    /** Horizontal position and width as fractions of the day column. */
    val xShare: Double = 0.0,
    val widthShare: Double = 1.0,
    /** Parallel groups at the same time (e.g. FPR with DH or HU); the student picks theirs. */
    val alternatives: List<TimetableBlock> = emptyList(),
    /** Homework is due in this lesson. */
    val hasHomework: Boolean = false,
    /** No subject: a workshop, excursion or school event; the title is WebUntis' text. */
    val isEvent: Boolean = false,
) {
    enum class Kind { regular, substitution, cancelled, exam, roomChange }

    /** Untis shows a changed room in green, independent of other changes. */
    val roomChanged: Boolean get() = originalRoom != null

    /** A Vertretung: the teacher changed. */
    val teacherChanged: Boolean get() = kind == Kind.substitution && !isEvent
}

private val blockOrder = compareBy<TimetableBlock>({ it.startMinute }, { it.endMinute })

/** Blocks for one day: double periods merged per course, parallel groups folded, overlaps in lanes. */
fun AppModel.timetableBlocks(on: Instant): List<TimetableBlock> {
    val day = calendar.startOfDay(on)
    val untis = data.untisTimetable
    var blocks: List<TimetableBlock> = if (untis != null && untisCovers(day, untis)) {
        val exams = (data.untisExtras?.exams ?: emptyList()).filter { calendar.isSameDay(it.date, day) }
        untis.periods.filter { calendar.isSameDay(it.date, day) }.map { block(it, exams) }
    } else {
        occurrences(day).map { o ->
            val subject = subject(o)
            TimetableBlock(
                id = "lesson-${o.lessonId}-${o.startMinute}",
                date = day,
                startMinute = o.startMinute.toInt(),
                endMinute = o.endMinute.toInt(),
                shortTitle = subject?.displayShortName ?: "?",
                subjectName = subject?.name ?: L("Unbekanntes Fach"),
                teacher = subject?.teacher ?: "",
                room = o.room,
                kind = when {
                    o.cancelled -> TimetableBlock.Kind.cancelled
                    o.substituted -> TimetableBlock.Kind.substitution
                    else -> TimetableBlock.Kind.regular
                },
                subjectID = subject?.id,
                lessonID = o.lessonId,
            )
        }
    }
    // Homework due on this day in a subject marks that subject's lessons.
    val homeworkSubjects = data.tasks.filter {
        it.kind == TaskKind.homework && !it.isDone && it.dueDate?.let { d -> calendar.isSameDay(d, day) } == true
    }.mapNotNull { it.subjectID }.toSet()
    blocks = blocks.map { if (it.subjectID != null && it.subjectID in homeworkSubjects) it.copy(hasHomework = true) else it }
    return assignLanes(pickCourses(mergeDoublePeriods(blocks.sortedWith(blockOrder))))
}

/** Key for a parallel group in its weekly slot: subject and teacher. */
fun AppModel.courseKey(block: TimetableBlock): String =
    "${calendar.mondayIndex(block.date)}|${block.startMinute}|${block.subjectName}|${block.teacher}"

/** The student attends [chosen]; the other groups in that slot are hidden every week. */
fun AppModel.chooseCourse(chosen: TimetableBlock, options: List<TimetableBlock>) {
    val show = courseKey(chosen)
    val hide = options.map { courseKey(it) }.filter { it != show }
    mutate { d ->
        val keys = d.preferences.hiddenCourses.toMutableSet()
        keys.addAll(hide)
        keys.remove(show)
        d.preferences.hiddenCourses = keys.sorted().toMutableList()
    }
}

/**
 * Lessons that start and end together are parallel groups: one block shows the
 * student's group, the others are a tap away, like WebUntis. Cancellations and
 * substitutions stay visible side by side so no change is hidden.
 */
private fun AppModel.pickCourses(blocks: List<TimetableBlock>): List<TimetableBlock> {
    val hidden = data.preferences.hiddenCourses.toSet()
    val result = mutableListOf<TimetableBlock>()
    val used = mutableSetOf<String>()
    for (block in blocks) {
        if (block.id in used) continue
        val group = blocks.filter {
            it.id !in used && it.startMinute == block.startMinute && it.endMinute == block.endMinute
        }
        val foldable = group.size > 1 && group.all {
            it.kind != TimetableBlock.Kind.cancelled && it.kind != TimetableBlock.Kind.substitution
        }
        if (!foldable) {
            result.add(block)
            used.add(block.id)
            continue
        }
        // Without a choice yet, an exam is shown first so it can't hide behind the count.
        // Courses chosen from Melden are hidden without a teacher ("weekday|start|subject|").
        val visible = group.filter { b ->
            val key = courseKey(b)
            val withoutTeacher = key.split('|').take(3).joinToString("|") + "|"
            key !in hidden && withoutTeacher !in hidden
        }
        val pick = visible.firstOrNull { it.kind == TimetableBlock.Kind.exam } ?: visible.firstOrNull() ?: group[0]
        result.add(pick.copy(alternatives = group.filter { it.id != pick.id }))
        used.addAll(group.map { it.id })
    }
    return result
}

/** Saturday school: own lessons or WebUntis periods on a Saturday. */
val AppModel.hasSaturdayLessons: Boolean
    get() = data.lessons.any { it.weekday == 5 } ||
        (data.untisTimetable?.periods?.any { calendar.mondayIndex(it.date) == 5 } ?: false)

/** Whether WebUntis data covers [day] (school look-ahead limits end earlier). */
private fun AppModel.untisCovers(day: Instant, untis: UntisTimetable): Boolean =
    calendar.startOfDay(untis.firstDay) <= day && day <= calendar.startOfDay(untis.lastDay)

/** The last day the school lets the student see, when WebUntis is connected. */
val AppModel.untisVisibleUntil: Instant?
    get() {
        val untis = data.untisTimetable ?: return null
        if (data.untis == null) return null
        return calendar.startOfDay(untis.lastDay)
    }

/** The bell schedule: from WebUntis, or the student's own lesson times. */
val AppModel.bellSchedule: List<BellPeriod>
    get() {
        data.untisTimetable?.bells?.takeIf { it.isNotEmpty() }?.let { return it }
        val slots = data.lessons.map { it.startMinute to it.endMinute }.distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
        return slots.mapIndexed { i, (start, end) -> BellPeriod("${i + 1}", start, end) }
    }

/**
 * An exam is either marked in the timetable or listed in WebUntis' exam list for
 * the same day, time and subject (many schools only do the latter).
 */
private fun AppModel.block(p: UntisPeriodRecord, exams: List<UntisExamRecord>): TimetableBlock {
    val subject = data.subjects.firstOrNull { it.untisKey == p.subjectKey }
    val names = listOf(p.subjectShort, p.subjectLong, p.subjectKey).filter { it.isNotEmpty() }
    val listed = exams.any { e ->
        val sameTime = e.startMinute < p.endMinute && p.startMinute < e.endMinute
        val sameSubject = e.subject.isEmpty() || names.any { it.equals(e.subject, ignoreCase = true) }
        sameTime && sameSubject
    }
    val kind = when {
        p.cancelled -> TimetableBlock.Kind.cancelled
        p.exam || listed -> TimetableBlock.Kind.exam
        p.irregular -> TimetableBlock.Kind.substitution
        p.originalRoom != null -> TimetableBlock.Kind.roomChange
        else -> TimetableBlock.Kind.regular
    }
    // Events have no subject; WebUntis names them in the lesson text.
    val isEvent = p.subjectShort.isEmpty() && p.subjectLong.isEmpty()
    val eventTitle = p.info?.split(" · ")?.first() ?: L("Veranstaltung")
    return TimetableBlock(
        id = "untis-${p.id}",
        date = p.date,
        startMinute = p.startMinute,
        endMinute = p.endMinute,
        shortTitle = if (isEvent) eventTitle else p.subjectShort.ifEmpty { p.subjectLong.take(4) },
        subjectName = if (isEvent) eventTitle else subject?.name ?: p.subjectLong.ifEmpty { p.subjectShort },
        teacher = p.teacher,
        room = p.room,
        originalRoom = p.originalRoom,
        kind = kind,
        info = if (isEvent && p.info == eventTitle) null else p.info,
        subjectID = subject?.id,
        isEvent = isEvent,
    )
}

/** Joins back-to-back periods of the same course (as Untis draws them). */
private fun mergeDoublePeriods(blocks: List<TimetableBlock>): List<TimetableBlock> {
    val merged = mutableListOf<TimetableBlock>()
    for (block in blocks) {
        val i = merged.indexOfLast { last ->
            last.subjectName == block.subjectName && last.teacher == block.teacher &&
                last.room == block.room && last.kind == block.kind && last.info == block.info &&
                block.startMinute >= last.endMinute && block.startMinute - last.endMinute <= 25
        }
        if (i >= 0) {
            merged[i] = merged[i].copy(endMinute = block.endMinute, hasHomework = merged[i].hasHomework || block.hasHomework)
        } else {
            merged.add(block)
        }
    }
    return merged.sortedWith(blockOrder)
}

/**
 * Overlapping blocks share the column width. Like WebUntis, what takes place gets
 * the room and cancelled lessons a narrow strip at the right.
 */
private fun assignLanes(blocks: List<TimetableBlock>): List<TimetableBlock> {
    val laneEnds = mutableListOf<Int>()
    val laned = blocks.map { b ->
        val free = laneEnds.indexOfFirst { it <= b.startMinute }
        if (free >= 0) {
            laneEnds[free] = b.endMinute
            b.copy(lane = free)
        } else {
            laneEnds.add(b.endMinute)
            b.copy(lane = laneEnds.size - 1)
        }
    }
    return laned.map { b ->
        val overlapping = laned.filter { it.startMinute < b.endMinute && b.startMinute < it.endMinute }
        val lanes = (overlapping.maxOfOrNull { it.lane } ?: 0) + 1
        var width = 1.0 / lanes
        var x = b.lane * width
        val active = overlapping.filter { it.kind != TimetableBlock.Kind.cancelled }.map { it.lane }.toSortedSet().toList()
        val cancelledOnly = (overlapping.filter { it.kind == TimetableBlock.Kind.cancelled }.map { it.lane }.toSet() - active.toSet()).sorted()
        if (active.isNotEmpty() && cancelledOnly.isNotEmpty()) {
            val activeShare = 0.72
            val cancelledIndex = cancelledOnly.indexOf(b.lane)
            val activeIndex = active.indexOf(b.lane)
            if (cancelledIndex >= 0) {
                width = (1 - activeShare) / cancelledOnly.size
                x = activeShare + cancelledIndex * width
            } else if (activeIndex >= 0) {
                width = activeShare / active.size
                x = activeIndex * width
            }
        }
        b.copy(lanes = lanes, xShare = x, widthShare = width)
    }
}
