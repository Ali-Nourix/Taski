package io.github.alinourix.taski.core.data.sync

import androidx.room.withTransaction
import io.github.alinourix.taski.core.data.db.Tables
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.data.db.dao.OutboxDao
import io.github.alinourix.taski.core.data.db.entity.ActivityEntity
import io.github.alinourix.taski.core.data.db.entity.OutboxEntity
import io.github.alinourix.taski.core.data.db.entity.SyncColumns
import io.github.alinourix.taski.core.domain.id.IdGenerator
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.sync.Hlc
import io.github.alinourix.taski.core.domain.sync.HlcClock
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

enum class OutboxOp(val code: String) { Upsert("upsert"), Delete("delete") }

/**
 * The only way repositories write synced rows. Every change runs in one Room
 * transaction that also records it in the outbox, so a row can never be
 * changed without the sync engine hearing about it, and never recorded
 * without being changed.
 */
@Singleton
class ChangeWriter @Inject constructor(
    private val db: TaskiDatabase,
    private val hlc: HlcClock,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val outbox: OutboxDao = db.outboxDao()

    suspend fun <R> write(block: suspend Change.() -> R): R = db.withTransaction { Change(clock.nowMillis()).block() }

    inner class Change internal constructor(val now: Long) {
        fun rev(): Hlc = hlc.now()

        fun newId(): String = ids.newId()

        /** Columns for a row created in this change. */
        fun newSync(rev: Hlc) = SyncColumns(createdAt = now, updatedAt = now, rev = rev.encode())

        suspend fun upserted(table: String, rowId: String, fields: Collection<String>, rev: Hlc) =
            record(table, rowId, OutboxOp.Upsert, fields, rev)

        suspend fun deleted(table: String, rowId: String, rev: Hlc) =
            record(table, rowId, OutboxOp.Delete, listOf("deleted_at"), rev)

        private suspend fun record(table: String, rowId: String, op: OutboxOp, fields: Collection<String>, rev: Hlc) {
            outbox.record(
                OutboxEntity(
                    id = ids.newId(),
                    entity = table,
                    rowId = rowId,
                    op = op.code,
                    changedFields = OutboxDao.encodeFields(fields),
                    rev = rev.encode(),
                    createdAt = now,
                ),
            )
        }

        /** Appends to the activity log, itself a synced, append-only table. */
        suspend fun log(table: String, rowId: String, kind: ActivityKind, details: Map<String, String?> = emptyMap()) {
            val rev = rev()
            val entry = ActivityEntity(
                id = ids.newId(),
                entity = table,
                rowId = rowId,
                kind = kind.code,
                payload = JsonObject(details.mapValues { JsonPrimitive(it.value) }).toString(),
                occurredAt = now,
                sync = newSync(rev),
            )
            db.historyDao().insertActivity(entry)
            upserted(Tables.ACTIVITY_LOG, entry.id, ACTIVITY_FIELDS, rev)
        }
    }

    companion object {
        val ACTIVITY_FIELDS = listOf("entity", "row_id", "kind", "payload", "occurred_at")
    }
}
