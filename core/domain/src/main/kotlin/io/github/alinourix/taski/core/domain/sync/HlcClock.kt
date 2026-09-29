package io.github.alinourix.taski.core.domain.sync

import io.github.alinourix.taski.core.domain.time.Clock

/** Issues revisions for local changes and absorbs revisions seen from elsewhere. */
interface HlcClock {
    /** A timestamp for a change made on this device, greater than every one issued or seen before. */
    fun now(): Hlc

    /**
     * Advances past a timestamp received from another device, so the next local
     * change orders after it even if this device's wall clock is behind.
     */
    fun receive(remote: Hlc): Hlc
}

/**
 * Kulkarni et al.'s hybrid logical clock. The physical part never goes
 * backwards, so a device whose clock was set back still produces increasing
 * revisions, and one whose clock runs ahead cannot make everyone else's edits
 * lose forever: the counter keeps them ordered once they have seen its stamp.
 */
class DefaultHlcClock(
    private val clock: Clock,
    private val node: String,
    initial: Hlc? = null,
) : HlcClock {
    private var last: Hlc = initial ?: Hlc.ZERO.copy(node = node)

    @Synchronized
    override fun now(): Hlc {
        val physical = clock.nowMillis()
        last = if (physical > last.millis) {
            Hlc(physical, 0, node)
        } else {
            bump(last.millis, last.counter + 1)
        }
        return last
    }

    @Synchronized
    override fun receive(remote: Hlc): Hlc {
        val physical = clock.nowMillis()
        val millis = maxOf(physical, last.millis, remote.millis)
        val counter = when {
            millis == last.millis && millis == remote.millis -> maxOf(last.counter, remote.counter) + 1
            millis == last.millis -> last.counter + 1
            millis == remote.millis -> remote.counter + 1
            else -> 0
        }
        last = bump(millis, counter)
        return last
    }

    /** A counter that would overflow rolls into the next logical millisecond. */
    private fun bump(millis: Long, counter: Int): Hlc =
        if (counter > Hlc.MAX_COUNTER) Hlc(millis + 1, 0, node) else Hlc(millis, counter, node)
}
