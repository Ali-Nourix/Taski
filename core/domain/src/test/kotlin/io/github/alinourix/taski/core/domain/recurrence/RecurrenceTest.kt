package io.github.alinourix.taski.core.domain.recurrence

import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The plugin's own cases from test/recurrence.mjs, so both apps roll a task forward the same way. */
class RecurrenceTest {
    private fun d(value: String) = LocalDate.parse(value)
    private val weekly = RepeatRule(1, RepeatUnit.Week)

    @Test
    fun weeklyCountsFromTheDeadline() =
        assertEquals(d("2026-09-02"), Recurrence.nextDueDate(weekly, d("2026-08-26"), d("2026-08-28")))

    @Test
    fun aStaleDeadlineCatchesUp() =
        assertEquals(d("2026-08-29"), Recurrence.nextDueDate(RepeatRule(1, RepeatUnit.Day), d("2026-07-01"), d("2026-08-28")))

    @Test
    fun withoutADeadlineTodayIsTheStart() {
        assertEquals(d("2026-09-04"), Recurrence.nextDueDate(weekly, null, d("2026-08-28")))
        assertEquals(d("2026-09-11"), Recurrence.nextDueDate(RepeatRule(2, RepeatUnit.Week), null, d("2026-08-28")))
    }

    @Test
    fun monthStepsClampInsteadOfSpilling() {
        val monthly = RepeatRule(1, RepeatUnit.Month)
        assertEquals(d("2026-02-28"), Recurrence.nextDueDate(monthly, d("2026-01-31"), d("2026-01-31")))
        assertEquals(d("2026-04-15"), Recurrence.nextDueDate(monthly, d("2026-03-15"), d("2026-03-15")))
    }

    @Test
    fun rulesAreVersionedJson() {
        val rule = RepeatRule(2, RepeatUnit.Week)
        assertEquals("""{"v":1,"every":2,"unit":"week"}""", rule.encode())
        assertEquals(rule, RepeatRule.decode(rule.encode()))
        assertNull(RepeatRule.decode("""{"v":2,"rrule":"FREQ=WEEKLY;BYDAY=MO"}"""), "a newer version is not guessed at")
        assertNull(RepeatRule.decode("""{"v":1,"every":0,"unit":"day"}"""))
        assertNull(RepeatRule.decode("garbage"))
    }
}
