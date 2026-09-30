package io.github.alinourix.taski.feature.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.time.JalaliCalendar
import io.github.alinourix.taski.core.domain.time.JalaliDate
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.localizeDigits
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

internal enum class TimelineScale { Day, Week, Month }

/** Where the timeline is looking: the scale, the day it is centred on, and whether the unscheduled list is open. */
@Stable
internal class TimelineState(scale: TimelineScale, anchor: LocalDate) {
    var scale by mutableStateOf(scale)
    var anchor by mutableStateOf(anchor)
    var unscheduledOpen by mutableStateOf(false)

    /** A day as the precise hour grid (drag to move) rather than the agenda on a rail. */
    var grid by mutableStateOf(false)

    companion object {
        val Saver = Saver<TimelineState, List<Any>>(
            save = { listOf(it.scale.name, it.anchor.toEpochDay(), it.unscheduledOpen, it.grid) },
            restore = { saved ->
                TimelineState(TimelineScale.valueOf(saved[0] as String), LocalDate.ofEpochDay(saved[1] as Long)).also {
                    it.unscheduledOpen = saved[2] as Boolean
                    it.grid = saved[3] as Boolean
                }
            },
        )
    }
}

@Composable
internal fun rememberTimelineState(today: LocalDate): TimelineState =
    rememberSaveable(saver = TimelineState.Saver) { TimelineState(TimelineScale.Week, today) }

/** Tasks with no dates yet and still open: the ones the timeline cannot place. */
internal fun unscheduledOf(state: BoardState): List<TaskItem> =
    state.groups.flatMap { it.items }.distinctBy { it.id }.filter { it.task.schedule.isEmpty && it.task.status != TaskStatus.Done }

/** The days a timeline shows for [anchor]: the day, its week, or its month in the calendar in use. */
internal fun visibleDays(scale: TimelineScale, anchor: LocalDate, calendar: CalendarSystem, persian: Boolean): List<LocalDate> = when (scale) {
    TimelineScale.Day -> listOf(anchor)
    TimelineScale.Week -> {
        val first = anchor.with(TemporalAdjusters.previousOrSame(CalendarText.firstDayOfWeek(calendar, persian)))
        (0L..6L).map { first.plusDays(it) }
    }
    TimelineScale.Month -> monthDays(anchor, calendar)
}

private fun monthDays(anchor: LocalDate, calendar: CalendarSystem): List<LocalDate> = when (calendar) {
    CalendarSystem.Gregorian -> anchor.withDayOfMonth(1).let { first -> (0 until anchor.lengthOfMonth()).map { first.plusDays(it.toLong()) } }
    CalendarSystem.Jalali -> {
        val j = JalaliCalendar.fromGregorian(anchor)
        val first = JalaliCalendar.toGregorian(JalaliDate(j.year, j.month, 1))
        (0 until JalaliCalendar.monthLength(j.year, j.month)).map { first.plusDays(it.toLong()) }
    }
}

/** One step back or forward: a day, a week, or a whole calendar month. */
internal fun stepped(scale: TimelineScale, anchor: LocalDate, direction: Int, calendar: CalendarSystem): LocalDate = when (scale) {
    TimelineScale.Day -> anchor.plusDays(direction.toLong())
    TimelineScale.Week -> anchor.plusDays(7L * direction)
    TimelineScale.Month -> monthDays(anchor, calendar).let { if (direction > 0) it.last().plusDays(1) else it.first().minusDays(1) }
}

@Composable
internal fun rangeTitle(scale: TimelineScale, days: List<LocalDate>): String {
    val config = LocalUiConfig.current
    val first = days.first()
    return when (scale) {
        TimelineScale.Day -> CalendarText.weekdayLong(first.dayOfWeek, config.persian) +
            (if (config.persian) "، " else ", ") + CalendarText.date(first, config.calendar, config.persian, config.today)
        TimelineScale.Week -> CalendarText.date(first, config.calendar, config.persian, config.today) + " – " +
            CalendarText.date(days.last(), config.calendar, config.persian, config.today)
        TimelineScale.Month -> {
            val (year, month) = CalendarText.yearMonth(first, config.calendar)
            CalendarText.monthName(config.calendar, month, config.persian, long = true) + " " + year.toString().localizeDigits(config.persian)
        }
    }
}

/** A time on a day, as the grid's minute offset turns into. */
internal fun LocalDate.atMinute(minute: Int): LocalDateTime = atStartOfDay().plusMinutes(minute.toLong())

internal fun LocalTime.minuteOfDay(): Int = hour * 60 + minute
