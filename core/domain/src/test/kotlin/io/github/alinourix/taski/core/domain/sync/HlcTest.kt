package io.github.alinourix.taski.core.domain.sync

import io.github.alinourix.taski.core.domain.time.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HlcTest {
    private val nodeA = "aaaaaaaaaaaaaaaa"
    private val nodeB = "bbbbbbbbbbbbbbbb"

    @Test
    fun encodedOrderMatchesTimestampOrder() {
        val stamps = listOf(
            Hlc(5, 0, nodeB), Hlc(1_000, 2, nodeA), Hlc(1_000, 10, nodeA), Hlc(1_000, 10, nodeB), Hlc(99_999, 0, nodeA),
        )
        assertEquals(stamps.sorted(), stamps.shuffled().sortedBy { it.encode() }.map { Hlc.parse(it.encode()) })
    }

    @Test
    fun roundTripsThroughText() {
        val hlc = Hlc(1_727_000_000_123, 42, nodeA)
        assertEquals("001727000000123-00042-$nodeA", hlc.encode())
        assertEquals(hlc, Hlc.parse(hlc.encode()))
    }

    @Test
    fun rejectsMalformedNodes() {
        assertFailsWith<IllegalArgumentException> { Hlc(1, 0, "short") }
        assertEquals(null, Hlc.parseOrNull("not a clock"))
    }

    @Test
    fun keepsIncreasingWhenTheWallClockGoesBack() {
        val clock = FixedClock(10_000)
        val hlc = DefaultHlcClock(clock, nodeA)
        val first = hlc.now()
        clock.set(5_000)
        val second = hlc.now()
        val third = hlc.now()
        assertTrue(first < second && second < third)
        assertEquals(10_000, third.millis)
        assertEquals(2, third.counter)
    }

    @Test
    fun resetsTheCounterWhenTimeMovesOn() {
        val clock = FixedClock(10_000)
        val hlc = DefaultHlcClock(clock, nodeA)
        hlc.now()
        hlc.now()
        clock.set(10_001)
        assertEquals(Hlc(10_001, 0, nodeA), hlc.now())
    }

    @Test
    fun ordersAfterARemoteStampFromAClockThatRunsAhead() {
        val clock = FixedClock(1_000)
        val hlc = DefaultHlcClock(clock, nodeA)
        val remote = Hlc(50_000, 7, nodeB)
        val received = hlc.receive(remote)
        assertTrue(received > remote)
        val next = hlc.now()
        assertTrue(next > received)
        assertEquals(50_000, next.millis)
    }

    @Test
    fun counterOverflowBorrowsTheNextMillisecond() {
        val clock = FixedClock(1_000)
        val hlc = DefaultHlcClock(clock, nodeA, initial = Hlc(1_000, Hlc.MAX_COUNTER, nodeA))
        assertEquals(Hlc(1_001, 0, nodeA), hlc.now())
    }

    @Test
    fun fieldRevsRoundTripAndIgnoreJunk() {
        val revs = FieldRevs().stamp(listOf("title", "status"), Hlc(3, 1, nodeA))
        assertEquals(revs, FieldRevs.decode(revs.encode()))
        assertEquals(FieldRevs(), FieldRevs.decode("{broken"))
        assertEquals(setOf("ok"), FieldRevs.decode("""{"ok":"${Hlc(1, 0, nodeA)}","bad":"x"}""").revs.keys)
    }
}
