package io.github.alinourix.taski.core.domain.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How a task shows up on a given day of a plan. */
enum class SlotKind {
    /** A task that begins and ends on this day. */
    Single,

    /** The first day of a task that runs over several days. */
    Start,

    /** The last day of a task that runs over several days. */
    End,

    /** A several-day task passing through a day that is shown anyway (today, or the first day in view). */
    Continue,
}

/** One task on one day of a plan. [dayNumber] counts from 1 within the task's own run of days. */
data class PlanSlot(val id: String, val span: Span, val kind: SlotKind, val lane: Int, val dayNumber: Int)

/** A several-day task passing along a row, drawn as a rail; it begins or ends on the row that says so. */
data class Rail(val id: String, val lane: Int, val begins: Boolean, val ends: Boolean)

sealed interface PlanRow {
    val rails: List<Rail>

    /** A day that has something to show. */
    data class Day(val date: LocalDate, val slots: List<PlanSlot>, override val rails: List<Rail>) : PlanRow

    /** A stretch of days on which nothing begins or ends; several-day tasks still pass through as rails. */
    data class Quiet(val from: LocalDate, val to: LocalDate, override val rails: List<Rail>) : PlanRow {
        val days: Int get() = ChronoUnit.DAYS.between(from, to).toInt() + 1
    }
}

/**
 * A week or a month laid out as the days that matter, not every day: a day is shown when a task
 * begins or ends on it, when it is today, and the days between fold into one quiet row. A task that
 * runs over several days is a rail along the side, so "from where to where" reads at a glance.
 */
object SchedulePlan {
    const val MaxLanes: Int = 3

    fun build(spans: List<Pair<String, Span>>, first: LocalDate, dayCount: Int, today: LocalDate): List<PlanRow> {
        val days = (0 until dayCount).map { first.plusDays(it.toLong()) }
        val last = days.last()
        val inView = spans.filter { (_, span) -> !span.lastDay.isBefore(first) && !span.firstDay.isAfter(last) }

        // Rails: only tasks that run over several days, packed so none shares a lane with another.
        val multi = inView.filter { it.second.dayCount > 1 }
        val laneRows = TaskSchedule.packRows(multi.map { (_, span) -> TaskSchedule.dayOffsets(span, first, dayCount)!! })
        val laneOf = multi.mapIndexed { index, (id, _) -> id to laneRows[index].coerceAtMost(MaxLanes - 1) }.toMap()

        fun railsOn(day: LocalDate) = multi.filter { (_, span) -> span.coversDay(day) }.map { (id, span) ->
            Rail(id, laneOf.getValue(id), begins = day == span.firstDay, ends = day == span.lastDay)
        }

        fun slotsOn(day: LocalDate): List<PlanSlot> = inView.mapNotNull { (id, span) ->
            if (!span.coversDay(day)) return@mapNotNull null
            val number = ChronoUnit.DAYS.between(span.firstDay, day).toInt() + 1
            val kind = when {
                span.dayCount == 1 -> SlotKind.Single
                day == span.firstDay -> SlotKind.Start
                day == span.lastDay -> SlotKind.End
                day == today || day == first -> SlotKind.Continue
                else -> return@mapNotNull null
            }
            PlanSlot(id, span, kind, laneOf[id] ?: 0, number)
        }.sortedWith(compareBy({ it.kind == SlotKind.Continue }, { !it.span.timed }, { it.span.start }, { it.id }))

        val rows = mutableListOf<PlanRow>()
        var quietFrom: LocalDate? = null
        var quietRails: List<Rail> = emptyList()
        fun closeQuiet(until: LocalDate) {
            quietFrom?.let { rows += PlanRow.Quiet(it, until, quietRails) }
            quietFrom = null
        }
        for (day in days) {
            val slots = slotsOn(day)
            if (slots.isNotEmpty() || day == today) {
                closeQuiet(day.minusDays(1))
                rows += PlanRow.Day(day, slots, railsOn(day))
            } else {
                if (quietFrom == null) {
                    quietFrom = day
                    quietRails = railsOn(day)
                }
            }
        }
        closeQuiet(last)
        return rows
    }
}
