package io.github.alinourix.taski.core.domain.time

import java.time.LocalDate

/** A date in the Solar Hijri (Jalali) calendar. Months are 1-based. */
data class JalaliDate(val year: Int, val month: Int, val day: Int)

/**
 * Gregorian ⇄ Jalali conversion, the same arithmetic the Obsidian plugin uses
 * (the 33-year cycle approximation, exact for 1178–1633 SH), so a date picked
 * on the phone reads the same as in the vault.
 */
object JalaliCalendar {
    private val gregorianDaysBeforeMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromGregorian(date: LocalDate): JalaliDate {
        var gy = date.year
        val gm = date.monthValue
        val gd = date.dayOfMonth
        var jy = if (gy <= 1600) 0 else 979
        gy -= if (gy <= 1600) 621 else 1600
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd +
            gregorianDaysBeforeMonth[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return JalaliDate(jy, jm, jd)
    }

    fun toGregorian(date: JalaliDate): LocalDate {
        var jy = date.year
        val jm = date.month
        val jd = date.day
        var gy = if (jy <= 979) 621 else 1600
        jy -= if (jy <= 979) 0 else 979
        var days = 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + 78 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        gy += 400 * (days / 146097)
        days %= 146097
        var leap = true
        if (days >= 36525) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++ else leap = false
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days >= 366) {
            leap = false
            days--
            gy += days / 365
            days %= 365
        }
        val monthLengths = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && days >= monthLengths[gm]) {
            days -= monthLengths[gm]
            gm++
        }
        return LocalDate.of(gy, gm, days + 1)
    }

    /** Days in a Jalali month, derived from the conversion so it can never disagree with it. */
    fun monthLength(year: Int, month: Int): Int {
        if (month <= 6) return 31
        if (month <= 11) return 30
        val firstOfNext = toGregorian(JalaliDate(year + 1, 1, 1))
        val firstOfEsfand = toGregorian(JalaliDate(year, 12, 1))
        return (firstOfNext.toEpochDay() - firstOfEsfand.toEpochDay()).toInt()
    }
}
