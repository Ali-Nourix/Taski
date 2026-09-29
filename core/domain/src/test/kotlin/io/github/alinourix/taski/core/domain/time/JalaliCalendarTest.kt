package io.github.alinourix.taski.core.domain.time

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class JalaliCalendarTest {
    @Test
    fun knownNewYears() {
        assertEquals(JalaliDate(1403, 1, 1), JalaliCalendar.fromGregorian(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1404, 1, 1), JalaliCalendar.fromGregorian(LocalDate.of(2025, 3, 21)))
        assertEquals(LocalDate.of(2025, 3, 21), JalaliCalendar.toGregorian(JalaliDate(1404, 1, 1)))
    }

    @Test
    fun roundTripsEveryDayForDecades() {
        var date = LocalDate.of(2000, 1, 1)
        while (date.year < 2060) {
            assertEquals(date, JalaliCalendar.toGregorian(JalaliCalendar.fromGregorian(date)))
            date = date.plusDays(1)
        }
    }

    @Test
    fun monthLengths() {
        assertEquals(31, JalaliCalendar.monthLength(1404, 1))
        assertEquals(30, JalaliCalendar.monthLength(1404, 7))
        assertEquals(30, JalaliCalendar.monthLength(1403, 12)) // 1403 is a leap year
        assertEquals(29, JalaliCalendar.monthLength(1404, 12))
    }
}
