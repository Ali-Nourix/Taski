package io.github.alinourix.taski.core.domain.id

import io.github.alinourix.taski.core.domain.time.Clock
import java.security.SecureRandom
import java.util.Random
import java.util.UUID

/** Source of primary keys. Injected everywhere a row is created. */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * RFC 9562 UUIDv7: 48 bits of Unix millis, then a 12-bit sequence (monotonic
 * within one millisecond on this device), then 62 random bits. Keys sort by
 * creation time and map 1:1 onto a Postgres `uuid`.
 */
class UuidV7Generator(
    private val clock: Clock,
    private val random: Random = SecureRandom(),
) : IdGenerator {
    private var lastMillis = -1L
    private var sequence = 0

    @Synchronized
    override fun newId(): String {
        val now = clock.nowMillis()
        if (now > lastMillis) {
            lastMillis = now
            // Start low in the range so a burst in one millisecond has room to count up.
            sequence = random.nextInt(0x400)
        } else {
            sequence++
            if (sequence > 0xFFF) {
                // Borrow the next millisecond rather than repeat or go backwards.
                lastMillis++
                sequence = 0
            }
        }
        val msb = (lastMillis shl 16) or (0x7L shl 12) or sequence.toLong()
        val lsb = (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }

    companion object {
        /** Unix millis encoded in a UUIDv7 string. */
        fun timestampOf(id: String): Long = UUID.fromString(id).mostSignificantBits ushr 16
    }
}
