package io.github.alinourix.taski.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.alinourix.taski.core.data.db.entity.OutboxEntity
import io.github.alinourix.taski.core.data.db.entity.ReminderStateEntity
import io.github.alinourix.taski.core.data.db.entity.TimerStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Dao
abstract class OutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE entity = :entity AND row_id = :rowId")
    abstract suspend fun find(entity: String, rowId: String): OutboxEntity?

    @Query("SELECT * FROM sync_outbox ORDER BY created_at, id")
    abstract suspend fun all(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    abstract fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(entry: OutboxEntity)

    @Query("UPDATE sync_outbox SET op = :op, changed_fields = :fields, rev = :rev WHERE id = :id")
    protected abstract suspend fun fold(id: String, op: String, fields: String, rev: String)

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    abstract suspend fun remove(ids: List<String>)

    /**
     * Records a change, compacted: a row already waiting to be pushed keeps
     * its place in the queue and takes the newest op and revision, and the
     * union of the fields changed so far.
     */
    @Transaction
    open suspend fun record(entry: OutboxEntity) {
        val existing = find(entry.entity, entry.rowId)
        if (existing == null) {
            insert(entry)
            return
        }
        val fields = (decodeFields(existing.changedFields) + decodeFields(entry.changedFields)).distinct().sorted()
        fold(existing.id, entry.op, encodeFields(fields), entry.rev)
    }

    companion object {
        private val serializer = ListSerializer(String.serializer())

        fun encodeFields(fields: Collection<String>): String = Json.encodeToString(serializer, fields.distinct().sorted())

        fun decodeFields(value: String): List<String> = runCatching { Json.decodeFromString(serializer, value) }.getOrDefault(emptyList())
    }
}

@Dao
interface ReminderStateDao {
    @Query("SELECT * FROM reminder_state")
    suspend fun all(): List<ReminderStateEntity>

    @Query("SELECT * FROM reminder_state WHERE task_id = :taskId")
    suspend fun get(taskId: String): ReminderStateEntity?

    @Upsert
    suspend fun upsert(state: ReminderStateEntity)

    @Query("DELETE FROM reminder_state WHERE task_id IN (:taskIds)")
    suspend fun delete(taskIds: List<String>)
}

@Dao
interface TimerStateDao {
    @Query("SELECT * FROM timer_state LIMIT 1")
    fun observe(): Flow<TimerStateEntity?>

    @Query("SELECT * FROM timer_state LIMIT 1")
    suspend fun get(): TimerStateEntity?

    @Query("DELETE FROM timer_state")
    suspend fun clear()

    @Insert
    suspend fun insert(state: TimerStateEntity)

    /** There is at most one countdown per device. */
    @Transaction
    suspend fun replace(state: TimerStateEntity?) {
        clear()
        if (state != null) insert(state)
    }
}
