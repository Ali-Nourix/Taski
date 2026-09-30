package io.github.alinourix.taski.core.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskScheduleTest {
    private val mon = LocalDate.of(2026, 9, 28)
    private val tue = mon.plusDays(1)
    private val thu = mon.plusDays(3)

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun aTaskWithNoDatesHasNoSpan() {
        assertNull(TaskSchedule.spanOf(Schedule()))
    }

    @Test
    fun aDateOnlyRangeRunsThroughTheEndOfItsLastDay() {
        val span = TaskSchedule.spanOf(Schedule(startDate = mon, dueDate = thu))!!
        assertFalse(span.timed)
        assertEquals(mon.atStartOfDay(), span.start)
        assertEquals(thu.plusDays(1).atStartOfDay(), span.end)
        assertEquals(thu, span.lastDay)
        assertEquals(4, span.dayCount)
        assertTrue(span.coversDay(tue))
        assertFalse(span.coversDay(thu.plusDays(1)))
    }

    @Test
    fun aTimedRangeEndsAtItsDueTime() {
        val span = TaskSchedule.spanOf(Schedule(mon, t(9), mon, t(10, 30)))!!
        assertTrue(span.timed)
        assertEquals(LocalDateTime.of(2026, 9, 28, 10, 30), span.end)
        assertEquals(mon, span.lastDay)
    }

    @Test
    fun aStartWithoutADueTakesAnHourOrADay() {
        val timed = TaskSchedule.spanOf(Schedule(startDate = mon, startTime = t(14)))!!
        assertEquals(LocalDateTime.of(2026, 9, 28, 15, 0), timed.end)
        val dated = TaskSchedule.spanOf(Schedule(startDate = mon))!!
        assertEquals(mon, dated.lastDay)
        assertFalse(dated.timed)
    }

    @Test
    fun aDueWithoutAStartIsADeadlineMarker() {
        val marker = TaskSchedule.spanOf(Schedule(dueDate = tue, dueTime = t(14, 30)))!!
        assertEquals(SpanKind.Deadline, marker.kind)
        assertEquals(marker.start, marker.end)
        assertEquals(tue, marker.lastDay)
        val allDay = TaskSchedule.spanOf(Schedule(dueDate = tue))!!
        assertFalse(allDay.timed)
        assertEquals(tue, allDay.firstDay)
        assertEquals(tue, allDay.lastDay)
    }

    @Test
    fun aTimeNeedsItsDate() {
        val fixed = TaskSchedule.coherent(Schedule(startTime = t(9), dueTime = t(10)), Anchor.Start)
        assertNull(fixed.startTime)
        assertNull(fixed.dueTime)
    }

    @Test
    fun aStartMovedPastTheDueDragsTheDueAlong() {
        val result = TaskSchedule.coherent(Schedule(startDate = thu, dueDate = tue), Anchor.Start)
        assertEquals(thu, result.startDate)
        assertEquals(thu, result.dueDate)
    }

    @Test
    fun aDueMovedBeforeTheStartDragsTheStartAlong() {
        val result = TaskSchedule.coherent(Schedule(startDate = thu, dueDate = tue), Anchor.Due)
        assertEquals(tue, result.startDate)
        assertEquals(tue, result.dueDate)
    }

    @Test
    fun onOneDayTheEndNeverPrecedesTheStart() {
        val late = TaskSchedule.coherent(Schedule(mon, t(15), mon, t(9)), Anchor.Start)
        assertEquals(t(15), late.startTime)
        assertEquals(t(15), late.dueTime)
        val early = TaskSchedule.coherent(Schedule(mon, t(15), mon, t(9)), Anchor.Due)
        assertEquals(t(9), early.startTime)
        assertEquals(t(9), early.dueTime)
    }

    @Test
    fun aConsistentScheduleIsLeftAlone() {
        val schedule = Schedule(mon, t(9), thu, t(17))
        assertEquals(schedule, TaskSchedule.coherent(schedule, Anchor.Start))
        assertEquals(schedule, TaskSchedule.coherent(schedule, Anchor.Due))
    }

    @Test
    fun shiftingKeepsTimesAndLength() {
        val moved = TaskSchedule.shifted(Schedule(mon, t(9), thu, t(17)), 7)
        assertEquals(mon.plusDays(7), moved.startDate)
        assertEquals(thu.plusDays(7), moved.dueDate)
        assertEquals(t(9), moved.startTime)
        assertEquals(t(17), moved.dueTime)
        assertNull(TaskSchedule.shifted(Schedule(dueDate = mon), 2).startDate)
    }

    @Test
    fun movingABlockKeepsItsLengthAndCanCrossMidnight() {
        val moved = TaskSchedule.movedTo(Schedule(mon, t(9), mon, t(10, 30)), LocalDateTime.of(2026, 9, 28, 23, 0))
        assertEquals(t(23), moved.startTime)
        assertEquals(tue, moved.dueDate)
        assertEquals(t(0, 30), moved.dueTime)
    }

    @Test
    fun aBlockDroppedOnADayTakesAnHour() {
        val moved = TaskSchedule.movedTo(Schedule(dueDate = mon), LocalDateTime.of(2026, 9, 29, 8, 0))
        assertEquals(Schedule(tue, t(8), tue, t(9)), moved)
    }

    @Test
    fun draggingABlockMovesBothEndsAndADeadlineItsOneMoment() {
        val moved = TaskSchedule.movedByMinutes(Schedule(mon, t(9), mon, t(10)), 90)!!
        assertEquals(Schedule(mon, t(10, 30), mon, t(11, 30)), moved)
        val deadline = TaskSchedule.movedByMinutes(Schedule(dueDate = mon, dueTime = t(23, 30)), 60)!!
        assertEquals(Schedule(null, null, tue, t(0, 30)), deadline)
    }

    @Test
    fun aTaskWithoutATimeCannotBeDraggedByMinutes() {
        assertNull(TaskSchedule.movedByMinutes(Schedule(startDate = mon, dueDate = thu), 30))
        assertNull(TaskSchedule.movedByMinutes(Schedule(), 30))
    }

    @Test
    fun resizingNeverInvertsABlock() {
        val base = Schedule(mon, t(9), mon, t(10))
        assertEquals(t(9, 5), TaskSchedule.withEnd(base, LocalDateTime.of(2026, 9, 28, 8, 0)).dueTime)
        assertEquals(t(9, 55), TaskSchedule.withStart(base, LocalDateTime.of(2026, 9, 28, 12, 0)).startTime)
        assertEquals(t(11), TaskSchedule.withEnd(base, LocalDateTime.of(2026, 9, 28, 11, 0)).dueTime)
    }

    @Test
    fun aTimedSpanIsClippedToTheDayAndDrawnAtLeastAHalfHour() {
        val overnight = TaskSchedule.spanOf(Schedule(mon, t(22), tue, t(2)))!!
        assertEquals(22 * 60 until 24 * 60, TaskSchedule.minutesOn(overnight, mon))
        assertEquals(0 until 2 * 60, TaskSchedule.minutesOn(overnight, tue))
        assertNull(TaskSchedule.minutesOn(overnight, thu))

        val brief = TaskSchedule.spanOf(Schedule(mon, t(9), mon, t(9, 5)))!!
        assertEquals(9 * 60 until 9 * 60 + 30, TaskSchedule.minutesOn(brief, mon))
    }

    @Test
    fun aDeadlineIsDrawnAsTheHalfHourBeforeIt() {
        val marker = TaskSchedule.spanOf(Schedule(dueDate = mon, dueTime = t(14, 30)))!!
        assertEquals(14 * 60 until 14 * 60 + 30, TaskSchedule.minutesOn(marker, mon))
    }

    @Test
    fun dateOnlySpansHaveNoMinutes() {
        assertNull(TaskSchedule.minutesOn(TaskSchedule.spanOf(Schedule(startDate = mon, dueDate = thu))!!, tue))
    }

    @Test
    fun daySpansAreClippedToTheVisibleRange() {
        val span = TaskSchedule.spanOf(Schedule(startDate = mon.minusDays(2), dueDate = tue))!!
        assertEquals(0..1, TaskSchedule.dayOffsets(span, mon, 7))
        assertNull(TaskSchedule.dayOffsets(span, thu, 7))
        val later = TaskSchedule.spanOf(Schedule(startDate = mon.plusDays(5), dueDate = mon.plusDays(20)))!!
        assertEquals(5..6, TaskSchedule.dayOffsets(later, mon, 7))
    }

    @Test
    fun barsShareARowOnlyWhenTheyDoNotTouch() {
        // 0..2 and 3..4 fit one row; 1..3 overlaps both and needs its own.
        val rows = TaskSchedule.packRows(listOf(0..2, 1..3, 3..4, 5..6))
        assertEquals(listOf(0, 1, 0, 0), rows)
    }

    @Test
    fun packingKeepsInputOrderAndIsStable() {
        val bars = listOf(4..4, 0..6, 0..0)
        assertEquals(listOf(1, 0, 1), TaskSchedule.packRows(bars))
    }

    @Test
    fun overlappingBlocksSitSideBySide() {
        val cols = TaskSchedule.columns(listOf(60 until 120, 90 until 150, 200 until 260))
        assertEquals(TaskSchedule.Column(0, 2), cols[0])
        assertEquals(TaskSchedule.Column(1, 2), cols[1])
        assertEquals(TaskSchedule.Column(0, 1), cols[2])
    }

    @Test
    fun blocksThatMerelyTouchDoNotShareAColumn() {
        val cols = TaskSchedule.columns(listOf(60 until 120, 120 until 180))
        assertEquals(listOf(TaskSchedule.Column(0, 1), TaskSchedule.Column(0, 1)), cols)
    }
}
