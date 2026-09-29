package io.github.alinourix.taski.core.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Steps done out of a planned number: the plugin's progress-bar capability. */
data class StepProgress(val done: Int, val total: Int) {
    init {
        require(total >= 1) { "total must be at least 1" }
    }

    val clampedDone: Int get() = done.coerceIn(0, total)
    val fraction: Float get() = clampedDone.toFloat() / total
}

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.NotStarted,
    val priority: Priority? = null,
    /** Null means the Inbox. */
    val projectId: String? = null,
    /** The task this one is a subtask of. */
    val parentId: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeat: RepeatRule? = null,
    /** Opt-in capabilities: each is off until the user adds it to the task. */
    val progress: StepProgress? = null,
    val timerMinutes: Int? = null,
    /** Minutes before the due moment to remind; null means no reminder. */
    val reminderOffsetMinutes: Int? = null,
    val sortKey: String,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isDone: Boolean get() = status == TaskStatus.Done
}

/** One field-level change to a task. Each maps to exactly the columns it writes. */
sealed interface TaskEdit {
    data class Title(val value: String) : TaskEdit
    data class Notes(val value: String) : TaskEdit
    data class SetPriority(val value: Priority?) : TaskEdit
    data class Due(val date: LocalDate?, val time: LocalTime?) : TaskEdit
    data class Repeat(val value: RepeatRule?) : TaskEdit
    data class Progress(val value: StepProgress?) : TaskEdit
    data class Timer(val minutes: Int?) : TaskEdit
    data class Reminder(val offsetMinutes: Int?) : TaskEdit
    data class MoveToProject(val projectId: String?) : TaskEdit
    data class SetParent(val parentId: String?) : TaskEdit
}

/** What a new task starts with. Everything else takes its default. */
data class NewTask(
    val title: String,
    val notes: String = "",
    val projectId: String? = null,
    val parentId: String? = null,
    val status: TaskStatus = TaskStatus.NotStarted,
    val priority: Priority? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeat: RepeatRule? = null,
    val tagIds: List<String> = emptyList(),
    val timerMinutes: Int? = null,
    val progress: StepProgress? = null,
    val reminderOffsetMinutes: Int? = null,
)
