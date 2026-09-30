package io.github.alinourix.taski.core.domain.schedule

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * When a task is meant to happen: from a start to a due moment. Dates are the
 * spine; a time on either end is optional, so a task can be a block in a day
 * (09:00 to 10:30), a run of days (Monday to Thursday) or both.
 */
data class Schedule(
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
) {
    val isEmpty: Boolean get() = startDate == null && dueDate == null
}

/** Which end of a schedule the person just moved, and so which one wins when they collide. */
enum class Anchor { Start, Due }

/** What a schedule looks like laid on a calendar. */
enum class SpanKind {
    /** Has a start: a block of time or a run of days. */
    Range,

    /** Only a due moment: a marker, not a block. */
    Deadline,
}

/**
 * A schedule as one interval on the calendar. [end] is exclusive, so a date-only
 * task due on the 3rd ends at the start of the 4th. [timed] is false when neither end
 * carries a time, which is what puts a task in the "all day" strip of a day view.
 */
data class Span(val start: LocalDateTime, val end: LocalDateTime, val timed: Boolean, val kind: SpanKind) {
    val firstDay: LocalDate get() = start.toLocalDate()

    /** The last day the span touches: an end at midnight belongs to the day before. */
    val lastDay: LocalDate
        get() = if (end > start) end.minusNanos(1).toLocalDate() else start.toLocalDate()

    fun coversDay(day: LocalDate): Boolean = !day.isBefore(firstDay) && !day.isAfter(lastDay)

    /** Days touched, counting both ends. */
    val dayCount: Int get() = ChronoUnit.DAYS.between(firstDay, lastDay).toInt() + 1
}

object TaskSchedule {
    private val DefaultLength: Duration = Duration.ofHours(1)

    /** The shortest a timed block is drawn, so a 5-minute task can still be seen and touched. */
    const val MinBlockMinutes: Int = 30
    const val MinutesPerDay: Int = 24 * 60

    fun spanOf(schedule: Schedule): Span? {
        val startDate = schedule.startDate
        val dueDate = schedule.dueDate
        return when {
            startDate == null && dueDate == null -> null
            startDate == null -> {
                val due = dueDate!!
                val time = schedule.dueTime
                if (time == null) Span(due.atStartOfDay(), due.plusDays(1).atStartOfDay(), timed = false, kind = SpanKind.Deadline)
                else due.atTime(time).let { Span(it, it, timed = true, kind = SpanKind.Deadline) }
            }
            dueDate == null -> {
                val time = schedule.startTime
                if (time == null) Span(startDate.atStartOfDay(), startDate.plusDays(1).atStartOfDay(), timed = false, kind = SpanKind.Range)
                else startDate.atTime(time).let { Span(it, it.plus(DefaultLength), timed = true, kind = SpanKind.Range) }
            }
            else -> {
                val start = startDate.atTime(schedule.startTime ?: LocalTime.MIN)
                val end = schedule.dueTime?.let { dueDate.atTime(it) } ?: dueDate.plusDays(1).atStartOfDay()
                Span(start, maxOf(start, end), timed = schedule.startTime != null || schedule.dueTime != null, kind = SpanKind.Range)
            }
        }
    }

    /**
     * Makes a schedule consistent: a time needs its date, and the start must not
     * come after the due moment. When they collide, [anchor] says which end the
     * person just set, and the other end follows it.
     */
    fun coherent(schedule: Schedule, anchor: Anchor): Schedule {
        var (startDate, startTime, dueDate, dueTime) = schedule
        if (startDate == null) startTime = null
        if (dueDate == null) dueTime = null
        if (startDate == null || dueDate == null) return Schedule(startDate, startTime, dueDate, dueTime)

        val start = startDate.atTime(startTime ?: LocalTime.MIN)
        val due = dueDate.atTime(dueTime ?: LocalTime.MAX)
        if (start <= due) return Schedule(startDate, startTime, dueDate, dueTime)
        return when (anchor) {
            Anchor.Start -> {
                // The due moment follows the start: same day, and not before the start's own time.
                val followTime = if (dueTime != null && startTime != null) startTime else dueTime?.takeIf { startTime == null }
                Schedule(startDate, startTime, startDate, followTime)
            }
            Anchor.Due -> {
                val followTime = if (startTime != null && dueTime != null) dueTime else startTime?.takeIf { dueTime == null }
                Schedule(dueDate, followTime, dueDate, dueTime)
            }
        }
    }

    /** Moves the whole schedule by whole days, keeping every time and the length. */
    fun shifted(schedule: Schedule, days: Long): Schedule = schedule.copy(
        startDate = schedule.startDate?.plusDays(days),
        dueDate = schedule.dueDate?.plusDays(days),
    )

    /**
     * Moves a timed block so it starts at [start], keeping its length. A task that
     * has no time yet takes one hour, which is what a block dropped on a day is.
     */
    fun movedTo(schedule: Schedule, start: LocalDateTime): Schedule {
        val span = spanOf(schedule)
        val length = if (span != null && span.timed && span.end > span.start) Duration.between(span.start, span.end) else DefaultLength
        val end = start.plus(length)
        return Schedule(start.toLocalDate(), start.toLocalTime(), end.toLocalDate(), end.toLocalTime())
    }

