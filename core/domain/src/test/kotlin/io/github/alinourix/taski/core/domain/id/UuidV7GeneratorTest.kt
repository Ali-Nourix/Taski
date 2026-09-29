package io.github.alinourix.taski.core.domain.id

import io.github.alinourix.taski.core.domain.time.FixedClock
import java.util.Random
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidV7GeneratorTest {
    @Test
    fun producesVersion7VariantIdsCarryingTheTime() {
        val clock = FixedClock(1_727_600_000_000)
        val id = UuidV7Generator(clock, Random(1)).newId()
        val uuid = UUID.fromString(id)
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        assertEquals(1_727_600_000_000, UuidV7Generator.timestampOf(id))
    }

    @Test
    fun idsSortInCreationOrderEvenWithinOneMillisecond() {
        val clock = FixedClock(1_000_000)
        val generator = UuidV7Generator(clock, Random(3))
        val ids = buildList {
            repeat(10_000) {
                add(generator.newId())
                if (it % 1_000 == 0) clock.advance(1)
            }
        }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun aClockGoingBackDoesNotBreakTheOrder() {
        val clock = FixedClock(5_000_000)
        val generator = UuidV7Generator(clock, Random(5))
        val first = generator.newId()
        clock.set(1_000)
        val second = generator.newId()
        assertTrue(second > first)
    }
}
