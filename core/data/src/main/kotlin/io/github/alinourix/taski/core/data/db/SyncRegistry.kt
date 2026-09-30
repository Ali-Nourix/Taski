package io.github.alinourix.taski.core.data.db

/** Every table name, in one place, shared by entities, queries and the registry. */
object Tables {
    const val TASKS = "tasks"
    const val PROJECTS = "projects"
    const val TAGS = "tags"
    const val TASK_TAGS = "task_tags"
    const val TASK_DEPENDENCIES = "task_dependencies"
    const val TASK_COMPLETIONS = "task_completions"
    const val TIMER_SESSIONS = "timer_sessions"
    const val ACTIVITY_LOG = "activity_log"
    const val SAVED_VIEWS = "saved_views"

    const val SYNC_OUTBOX = "sync_outbox"
    const val REMINDER_STATE = "reminder_state"
    const val TIMER_STATE = "timer_state"
    const val TASK_FTS = "task_fts"
}

enum class SyncMode { Synced, LocalOnly }

/** How a synced row is written, which decides how the sync engine merges it. */
enum class RowShape {
    /** Several fields edited independently: per-field clocks in `field_revs`. */
    FieldClocked,
    /** Written whole (links): last writer wins on `rev`. */
    Whole,
    /** Written once, never edited: syncing is idempotent. */
    AppendOnly,
    /** Not synced. */
    Local,
}

data class TableSpec(val name: String, val mode: SyncMode, val shape: RowShape)

/**
 * The one place that says which tables sync and which never leave the device.
 * A test checks every table in Room's exported schema against this list, that
 * every synced table carries [REQUIRED_COLUMNS], and that the draft Supabase
 * migration declares the same synced tables and columns.
 */
object SyncRegistry {
    val REQUIRED_COLUMNS = listOf("id", "user_id", "created_at", "updated_at", "deleted_at", "rev", "server_updated_at")
    const val FIELD_REVS = "field_revs"

    val tables: List<TableSpec> = listOf(
        TableSpec(Tables.TASKS, SyncMode.Synced, RowShape.FieldClocked),
        TableSpec(Tables.PROJECTS, SyncMode.Synced, RowShape.FieldClocked),
        TableSpec(Tables.TAGS, SyncMode.Synced, RowShape.FieldClocked),
        TableSpec(Tables.SAVED_VIEWS, SyncMode.Synced, RowShape.FieldClocked),
        TableSpec(Tables.TASK_TAGS, SyncMode.Synced, RowShape.Whole),
        TableSpec(Tables.TASK_DEPENDENCIES, SyncMode.Synced, RowShape.Whole),
        TableSpec(Tables.TASK_COMPLETIONS, SyncMode.Synced, RowShape.AppendOnly),
        TableSpec(Tables.TIMER_SESSIONS, SyncMode.Synced, RowShape.AppendOnly),
        TableSpec(Tables.ACTIVITY_LOG, SyncMode.Synced, RowShape.AppendOnly),

        TableSpec(Tables.SYNC_OUTBOX, SyncMode.LocalOnly, RowShape.Local),
        TableSpec(Tables.REMINDER_STATE, SyncMode.LocalOnly, RowShape.Local),
        TableSpec(Tables.TIMER_STATE, SyncMode.LocalOnly, RowShape.Local),
        TableSpec(Tables.TASK_FTS, SyncMode.LocalOnly, RowShape.Local),
    )

    val synced: List<TableSpec> get() = tables.filter { it.mode == SyncMode.Synced }

    fun spec(table: String): TableSpec? = tables.firstOrNull { it.name == table }

    /**
     * Tables SQLite or Room create for their own bookkeeping; they are neither
     * app data nor ours to classify. FTS shadow tables are named after [Tables.TASK_FTS].
     */
    fun isInternal(table: String): Boolean =
        table == "room_master_table" || table.startsWith("sqlite_") || table.startsWith("${Tables.TASK_FTS}_")
}
