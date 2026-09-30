package io.github.alinourix.taski.core.domain.reminder

import io.github.alinourix.taski.core.domain.model.Coded
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.codeOf
import java.time.LocalTime

/** gentle = a quiet notification, normal = heads-up, strict = full-screen alert that must be acknowledged. */
enum class Strictness(override val code: String) : Coded {
    Gentle("gentle"), Normal("normal"), Strict("strict");

    companion object {
        fun fromCode(code: String?): Strictness = codeOf<Strictness>(code) ?: Normal
    }
}

/** How "created within N days" combines with the deadline filters. */
enum class AgeFilterMode(override val code: String) : Coded {
    Only("only"), Or("or"), And("and");

    companion object {
        fun fromCode(code: String?): AgeFilterMode = codeOf<AgeFilterMode>(code) ?: Only
    }
}

/**
 * The daily digest: the plugin's reminder schedule, filters and strictness.
 * A device setting, so it is kept in preferences rather than synced.
 */
data class DigestSettings(
    val enabled: Boolean = true,
    val times: List<LocalTime> = listOf(LocalTime.of(9, 0)),
    val repeatEnabled: Boolean = false,
    val repeatIntervalMinutes: Int = 180,
    val quietHoursEnabled: Boolean = false,
    val quietStart: LocalTime = LocalTime.of(23, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0),
    val strictness: Strictness = Strictness.Normal,
    val strictHoldSeconds: Int = 5,
    val snoozeMinutes: Int = 10,
    val priorities: Set<Priority> = setOf(Priority.Highest, Priority.High, Priority.Medium),
    val includeWithoutPriority: Boolean = false,
    val statuses: Set<TaskStatus> = setOf(TaskStatus.NotStarted, TaskStatus.InProgress, TaskStatus.NotDone),
    val includeOverdue: Boolean = true,
    /** Include tasks due within this many days (0 = today only). */
    val dueWithinDays: Int = 0,
    val includeWithoutDeadline: Boolean = false,
    /** Only tasks created within this many days; 0 turns the age filter off. */
    val createdWithinDays: Int = 0,
    val ageFilterMode: AgeFilterMode = AgeFilterMode.Only,
    val maxTasksShown: Int = 12,
) {
    companion object {
        const val MIN_REPEAT_MINUTES = 5
    }
}
