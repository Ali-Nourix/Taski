package io.github.alinourix.taski.core.domain.model

/**
 * A task as the screens show it: the row itself, what it links to, and the
 * values derived from other rows. None of the derived values are stored.
 */
data class TaskItem(
    val task: Task,
    val tags: List<Tag> = emptyList(),
    val project: Project? = null,
    val subtaskCount: Int = 0,
    val subtasksDone: Int = 0,
    /** Open tasks this one waits for. */
    val blockedByCount: Int = 0,
) {
    val id: String get() = task.id
    val isBlocked: Boolean get() = blockedByCount > 0

    /** Manual steps when the task has that capability, otherwise its subtasks, otherwise nothing. */
    val progressFraction: Float?
        get() = task.progress?.fraction
            ?: if (subtaskCount > 0) subtasksDone.toFloat() / subtaskCount else null
}
