package io.github.alinourix.taski.core.domain.query

import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DeadlineBucket { Overdue, Today, Week, Later, Past, None }

data class TaskGroup(
    /** Stable id, `<groupBy>:<value>`, for remembering which groups are folded. */
    val id: String,
    val groupBy: GroupBy,
    /** A status or priority code, tag or project id, bucket name; [ViewDefinition.NONE] for "none". */
    val value: String,
    val items: List<TaskItem>,
)

data class Summary(val total: Int, val done: Int, val overdue: Int)

/**
 * Filtering, ordering and grouping for every task list: the board, a project,
 * Today. The same rules the plugin's board uses, as pure functions.
 */
object TaskQuery {

    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    fun deadlineBucket(item: TaskItem, today: LocalDate): DeadlineBucket {
        val due = item.task.dueDate ?: return DeadlineBucket.None
        val days = daysUntil(due, today)
        return when {
            days < 0 -> if (item.task.status == TaskStatus.Done) DeadlineBucket.Past else DeadlineBucket.Overdue
            days == 0L -> DeadlineBucket.Today
            days <= 7 -> DeadlineBucket.Week
            else -> DeadlineBucket.Later
        }
    }

    fun isOverdue(item: TaskItem, today: LocalDate): Boolean =
        item.task.status != TaskStatus.Done && deadlineBucket(item, today) == DeadlineBucket.Overdue

    /**
     * [matchingIds], when given, is the result of a full-text search; a task
     * also matches the search through its tags' and project's names, as on the
     * plugin's board.
     */
    fun filter(
        items: List<TaskItem>,
        view: ViewDefinition,
        search: String = "",
        matchingIds: Set<String>? = null,
    ): List<TaskItem> {
        val terms = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val statuses = view.statuses.toSet()
        val priorities = view.priorities.toSet()
        val tags = view.tagIds.toSet()
        val projects = view.projectIds.toSet()

        return items.filter { item ->
            val task = item.task
            if (!view.showSubtasks && task.parentId != null) return@filter false
            if (statuses.isNotEmpty() && task.status.code !in statuses) return@filter false
            if (priorities.isNotEmpty() && (task.priority?.code ?: ViewDefinition.NONE) !in priorities) return@filter false
            if (projects.isNotEmpty() && (task.projectId ?: ViewDefinition.NONE) !in projects) return@filter false
            if (tags.isNotEmpty()) {
                val matches = if (item.tags.isEmpty()) ViewDefinition.NONE in tags else item.tags.any { it.id in tags }
                if (!matches) return@filter false
            }
            if (terms.isNotEmpty()) {
                val byIndex = matchingIds?.contains(task.id) == true
                if (!byIndex) {
                    val haystack = buildString {
                        append(task.title.lowercase()).append(' ')
                        item.tags.forEach { append(it.name.lowercase()).append(' ') }
                        item.project?.let { append(it.name.lowercase()) }
                    }
                    if (!terms.all { it in haystack }) return@filter false
                }
            }
            true
        }
    }

