package io.github.alinourix.taski.core.domain.schedule

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulePlanTest {
    // A week, Monday the 28th to Sunday the 4th.
    private val mon = LocalDate.of(2026, 9, 28)
    private val wed = mon.plusDays(2)

    private fun span(startDate: LocalDate?, dueDate: LocalDate?, start: LocalTime? = null, due: LocalTime? = null) =
        TaskSchedule.spanOf(Schedule(startDate, start, dueDate, due))!!

    private fun build(vararg spans: Pair<String, Span>, today: LocalDate = wed) = SchedulePlan.build(spans.toList(), mon, 7, today)

    private fun List<PlanRow>.shape() = map {
        when (it) {
            is PlanRow.Day -> "D${it.date.dayOfMonth}"
            is PlanRow.Quiet -> if (it.from == it.to) "Q${it.from.dayOfMonth}" else "Q${it.from.dayOfMonth}-${it.to.dayOfMonth}"
        }
    }

    @Test
    fun anEmptyWeekIsTodayAndTheDaysAroundIt() {
        assertEquals(listOf("Q28-29", "D30", "Q1-4"), build().shape())
    }

    @Test
    fun onlyDaysWithSomethingAreShownAndTheRestFoldAway() {
        val rows = build("a" to span(null, mon.plusDays(5)), "b" to span(mon, mon))
        assertEquals(listOf("D28", "Q29", "D30", "Q1-2", "D3", "Q4"), rows.shape())
    }

    @Test
    fun aSeveralDayTaskShowsOnItsFirstAndLastDayAndPassesThroughBetween() {
        val rows = build("run" to span(mon.plusDays(1), mon.plusDays(4)), today = mon)
        // The 29th starts it, the 2nd ends it; the 30th and the 1st are quiet but carry its rail.
        assertEquals(listOf("D28", "D29", "Q30-1", "D2", "Q3-4"), rows.shape())
        val start = (rows[1] as PlanRow.Day).slots.single()
        assertEquals(SlotKind.Start, start.kind)
        assertEquals(1, start.dayNumber)
        val quiet = rows[2] as PlanRow.Quiet
        assertEquals(2, quiet.days)
        assertEquals(listOf("run"), quiet.rails.map { it.id })
        val end = (rows[3] as PlanRow.Day).slots.single()
        assertEquals(SlotKind.End, end.kind)
        assertEquals(4, end.dayNumber)
        assertTrue(rows[3].rails.single().ends)
    }

    @Test
    fun todayShowsWhatIsPassingThroughItAndOtherMiddleDaysDoNot() {
        val rows = build("run" to span(mon, mon.plusDays(5)))
        val today = rows.filterIsInstance<PlanRow.Day>().single { it.date == wed }
        assertEquals(SlotKind.Continue, today.slots.single().kind)
        assertEquals(3, today.slots.single().dayNumber)
    }

    @Test
    fun theFirstDayInViewShowsATaskThatBeganBeforeIt() {
        val rows = build("run" to span(mon.minusDays(3), mon.plusDays(4)), today = mon.plusDays(6))
        val first = rows.first() as PlanRow.Day
        assertEquals(mon, first.date)
        assertEquals(SlotKind.Continue, first.slots.single().kind)
        assertEquals(4, first.slots.single().dayNumber)
    }

    @Test
    fun aTaskThatEndsBeforeTheWeekIsNotShown() {
        val rows = build("old" to span(mon.minusDays(5), mon.minusDays(1)))
        assertEquals(listOf("Q28-29", "D30", "Q1-4"), rows.shape())
    }

    @Test
    fun railsOfOverlappingTasksTakeOwnLanes() {
        val rows = build("a" to span(mon, mon.plusDays(3)), "b" to span(mon.plusDays(1), mon.plusDays(5)), today = mon.plusDays(6))
        val lanes = rows.flatMap { it.rails }.groupBy({ it.id }, { it.lane }).mapValues { it.value.distinct() }
        assertEquals(listOf(0), lanes["a"])
        assertEquals(listOf(1), lanes["b"])
    }

    @Test
    fun aDayIsOrderedByTimeWithAllDayAndPassingTasksLast() {
        val rows = build(
            "late" to span(wed, wed, LocalTime.of(15, 0), LocalTime.of(16, 0)),
            "early" to span(wed, wed, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            "date" to span(wed, wed),
            "run" to span(mon, mon.plusDays(5)),
        )
        val day = rows.filterIsInstance<PlanRow.Day>().single { it.date == wed }
        assertEquals(listOf("early", "late", "date", "run"), day.slots.map { it.id })
    }

    @Test
    fun aDeadlineIsASingleDaySlot() {
        val rows = build("due" to span(null, mon.plusDays(3)))
        val slot = rows.filterIsInstance<PlanRow.Day>().single { it.date == mon.plusDays(3) }.slots.single()
        assertEquals(SlotKind.Single, slot.kind)
        assertEquals(SpanKind.Deadline, slot.span.kind)
    }
}
