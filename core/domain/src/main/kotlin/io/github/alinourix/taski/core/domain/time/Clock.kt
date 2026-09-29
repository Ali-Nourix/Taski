package io.github.alinourix.taski.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only source of "now" in the app. Nothing reads the system clock directly,
 * so tests can pin time and a future sync engine can reason about it.
 */
interface Clock {
    fun nowMillis(): Long
    fun zone(): ZoneId

    fun now(): Instant = Instant.ofEpochMilli(nowMillis())
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** A clock that only moves when told to. */
class FixedClock(
    private var millis: Long,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock {
    override fun nowMillis(): Long = millis
    override fun zone(): ZoneId = zone

    fun set(value: Long) {
        millis = value
    }

    fun advance(byMillis: Long) {
        millis += byMillis
    }
}