    /**
     * Every key has a natural direction (most urgent priority first, soonest
     * deadline first, least finished first); [descending] reverses it. Tasks
     * without a value go last either way, and ties fall back to manual order,
     * so sorting never shuffles equal rows.
     */
    fun sort(items: List<TaskItem>, key: SortKey, descending: Boolean, today: LocalDate): List<TaskItem> {
        val manual = compareBy<TaskItem>({ it.task.sortKey }, { it.task.id })
        if (key == SortKey.Manual) {
            val sorted = items.sortedWith(manual)
            return if (descending) sorted.reversed() else sorted
        }
        val sign = if (descending) -1 else 1
        fun valueOf(item: TaskItem): Comparable<*>? = when (key) {
            SortKey.Title -> item.task.title.lowercase()
            SortKey.Status -> TaskStatus.BOARD_ORDER.indexOf(item.task.status)
            SortKey.Priority -> item.task.priority?.let { -it.rank }
            SortKey.Deadline -> item.task.dueDate?.let { daysUntil(it, today) }
            SortKey.Progress -> item.progressFraction
            SortKey.Project -> item.project?.name?.lowercase()
            SortKey.Created -> item.task.createdAt
            SortKey.Manual -> null
        }
        return items.sortedWith { a, b ->
            val va = valueOf(a)
            val vb = valueOf(b)
            when {
                va == null && vb == null -> manual.compare(a, b)
                va == null -> 1
                vb == null -> -1
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val diff = (va as Comparable<Any>).compareTo(vb)
                    if (diff != 0) diff * sign else manual.compare(a, b)
                }
            }
        }
    }

    /**
     * Splits already sorted tasks into groups, in a fixed order per grouping.
     * A task with two tags appears under both, like a multi-select board. Empty
     * status groups are kept so every board column exists; others are dropped.
     */
    fun group(items: List<TaskItem>, groupBy: GroupBy, tags: List<Tag>, today: LocalDate): List<TaskGroup> {
        fun make(value: String, list: List<TaskItem>) = TaskGroup("${groupBy.name.lowercase()}:$value", groupBy, value, list)

        return when (groupBy) {
            GroupBy.None -> listOf(make(ViewDefinition.NONE, items))
            GroupBy.Status -> TaskStatus.BOARD_ORDER.map { status -> make(status.code, items.filter { it.task.status == status }) }
            GroupBy.Priority -> (Priority.entries.map { it.code } + ViewDefinition.NONE)
                .map { code -> make(code, items.filter { (it.task.priority?.code ?: ViewDefinition.NONE) == code }) }
                .filter { it.items.isNotEmpty() }
            GroupBy.Deadline -> DeadlineBucket.entries
                .map { bucket -> make(bucket.name.lowercase(), items.filter { deadlineBucket(it, today) == bucket }) }
                .filter { it.items.isNotEmpty() }
            GroupBy.Tag -> {
                val order = tags.sortedWith(compareBy({ it.sortKey }, { it.id })).map { it.id }
                (order.map { id -> make(id, items.filter { item -> item.tags.any { it.id == id } }) } +
                    make(ViewDefinition.NONE, items.filter { it.tags.isEmpty() }))
                    .filter { it.items.isNotEmpty() }
            }
            GroupBy.Project -> {
                val byProject = items.groupBy { it.task.projectId }
                val inbox = byProject[null]?.let { listOf(make(ViewDefinition.NONE, it)) }.orEmpty()
                inbox + byProject.filterKeys { it != null }
                    .map { (id, list) -> Triple(id!!, list.first().project, list) }
                    .sortedWith(compareBy({ it.second?.sortKey ?: "~" }, { it.first }))
                    .map { (id, _, list) -> make(id, list) }
            }
        }
    }

    fun summarize(items: List<TaskItem>, today: LocalDate): Summary {
        var done = 0
        var overdue = 0
        for (item in items) {
            if (item.task.status == TaskStatus.Done) done++ else if (isOverdue(item, today)) overdue++
        }
        return Summary(items.size, done, overdue)
    }

    /**
     * Today: everything due today or earlier that is still open, and whatever
     * is in progress, most urgent first.
     */
    fun today(items: List<TaskItem>, today: LocalDate): List<TaskItem> = items
        .filter { item ->
            val task = item.task
            val dueByToday = task.dueDate?.let { !it.isAfter(today) } == true
            (task.status.isOpen || task.status == TaskStatus.NotDone) && dueByToday ||
                task.status == TaskStatus.InProgress
        }
        .sortedWith(
            compareBy<TaskItem>(
                { it.task.dueDate ?: LocalDate.MAX },
                { -(it.task.priority?.rank ?: 0) },
                { it.task.dueTime },
                { it.task.sortKey },
            ),
        )
}
