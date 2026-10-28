package app.meldeplanner.android.core

import uniffi.melde_ffi.FfiDate
import uniffi.melde_ffi.FfiDateTime
import uniffi.melde_ffi.FfiWeekday
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The student's calendar (Monday-first weeks, the device time zone). The
 * counterpart of Foundation's `Calendar` plus meldo-ios/MeldeCore/CalendarBridge:
 * the Rust core never sees time zones, this class resolves instants first.
 */
class MeldoCalendar(val zone: ZoneId = ZoneId.systemDefault()) {
    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    fun instant(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    fun startOfDay(instant: Instant): Instant = instant(localDate(instant))

    fun isSameDay(a: Instant, b: Instant): Boolean = localDate(a) == localDate(b)

    fun addDays(instant: Instant, days: Long): Instant =
        instant.atZone(zone).plusDays(days).toInstant()

    fun addWeeks(instant: Instant, weeks: Long): Instant =
        instant.atZone(zone).plusWeeks(weeks).toInstant()

    /** 0 = Monday … 6 = Sunday. */
    fun mondayIndex(instant: Instant): Int = localDate(instant).dayOfWeek.value - 1

    /** Monday 00:00 of the week containing [instant]. */
    fun weekStart(instant: Instant): Instant =
        instant(localDate(instant).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

    /** Whole weeks between the weeks containing [from] and [to]. */
    fun weeksBetween(from: Instant, to: Instant): Long =
        ChronoUnit.WEEKS.between(localDate(weekStart(from)), localDate(weekStart(to)))

    fun minuteOfDay(instant: Instant): Int {
        val t = instant.atZone(zone)
        return t.hour * 60 + t.minute
    }

    /** [day] at [minute] after midnight. */
    fun at(day: Instant, minute: Int): Instant =
        ZonedDateTime.of(localDate(day).atStartOfDay(), zone).plusMinutes(minute.toLong()).toInstant()

    fun ffiDate(instant: Instant): FfiDate {
        val d = localDate(instant)
        return FfiDate(d.year, d.monthValue.toUByte(), d.dayOfMonth.toUByte())
    }

    fun ffiDateTime(instant: Instant): FfiDateTime =
        FfiDateTime(ffiDate(instant), minuteOfDay(instant).toUShort())

    /** Midnight of [date] in this calendar's zone. */
    fun instant(date: FfiDate): Instant =
        instant(LocalDate.of(date.year, date.month.toInt(), date.day.toInt()))

    companion object {
        val current: MeldoCalendar get() = MeldoCalendar()
    }
}

/** Monday-first index, 0…6. */
fun ffiWeekday(mondayIndex: Int): FfiWeekday = when (mondayIndex) {
    0 -> FfiWeekday.MONDAY
    1 -> FfiWeekday.TUESDAY
    2 -> FfiWeekday.WEDNESDAY
    3 -> FfiWeekday.THURSDAY
    4 -> FfiWeekday.FRIDAY
    5 -> FfiWeekday.SATURDAY
    else -> FfiWeekday.SUNDAY
}

val FfiWeekday.mondayIndex: Int
    get() = when (this) {
        FfiWeekday.MONDAY -> 0
        FfiWeekday.TUESDAY -> 1
        FfiWeekday.WEDNESDAY -> 2
        FfiWeekday.THURSDAY -> 3
        FfiWeekday.FRIDAY -> 4
        FfiWeekday.SATURDAY -> 5
        FfiWeekday.SUNDAY -> 6
    }
