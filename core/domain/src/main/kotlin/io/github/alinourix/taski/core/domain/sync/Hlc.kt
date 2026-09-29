package io.github.alinourix.taski.core.domain.sync

/**
 * A hybrid logical clock timestamp: wall-clock millis, a counter for events in
 * the same millisecond (or while the wall clock lags behind a timestamp already
 * seen), and the id of the device that made it.
 *
 * The encoded form is fixed width, so comparing two encoded strings gives the
 * same total order as comparing the timestamps: millis first, then counter,
 * then device id as the tie-break no two devices share.
 */
data class Hlc(val millis: Long, val counter: Int, val node: String) : Comparable<Hlc> {
    init {
        require(millis in 0..MAX_MILLIS) { "millis out of range: $millis" }
        require(counter in 0..MAX_COUNTER) { "counter out of range: $counter" }
        require(NODE_PATTERN.matches(node)) { "node must be $NODE_LENGTH lowercase hex chars: $node" }
    }

    fun encode(): String = "%015d-%05d-%s".format(millis, counter, node)

    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, Hlc::millis, Hlc::counter, Hlc::node)

    override fun toString(): String = encode()

    companion object {
        const val NODE_LENGTH = 16
        const val MAX_COUNTER = 99_999
        const val MAX_MILLIS = 999_999_999_999_999L
        private val NODE_PATTERN = Regex("[0-9a-f]{$NODE_LENGTH}")
        private val ENCODED = Regex("""(\d{15})-(\d{5})-([0-9a-f]{$NODE_LENGTH})""")

        /** The earliest possible timestamp, older than anything a device can produce. */
        val ZERO = Hlc(0, 0, "0".repeat(NODE_LENGTH))

        fun parse(value: String): Hlc {
            val match = requireNotNull(ENCODED.matchEntire(value)) { "not an HLC: $value" }
            val (millis, counter, node) = match.destructured
            return Hlc(millis.toLong(), counter.toInt(), node)
        }

        fun parseOrNull(value: String?): Hlc? = value?.let { runCatching { parse(it) }.getOrNull() }
    }
}

fun laterOf(a: Hlc?, b: Hlc?): Hlc? = when {
    a == null -> b
    b == null -> a
    else -> if (a >= b) a else b
}
