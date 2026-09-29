package io.github.alinourix.taski.core.domain.recurrence

import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import java.time.LocalDate

object Recurrence {
    /**
     * The next due date of a repeating task, strictly after [today].
     *
     * Counting on from the old due date keeps a weekly task on its weekday even
     * when it is ticked off late. A due date already in the past is advanced
     * until it lands in the future, so clearing a month of missed dailies leaves
     * one task due tomorrow, not one due last week. Month steps clamp: a task due
     * on the 31st repeats on the 30th in a 30-day month.
     */
    fun nextDueDate(rule: RepeatRule, from: LocalDate?, today: LocalDate): LocalDate {
        var cursor = from ?: today
        do {
            cursor = advance(cursor, rule)
        } while (!cursor.isAfter(today))
        return cursor
    }

    private fun advance(date: LocalDate, rule: RepeatRule): LocalDate = when (rule.unit) {
        RepeatUnit.Day -> date.plusDays(rule.every.toLong())
        RepeatUnit.Week -> date.plusWeeks(rule.every.toLong())
        RepeatUnit.Month -> date.plusMonths(rule.every.toLong())
    }
}
