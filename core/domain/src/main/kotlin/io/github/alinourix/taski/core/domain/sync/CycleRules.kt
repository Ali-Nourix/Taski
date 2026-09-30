package io.github.alinourix.taski.core.domain.sync

/**
 * A directed edge in a relation that must stay acyclic: a task's parent
 * (`from` = child, `to` = parent) or a dependency (`from` = task, `to` = the
 * task it waits for). [rev] is the revision that created or last set it.
 */
data class Edge(val id: String, val from: String, val to: String, val rev: Hlc)

data class CycleResolution(val kept: List<Edge>, val dropped: List<Edge>)

object CycleRules {
    /**
     * Breaks every cycle a merge produced by dropping, from each cycle, the edge
     * with the newest revision: the last change is the one that closed the loop.
     * Repeats until the graph is acyclic, so overlapping cycles are handled too.
     * The caller records each dropped edge in the activity log.
     */
    fun breakCycles(edges: List<Edge>): CycleResolution {
        val kept = edges.toMutableList()
        val dropped = mutableListOf<Edge>()
        while (true) {
            val cycle = findCycle(kept) ?: break
            val newest = cycle.maxWith(compareBy<Edge> { it.rev }.thenBy { it.id })
            kept.remove(newest)
            dropped += newest
        }
        return CycleResolution(kept, dropped)
    }

    /** True if adding [candidate] to [edges] would close a loop. Used to refuse such edits locally. */
    fun wouldCreateCycle(edges: List<Edge>, candidate: Edge): Boolean {
        if (candidate.from == candidate.to) return true
        val adjacency = edges.groupBy({ it.from }, { it.to })
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(listOf(candidate.to))
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node == candidate.from) return true
            if (seen.add(node)) adjacency[node]?.forEach(stack::addLast)
        }
        return false
    }

    /** The edges of some cycle, or null if the graph has none. Iterative, so deep chains cannot overflow. */
    fun findCycle(edges: List<Edge>): List<Edge>? {
        val outgoing = edges.sortedBy { it.id }.groupBy { it.from }
        val state = mutableMapOf<String, Int>() // 1 = on the current path, 2 = finished
        val nodes = (edges.map { it.from } + edges.map { it.to }).distinct().sorted()

        for (start in nodes) {
            if (state[start] != null) continue
            val path = ArrayList<Edge>()
            val iterators = ArrayDeque<Pair<String, Iterator<Edge>>>()
            state[start] = 1
            iterators.addLast(start to (outgoing[start] ?: emptyList()).iterator())
            while (iterators.isNotEmpty()) {
                val (node, iterator) = iterators.last()
                if (!iterator.hasNext()) {
                    state[node] = 2
                    iterators.removeLast()
                    if (path.isNotEmpty()) path.removeAt(path.lastIndex)
                    continue
                }
                val edge = iterator.next()
                when (state[edge.to]) {
                    1 -> {
                        val startIndex = path.indexOfFirst { it.from == edge.to }.let { if (it < 0) path.size else it }
                        return path.subList(startIndex, path.size) + edge
                    }
                    2 -> Unit
                    else -> {
                        state[edge.to] = 1
                        path += edge
                        iterators.addLast(edge.to to (outgoing[edge.to] ?: emptyList()).iterator())
                    }
                }
            }
        }
        return null
    }
}
