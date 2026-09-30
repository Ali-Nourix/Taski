package io.github.alinourix.taski.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.alinourix.taski.core.data.db.Tables

// Synced tables. No foreign keys between them: rows can arrive by sync in any
// order (a subtask before its parent), and a dangling reference is resolved by
// the conflict rules instead of rejected by the database.

@Entity(
    tableName = Tables.TASKS,
    indices = [Index("project_id"), Index("parent_id"), Index("due_date"), Index("deleted_at")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val title: String,
    val notes: String,
    /** A [io.github.alinourix.taski.core.domain.model.TaskStatus] code. */
    val status: String,
    val priority: String?,
    @ColumnInfo(name = "start_date") val startDate: String?,
    @ColumnInfo(name = "start_time") val startTime: String?,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "due_time") val dueTime: String?,
    /** Versioned JSON; see [io.github.alinourix.taski.core.domain.model.RepeatRule]. */
    @ColumnInfo(name = "repeat_rule") val repeatRule: String?,
    @ColumnInfo(name = "progress_done") val progressDone: Int?,
    @ColumnInfo(name = "progress_total") val progressTotal: Int?,
    @ColumnInfo(name = "timer_minutes") val timerMinutes: Int?,
    @ColumnInfo(name = "reminder_offset_min") val reminderOffsetMin: Int?,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    /** Set when the trash was emptied: the tombstone stays, its content is gone. */
    @ColumnInfo(name = "purged_at") val purgedAt: Long? = null,
    @ColumnInfo(name = "field_revs") val fieldRevs: String,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = Tables.PROJECTS)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "field_revs") val fieldRevs: String,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = Tables.TAGS)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "field_revs") val fieldRevs: String,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = Tables.SAVED_VIEWS)
data class SavedViewEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Versioned JSON; see [io.github.alinourix.taski.core.domain.model.ViewDefinition]. */
    val definition: String,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "field_revs") val fieldRevs: String,
    @Embedded val sync: SyncColumns,
)

/** A task carries a tag. Removing the tag tombstones the row; adding it again revives it. */
@Entity(tableName = Tables.TASK_TAGS, indices = [Index(value = ["task_id", "tag_id"], unique = true), Index("tag_id")])
data class TaskTagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
    @Embedded val sync: SyncColumns,
)

/** [taskId] waits for [dependsOnId]. */
@Entity(
    tableName = Tables.TASK_DEPENDENCIES,
    indices = [Index(value = ["task_id", "depends_on_id"], unique = true), Index("depends_on_id")],
)
data class TaskDependencyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "depends_on_id") val dependsOnId: String,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = Tables.TASK_COMPLETIONS, indices = [Index("task_id")])
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "occurrence_date") val occurrenceDate: String?,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = Tables.TIMER_SESSIONS, indices = [Index("task_id")])
data class TimerSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "planned_seconds") val plannedSeconds: Int,
    val finished: Boolean,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = Tables.ACTIVITY_LOG, indices = [Index("row_id")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    /** The table the row lives in. */
    val entity: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** An [io.github.alinourix.taski.core.domain.model.ActivityKind] code. */
    val kind: String,
    val payload: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @Embedded val sync: SyncColumns,
)

// Local-only tables.

/**
 * Pending changes for a future sync engine to push, written in the same
 * transaction as the change itself. One row per changed row: later changes
 * fold into it, so the outbox never grows past the number of dirty rows.
 */
@Entity(tableName = Tables.SYNC_OUTBOX, indices = [Index(value = ["entity", "row_id"], unique = true)])
data class OutboxEntity(
    @PrimaryKey val id: String,
    val entity: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** `upsert` or `delete`. */
    val op: String,
    /** JSON array of the column names changed since the last push. */
    @ColumnInfo(name = "changed_fields") val changedFields: String,
    val rev: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Delivery bookkeeping for a task's reminder, rebuilt from task data at will. */
@Entity(tableName = Tables.REMINDER_STATE)
data class ReminderStateEntity(
    @PrimaryKey @ColumnInfo(name = "task_id") val taskId: String,
    /** The moment the alarm is set for. */
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long?,
    /** The last delivery shown, so the same due moment never alerts twice. */
    @ColumnInfo(name = "delivered_key") val deliveredKey: String?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?,
)

/** The focus timer counting down on this device. */
@Entity(tableName = Tables.TIMER_STATE)
data class TimerStateEntity(
    @PrimaryKey @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "planned_seconds") val plannedSeconds: Int,
    @ColumnInfo(name = "banked_seconds") val bankedSeconds: Long,
    @ColumnInfo(name = "running_since") val runningSince: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Long?,
)

/** Full-text index over task titles and notes, kept in step with [TaskEntity] by triggers Room creates. */
@Fts4(contentEntity = TaskEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = Tables.TASK_FTS)
data class TaskFtsEntity(
    val title: String,
    val notes: String,
)
