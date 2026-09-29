package io.github.alinourix.taski.core.ui.format

import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.time.JalaliCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dates in either calendar, in English or Persian, with Persian digits when
 * the app speaks Persian. The month names are the plugin's.
 */
object CalendarText {
    private val gregorianEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val gregorianEnLong = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val gregorianFa = listOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
    private val jalaliEn = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
    )
    private val jalaliFa = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    private val weekdaysEn = mapOf(
        DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed", DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun",
    )
    private val weekdaysEnLong = DayOfWeek.entries.associateWith { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    private val weekdaysFa = mapOf(
        DayOfWeek.SATURDAY to "شنبه", DayOfWeek.SUNDAY to "یکشنبه", DayOfWeek.MONDAY to "دوشنبه", DayOfWeek.TUESDAY to "سه‌شنبه",
        DayOfWeek.WEDNESDAY to "چهارشنبه", DayOfWeek.THURSDAY to "پنجشنبه", DayOfWeek.FRIDAY to "جمعه",
    )
    private val weekdaysFaShort = mapOf(
        DayOfWeek.SATURDAY to "ش", DayOfWeek.SUNDAY to "ی", DayOfWeek.MONDAY to "د", DayOfWeek.TUESDAY to "س",
        DayOfWeek.WEDNESDAY to "چ", DayOfWeek.THURSDAY to "پ", DayOfWeek.FRIDAY to "ج",
    )

    /** The first day of the week: Saturday in the Jalali calendar and in Persian, Monday otherwise. */
    fun firstDayOfWeek(calendar: CalendarSystem, persian: Boolean): DayOfWeek =
        if (calendar == CalendarSystem.Jalali || persian) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

    fun weekdayShort(day: DayOfWeek, persian: Boolean): String = if (persian) weekdaysFaShort.getValue(day) else weekdaysEn.getValue(day).take(2)

    fun weekdayLong(day: DayOfWeek, persian: Boolean): String = if (persian) weekdaysFa.getValue(day) else weekdaysEnLong.getValue(day)

    fun monthName(calendar: CalendarSystem, month: Int, persian: Boolean, long: Boolean = false): String = when (calendar) {
        CalendarSystem.Jalali -> if (persian) jalaliFa[month - 1] else jalaliEn[month - 1]
        CalendarSystem.Gregorian -> if (persian) gregorianFa[month - 1] else if (long) gregorianEnLong[month - 1] else gregorianEn[month - 1]
    }

    /** Year and month of [date] in [calendar]. */
    fun yearMonth(date: LocalDate, calendar: CalendarSystem): Pair<Int, Int> = when (calendar) {
        CalendarSystem.Jalali -> JalaliCalendar.fromGregorian(date).let { it.year to it.month }
        CalendarSystem.Gregorian -> date.year to date.monthValue
    }

    fun dayOfMonth(date: LocalDate, calendar: CalendarSystem): Int = when (calendar) {
        CalendarSystem.Jalali -> JalaliCalendar.fromGregorian(date).day
        CalendarSystem.Gregorian -> date.dayOfMonth
    }

    /** `29 Sep`, `7 Mehr`, `۷ مهر`; the year is added when it is not [thisYearOf]'s. */
    fun date(date: LocalDate, calendar: CalendarSystem, persian: Boolean, thisYearOf: LocalDate? = null): String {
        val (year, month) = yearMonth(date, calendar)
        val day = dayOfMonth(date, calendar)
        val showYear = thisYearOf == null || yearMonth(thisYearOf, calendar).first != year
        val name = monthName(calendar, month, persian)
        val text = when {
            persian || calendar == CalendarSystem.Jalali -> if (showYear) "$day $name $year" else "$day $name"
            else -> if (showYear) "$name $day, $year" else "$name $day"
        }
        return text.localizeDigits(persian)
    }

    fun monthTitle(year: Int, month: Int, calendar: CalendarSystem, persian: Boolean): String =
        "${monthName(calendar, month, persian, long = true)} $year".localizeDigits(persian)

    fun time(time: LocalTime, persian: Boolean): String = "%02d:%02d".format(time.hour, time.minute).localizeDigits(persian)
}

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Western digits to Persian ones when [persian] is set. */
fun String.localizeDigits(persian: Boolean): String =
    if (!persian) this else map { if (it in '0'..'9') PERSIAN_DIGITS[it - '0'] else it }.joinToString("")

fun Int.localized(persian: Boolean): String = toString().localizeDigits(persian)
