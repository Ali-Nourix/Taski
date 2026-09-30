package io.github.alinourix.taski.core.domain.sync

/**
 * One side of a row as the sync engine sees it: column values in their
 * storage form, the clocks that wrote them, and the tombstone.
 *
 * [fieldRevs] is empty for rows that are only ever written whole (links,
 * append-only records); those merge on [rev] alone.
 */
data class RowState(
    val id: String,
    val values: Map<String, String?>,
    val rev: Hlc,
    val deletedAt: Long? = null,
    val fieldRevs: FieldRevs = FieldRevs(),
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * The merge rules the sync engine will apply. Pure functions: they decide,
 * the engine only feeds them rows and writes back what they return.
 */
object ConflictRules {
    /** The tombstone is merged like any other field, under this name. */
    const val DELETED_AT = "deleted_at"

    /**
     * Field-level last-writer-wins by HLC, then the delete-versus-edit rule:
     * whichever of the tombstone and the newest edit carries the later revision
     * wins, so an edit made after a delete brings the row back.
     *
     * A field one side never stamped falls back to that side's row revision,
     * which is what a row written before field clocks existed would carry.
     */
    fun mergeRow(local: RowState, remote: RowState): RowState {
        require(local.id == remote.id) { "cannot merge different rows: ${local.id} / ${remote.id}" }
        if (local.fieldRevs.revs.isEmpty() && remote.fieldRevs.revs.isEmpty()) return mergeWhole(local, remote)

        val fields = (local.values.keys + remote.values.keys) - DELETED_AT
        val values = mutableMapOf<String, String?>()
        val revs = mutableMapOf<String, Hlc>()
        for (field in fields) {
            val localRev = local.fieldRevs[field] ?: local.rev
            val remoteRev = remote.fieldRevs[field] ?: remote.rev
            val winner = if (remoteRev > localRev) remote else local
            values[field] = winner.values[field]
            revs[field] = if (remoteRev > localRev) remoteRev else localRev
        }

        val localDeleteRev = local.fieldRevs[DELETED_AT] ?: local.rev.takeIf { local.isDeleted }
        val remoteDeleteRev = remote.fieldRevs[DELETED_AT] ?: remote.rev.takeIf { remote.isDeleted }
        val tombstoneFromRemote = remoteDeleteRev != null && (localDeleteRev == null || remoteDeleteRev > localDeleteRev)
        var deletedAt = if (tombstoneFromRemote) remote.deletedAt else local.deletedAt
        var deleteRev = if (tombstoneFromRemote) remoteDeleteRev else localDeleteRev

        if (deletedAt != null && deleteRev != null) {
            val newestEdit = revs.values.maxOrNull()
            if (newestEdit != null && newestEdit > deleteRev) {
                deletedAt = null
                deleteRev = newestEdit
            }
        }
        deleteRev?.let { revs[DELETED_AT] = it }

        return RowState(
            id = local.id,
            values = values,
            rev = revs.values.maxOrNull() ?: laterOf(local.rev, remote.rev)!!,
            deletedAt = deletedAt,
            fieldRevs = FieldRevs(revs),
        )
    }

    /** Whole-row last-writer-wins: the later revision decides values and tombstone together. */
    fun mergeWhole(local: RowState, remote: RowState): RowState = if (remote.rev > local.rev) remote else local

    /**
     * Two link rows (task↔tag, a dependency) created independently for the same
     * pair, which the server's unique constraint allows only once. The lower id
     * survives, so both devices pick the same one; its state is whichever side
     * changed last, so an add on one device and a remove on another resolve by
     * time like any other edit.
     */
    fun mergeDuplicateLinks(a: RowState, b: RowState): LinkMerge {
        val (keep, drop) = if (a.id <= b.id) a to b else b to a
        val latest = if (b.rev > a.rev) b else a
        return LinkMerge(
            kept = keep.copy(values = latest.values, rev = latest.rev, deletedAt = latest.deletedAt),
            droppedId = drop.id,
        )
    }

    data class LinkMerge(val kept: RowState, val droppedId: String)
}
