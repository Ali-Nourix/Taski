package io.github.alinourix.taski.core.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FocusTimerStateTest {
    @Test
    fun countsFromTimestampsAcrossPauses() {
        var timer = FocusTimerState("t", plannedSeconds = 25 * 60).start(now = 0)
        assertEquals(25 * 60L - 60, timer.remainingSeconds(60_000))
        timer = timer.pause(now = 60_000)
        assertEquals(25 * 60L - 60, timer.remainingSeconds(10_000_000), "a paused timer does not move")
        timer = timer.start(now = 100_000)
        assertEquals(100_000 + (25 * 60L - 60) * 1000, timer.endsAt())
        assertTrue(timer.isFinished(timer.endsAt()!!))
        assertEquals(0L, timer.startedAt)
    }

    @Test
    fun formatsLikeThePluginChip() {
        assertEquals("25:00", FocusTimerState.formatClock(1500))
        assertEquals("1:02:03", FocusTimerState.formatClock(3723))
        assertEquals("-0:05", FocusTimerState.formatClock(-5))
    }
}
