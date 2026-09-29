package io.github.alinourix.taski.core.domain.order

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FractionalIndexTest {
    @Test
    fun matchesTheReferenceImplementation() {
        assertEquals("a0", FractionalIndex.between(null, null))
        assertEquals("a1", FractionalIndex.between("a0", null))
        assertEquals("a0V", FractionalIndex.between("a0", "a1"))
        assertEquals("Zz", FractionalIndex.between(null, "a0"))
        assertEquals("b00", FractionalIndex.between("az", null))
        assertEquals("a1V", FractionalIndex.between("a1", "a2"))
        assertEquals("a0l", FractionalIndex.between("a0V", "a1"))
        assertEquals("a0G", FractionalIndex.between("a0", "a0V"))
        assertEquals("ZzV", FractionalIndex.between("Zz", "a0"))
        assertEquals("a1", FractionalIndex.between("a0", "a1V"))
        assertEquals("b99", FractionalIndex.between(null, "b999"))
        assertEquals("b127", FractionalIndex.between("b125", "b129"))
        assertEquals("Xzzz", FractionalIndex.between(null, "Y00"))
        assertEquals("c000", FractionalIndex.between("bzz", null))
    }

    @Test
    fun rejectsBadInput() {
        assertFailsWith<IllegalArgumentException> { FractionalIndex.between("a1", "a0") }
        assertFailsWith<IllegalArgumentException> { FractionalIndex.between("a00", null) }
        assertFailsWith<IllegalArgumentException> { FractionalIndex.between("!", null) }
    }

    @Test
    fun randomInsertionsKeepAStrictOrder() {
        val random = Random(7)
        val keys = mutableListOf<String>()
        repeat(2_000) {
            val index = random.nextInt(keys.size + 1)
            val key = FractionalIndex.between(keys.getOrNull(index - 1), keys.getOrNull(index))
            keys.add(index, key)
        }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all(FractionalIndex::isValid))
    }

    @Test
    fun repeatedFrontAndBackInsertsStayShort() {
        var first = FractionalIndex.between(null, null)
        var last = first
        repeat(1_000) {
            first = FractionalIndex.between(null, first)
            last = FractionalIndex.between(last, null)
        }
        assertTrue(first.length < 6 && last.length < 6, "$first / $last")
    }

    @Test
    fun spreadsSeveralKeysBetweenNeighbours() {
        val keys = FractionalIndex.nBetween("a0", "a1", 10)
        assertEquals(10, keys.size)
        assertEquals(keys.sorted(), keys)
        assertTrue(keys.first() > "a0" && keys.last() < "a1")
        val fresh = FractionalIndex.nBetween(null, null, 5)
        assertEquals(fresh.sorted(), fresh)
    }
}

class OrderPlannerTest {
    @Test
    fun aNormalInsertTouchesOnlyTheItem() {
        val placement = OrderPlanner.insertAt(listOf("a0", "a1"), 1)
        assertEquals("a0V", placement.key)
        assertTrue(placement.rekeyed.isEmpty())
    }

    @Test
    fun tiedNeighboursAreSpreadApart() {
        val keys = listOf("a0", "a1", "a1", "a2")
        val placement = OrderPlanner.insertAt(keys, 2)
        val result = keys.toMutableList().apply {
            placement.rekeyed.forEach { (i, key) -> this[i] = key }
            add(2, placement.key)
        }
        assertEquals(result.sorted(), result)
        assertEquals(result.size, result.toSet().size)
        assertEquals(setOf(2), placement.rekeyed.keys)
    }

    @Test
    fun malformedKeysAreRepaired() {
        val keys = listOf("a0", "not a key", "a2")
        val placement = OrderPlanner.insertAt(keys, 3)
        val result = keys.indices.map { placement.rekeyed[it] ?: keys[it] } + placement.key
        assertEquals(result.sorted(), result)
        assertTrue(result.all(FractionalIndex::isValid))
    }
}
