package io.github.alinourix.taski.core.domain.model

import java.time.LocalDate

data class Project(
    val id: String,
    val name: String,
    val color: ColorToken,
    val sortKey: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A label with a colour. Unlike the plugin, where a note stores a tag's key,
 * tasks link to a tag by id, so renaming or recolouring never touches a task.
 */
data class Tag(
    val id: String,
    val name: String,
    val color: ColorToken,
    val sortKey: String,
)

/** One completion of a task, kept forever. A repeating task gets one per occurrence. */
data class Completion(
    val id: String,
    val taskId: String,
    /** The due date that was completed, for repeating tasks. */
    val occurrenceDate: LocalDate?,
    val completedAt: Long,
)

/** A stretch of focused time on a task. Written once, when the stretch ends. */
data class TimerSession(
    val id: String,
    val taskId: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    /** True when the countdown reached zero rather than being stopped early. */
    val finished: Boolean,
) {
    val seconds: Long get() = (endedAt - startedAt) / 1000
}

enum class ActivityKind(override val code: String) : Coded {
    Created("created"),
    StatusChanged("status_changed"),
    Completed("completed"),
    RolledForward("rolled_forward"),
    Deleted("deleted"),
    Restored("restored"),
    MovedToInbox("moved_to_inbox"),
    CycleEdgeDropped("cycle_edge_dropped"),
    ;

    companion object {
        fun fromCode(code: String?): ActivityKind? = codeOf<ActivityKind>(code)
    }
}

/** An append-only log line: what happened to which row, and when. */
data class ActivityEntry(
    val id: String,
    val entity: String,
    val rowId: String,
    val kind: ActivityKind,
    /** Small JSON object with the details, e.g. `{"from":"done","to":"not_started"}`. */
    val payload: String,
    val occurredAt: Long,
)

data class SavedView(
    val id: String,
    val name: String,
    val definition: ViewDefinition,
    val sortKey: String,
)