    /**
     * Moves a timed task by [minutes] (negative is earlier), keeping its length: a block moves
     * both ends, a deadline moves its one due moment. Null for a task that carries no time.
     */
    fun movedByMinutes(schedule: Schedule, minutes: Long): Schedule? {
        val span = spanOf(schedule) ?: return null
        if (!span.timed) return null
        if (span.kind == SpanKind.Deadline) {
            val due = span.end.plusMinutes(minutes)
            return Schedule(null, null, due.toLocalDate(), due.toLocalTime())
        }
        return movedTo(schedule, span.start.plusMinutes(minutes))
    }

    /** Sets the end of a timed block, never before its start. */
    fun withEnd(schedule: Schedule, end: LocalDateTime): Schedule {
        val start = spanOf(schedule)?.start ?: end.minus(DefaultLength)
        val safeEnd = maxOf(end, start.plusMinutes(5))
        return Schedule(start.toLocalDate(), start.toLocalTime(), safeEnd.toLocalDate(), safeEnd.toLocalTime())
    }

    /** Sets the start of a timed block, never after its end. */
    fun withStart(schedule: Schedule, start: LocalDateTime): Schedule {
        val end = spanOf(schedule)?.end ?: start.plus(DefaultLength)
        val safeStart = minOf(start, end.minusMinutes(5))
        return Schedule(safeStart.toLocalDate(), safeStart.toLocalTime(), end.toLocalDate(), end.toLocalTime())
    }

    /**
     * The minutes of [day] a timed span occupies, drawn at least [MinBlockMinutes] tall;
     * null if it does not touch the day. A deadline is drawn as the half hour leading up to it.
     */
    fun minutesOn(span: Span, day: LocalDate): IntRange? {
        if (!span.timed || !span.coversDay(day)) return null
        val midnight = day.atStartOfDay()
        fun minutes(moment: LocalDateTime) = Duration.between(midnight, moment).toMinutes().coerceIn(0, MinutesPerDay.toLong()).toInt()
        var from = minutes(span.start)
        var to = minutes(span.end)
        if (span.kind == SpanKind.Deadline) from = (to - MinBlockMinutes).coerceAtLeast(0)
        if (to - from < MinBlockMinutes) to = (from + MinBlockMinutes).coerceAtMost(MinutesPerDay).also { if (it - from < MinBlockMinutes) from = it - MinBlockMinutes }
        return from until to
    }

    /** A span's place among the days of a visible range: inclusive day offsets from [first], clipped to [days]. */
    fun dayOffsets(span: Span, first: LocalDate, days: Int): IntRange? {
        val from = ChronoUnit.DAYS.between(first, span.firstDay).toInt()
        val to = ChronoUnit.DAYS.between(first, span.lastDay).toInt()
        if (to < 0 || from > days - 1) return null
        return from.coerceAtLeast(0)..to.coerceAtMost(days - 1)
    }

    /**
     * Rows for bars laid over days: each bar goes in the first row where it does not
     * touch the previous one. Bars are given by their inclusive day offsets and come
     * back with the row each one took, in the same order.
     */
    fun packRows(bars: List<IntRange>): List<Int> {
        val order = bars.indices.sortedWith(compareBy({ bars[it].first }, { -(bars[it].last - bars[it].first) }, { it }))
        val rowEnds = mutableListOf<Int>()
        val rows = IntArray(bars.size)
        for (i in order) {
            val bar = bars[i]
            val row = rowEnds.indexOfFirst { it < bar.first }.let { if (it == -1) rowEnds.size.also { rowEnds.add(bar.last) } else it.also { r -> rowEnds[r] = bar.last } }
            rows[i] = row
        }
        return rows.toList()
    }

    /** Where a block sits among the blocks it overlaps within a day: its column, and how many columns share its time. */
    data class Column(val index: Int, val count: Int)

    /**
     * Side-by-side columns for blocks that overlap in time, as a day view draws them.
     * Blocks are half-open minute ranges; the result keeps their order.
     */
    fun columns(blocks: List<IntRange>): List<Column> {
        val order = blocks.indices.sortedWith(compareBy({ blocks[it].first }, { -(blocks[it].last - blocks[it].first) }, { it }))
        val index = IntArray(blocks.size)
        val count = IntArray(blocks.size)
        var cluster = mutableListOf<Int>()
        var clusterEnd = -1
        val columnEnds = mutableListOf<Int>()

        fun closeCluster() {
            val width = columnEnds.size.coerceAtLeast(1)
            cluster.forEach { count[it] = width }
            cluster = mutableListOf()
            columnEnds.clear()
        }

        for (i in order) {
            val block = blocks[i]
            if (cluster.isNotEmpty() && block.first > clusterEnd) closeCluster()
            val column = columnEnds.indexOfFirst { it < block.first }.let { c -> if (c == -1) columnEnds.size.also { columnEnds.add(block.last) } else c.also { columnEnds[c] = block.last } }
            index[i] = column
            cluster.add(i)
            clusterEnd = maxOf(clusterEnd, block.last)
        }
        if (cluster.isNotEmpty()) closeCluster()
        return blocks.indices.map { Column(index[it], count[it]) }
    }
}
