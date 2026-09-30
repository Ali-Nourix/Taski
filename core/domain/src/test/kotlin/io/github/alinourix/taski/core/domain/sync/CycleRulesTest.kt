package io.github.alinourix.taski.core.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleRulesTest {
    private val node = "cccccccccccccccc"
    private fun edge(id: String, from: String, to: String, rev: Long) = Edge(id, from, to, Hlc(rev, 0, node))

    @Test
    fun anAcyclicGraphIsLeftAlone() {
        val edges = listOf(edge("1", "a", "b", 1), edge("2", "b", "c", 2), edge("3", "a", "c", 3))
        val result = CycleRules.breakCycles(edges)
        assertEquals(edges, result.kept)
        assertTrue(result.dropped.isEmpty())
    }

    @Test
    fun dropsTheNewestEdgeOfACycle() {
        // Device 1 made a the parent of b; device 2 later made b the parent of a.
        val edges = listOf(edge("old", "b", "a", 10), edge("new", "a", "b", 20))
        val result = CycleRules.breakCycles(edges)
        assertEquals(listOf("new"), result.dropped.map { it.id })
        assertEquals(listOf("old"), result.kept.map { it.id })
    }

    @Test
    fun breaksOverlappingCyclesUntilNoneRemain() {
        val edges = listOf(
            edge("ab", "a", "b", 1), edge("bc", "b", "c", 2), edge("ca", "c", "a", 5),
            edge("cb", "c", "b", 7), edge("self", "d", "d", 3),
        )
        val result = CycleRules.breakCycles(edges)
        assertEquals(setOf("cb", "ca", "self"), result.dropped.map { it.id }.toSet())
        assertNull(CycleRules.findCycle(result.kept))
    }

    @Test
    fun refusesALocalEditThatWouldCloseALoop() {
        val edges = listOf(edge("1", "a", "b", 1), edge("2", "b", "c", 2))
        assertTrue(CycleRules.wouldCreateCycle(edges, edge("x", "c", "a", 3)))
        assertTrue(CycleRules.wouldCreateCycle(edges, edge("x", "a", "a", 3)))
        assertFalse(CycleRules.wouldCreateCycle(edges, edge("x", "a", "c", 3)))
    }

    @Test
    fun handlesLongChainsWithoutRecursion() {
        val chain = (0 until 20_000).map { edge("e$it", "n$it", "n${it + 1}", it.toLong()) }
        assertNull(CycleRules.findCycle(chain))
        val closed = chain + edge("back", "n20000", "n0", 99_999)
        assertEquals(listOf("back"), CycleRules.breakCycles(closed).dropped.map { it.id })
    }

    @Test
    fun orphanedTasksMoveToTheInbox() {
        val moved = OrphanRules.tasksToMoveToInbox(
            mapOf("t1" to "p1", "t2" to "gone", "t3" to null),
            liveProjectIds = setOf("p1"),
        )
        assertEquals(setOf("t2"), moved)
    }
}
