package io.github.alinourix.taski.core.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConflictRulesTest {
    private val a = "aaaaaaaaaaaaaaaa"
    private val b = "bbbbbbbbbbbbbbbb"
    private fun rev(millis: Long, node: String = a) = Hlc(millis, 0, node)

    private fun row(
        values: Map<String, String?>,
        revs: Map<String, Hlc>,
        deletedAt: Long? = null,
    ) = RowState("row", values, revs.values.max(), deletedAt, FieldRevs(revs))

    @Test
    fun concurrentEditsToDifferentFieldsBothSurvive() {
        val base = mapOf("title" to "Old", "priority" to null)
        val local = row(base + ("title" to "New title"), mapOf("title" to rev(20), "priority" to rev(1)))
        val remote = row(base + ("priority" to "high"), mapOf("title" to rev(1), "priority" to rev(30, b)))

        val merged = ConflictRules.mergeRow(local, remote)

        assertEquals("New title", merged.values["title"])
        assertEquals("high", merged.values["priority"])
        assertEquals(rev(30, b), merged.rev)
        assertEquals(merged, ConflictRules.mergeRow(remote, local), "merge must be commutative")
    }

    @Test
    fun sameFieldGoesToTheLaterRevision() {
        val local = row(mapOf("title" to "Mine"), mapOf("title" to rev(10)))
        val remote = row(mapOf("title" to "Theirs"), mapOf("title" to rev(10, b)))
        // Same millis and counter: the device id breaks the tie, identically on both sides.
        assertEquals("Theirs", ConflictRules.mergeRow(local, remote).values["title"])
        assertEquals("Theirs", ConflictRules.mergeRow(remote, local).values["title"])
    }

    @Test
    fun aLaterEditResurrectsADeletedRow() {
        val deleted = row(mapOf("title" to "T"), mapOf("title" to rev(1), "deleted_at" to rev(10)), deletedAt = 10)
        val edited = row(mapOf("title" to "Edited"), mapOf("title" to rev(20, b)))

        val merged = ConflictRules.mergeRow(deleted, edited)

        assertNull(merged.deletedAt)
        assertEquals("Edited", merged.values["title"])
        assertEquals(ConflictRules.mergeRow(edited, deleted), merged)
    }

    @Test
    fun aLaterDeleteWinsOverAnEarlierEdit() {
        val edited = row(mapOf("title" to "Edited"), mapOf("title" to rev(20, b)))
        val deleted = row(mapOf("title" to "T"), mapOf("title" to rev(1), "deleted_at" to rev(30)), deletedAt = 30)

        val merged = ConflictRules.mergeRow(edited, deleted)

        assertEquals(30L, merged.deletedAt)
        assertEquals("Edited", merged.values["title"], "the edit still lands in the tombstone")
    }

    @Test
    fun restoringFromTheTrashIsANewerEditOfTheTombstone() {
        val deleted = row(mapOf("title" to "T"), mapOf("title" to rev(1), "deleted_at" to rev(10)), deletedAt = 10)
        val restored = row(mapOf("title" to "T"), mapOf("title" to rev(1), "deleted_at" to rev(15, b)), deletedAt = null)
        assertNull(ConflictRules.mergeRow(deleted, restored).deletedAt)
        assertNull(ConflictRules.mergeRow(restored, deleted).deletedAt)
    }

    @Test
    fun wholeRowsMergeOnTheirRevision() {
        val old = RowState("l", mapOf("tag_id" to "t"), rev(1))
        val removed = RowState("l", mapOf("tag_id" to "t"), rev(2, b), deletedAt = 2)
        assertEquals(removed, ConflictRules.mergeRow(old, removed))
        assertEquals(removed, ConflictRules.mergeRow(removed, old))
    }

    @Test
    fun duplicateLinksCollapseOntoTheLowerIdWithTheLatestState() {
        val first = RowState("018f-0001", mapOf("task_id" to "x", "tag_id" to "y"), rev(5))
        val second = RowState("018f-0002", mapOf("task_id" to "x", "tag_id" to "y"), rev(9, b), deletedAt = 9)

        val merged = ConflictRules.mergeDuplicateLinks(second, first)

        assertEquals("018f-0001", merged.kept.id)
        assertEquals(9L, merged.kept.deletedAt)
        assertEquals("018f-0002", merged.droppedId)
    }
}
