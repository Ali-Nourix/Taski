package io.github.alinourix.taski.core.data.repository

import io.github.alinourix.taski.core.data.db.entity.ProjectEntity
import io.github.alinourix.taski.core.data.db.entity.SavedViewEntity
import io.github.alinourix.taski.core.data.db.entity.TagEntity
import io.github.alinourix.taski.core.data.db.entity.TaskEntity

/**
 * The user-editable columns of each field-clocked table, with how to read
 * them. Changed fields are found by comparing the row before and after an
 * edit, so a repository cannot forget to stamp a column it wrote.
 */
internal class ColumnSet<T>(private val columns: List<Pair<String, (T) -> Any?>>) {
    val names: List<String> = columns.map { it.first }

    fun changed(old: T, new: T): List<String> = columns.filter { (_, read) -> read(old) != read(new) }.map { it.first }
}

internal val TaskColumns = ColumnSet<TaskEntity>(
    listOf(
        "project_id" to { it.projectId },
        "parent_id" to { it.parentId },
        "title" to { it.title },
        "notes" to { it.notes },
        "status" to { it.status },
        "priority" to { it.priority },
        "due_date" to { it.dueDate },
        "due_time" to { it.dueTime },
        "repeat_rule" to { it.repeatRule },
        "progress_done" to { it.progressDone },
        "progress_total" to { it.progressTotal },
        "timer_minutes" to { it.timerMinutes },
        "reminder_offset_min" to { it.reminderOffsetMin },
        "sort_key" to { it.sortKey },
        "completed_at" to { it.completedAt },
        "purged_at" to { it.purgedAt },
        "deleted_at" to { it.sync.deletedAt },
    ),
)

internal val ProjectColumns = ColumnSet<ProjectEntity>(
    listOf(
        "name" to { it.name },
        "color" to { it.color },
        "sort_key" to { it.sortKey },
        "deleted_at" to { it.sync.deletedAt },
    ),
)

internal val TagColumns = ColumnSet<TagEntity>(
    listOf(
        "name" to { it.name },
        "color" to { it.color },
        "sort_key" to { it.sortKey },
        "deleted_at" to { it.sync.deletedAt },
    ),
)

internal val SavedViewColumns = ColumnSet<SavedViewEntity>(
    listOf(
        "name" to { it.name },
        "definition" to { it.definition },
        "sort_key" to { it.sortKey },
        "deleted_at" to { it.sync.deletedAt },
    ),
)

internal val LinkFields = listOf("deleted_at")
