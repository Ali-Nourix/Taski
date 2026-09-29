package io.github.alinourix.taski.core.domain.order

/** Where a moved or new item goes, and any neighbours that had to be re-keyed to make room. */
data class Placement(val key: String, val rekeyed: Map<Int, String> = emptyMap())

object OrderPlanner {
    /**
     * A key for an item inserted at [index] into [keys] (already in order).
     * Normally only the item itself gets a key. When the neighbours tie —
     * two devices inserted into the same gap — or a key is malformed, the
     * smallest run of neighbours that blocks the insert is re-keyed too.
     */
    fun insertAt(keys: List<String>, index: Int): Placement {
        require(index in 0..keys.size) { "index $index out of 0..${keys.size}" }
        val prev = keys.getOrNull(index - 1)
        val next = keys.getOrNull(index)
        // A key that cannot be read (from a buggy or future client) would sort unpredictably, so any one of them triggers a relayout.
        val clean = keys.all(FractionalIndex::isValid)
        if (clean && (prev == null || next == null || prev < next)) {
            return Placement(FractionalIndex.between(prev, next))
        }
        if (clean && prev != null) {
            var end = index
            while (end < keys.size && keys[end] <= prev) end++
            val upper = keys.getOrNull(end)
            val fresh = FractionalIndex.nBetween(prev, upper, 1 + end - index)
            return Placement(fresh[0], (index until end).associateWith { fresh[it - index + 1] })
        }
        // Something unreadable: lay the whole list out again.
        val fresh = FractionalIndex.nBetween(null, null, keys.size + 1)
        val rekeyed = keys.indices.associateWith { i -> fresh[if (i < index) i else i + 1] }
        return Placement(fresh[index], rekeyed)
    }
}
