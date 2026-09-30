package io.github.alinourix.taski.core.domain.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Storage forms shared with the server: dates are ISO `YYYY-MM-DD`, times of
 * day `HH:MM`, instants UTC epoch millis. Parsing is lenient about garbage
 * (it returns null) because a row may one day arrive from another client.
 */
object DateCodes {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun date(value: LocalDate): String = value.toString()

    fun time(value: LocalTime): String = value.format(TIME)

    fun parseDate(value: String?): LocalDate? = value?.let {
        try {
            LocalDate.parse(it.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseTime(value: String?): LocalTime? = value?.let {
        try {
            LocalTime.parse(it.trim(), TIME)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
