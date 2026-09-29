package io.github.alinourix.taski.core.domain.reminder

import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.query.TaskQuery
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * When to alert, as pure functions of task data and settings. Alarms are
 * rebuilt from these after any change, so a task edited on another device and
 * synced in later reschedules itself the same way as a local edit.
 */
object ReminderRules {
    /** The time of day a reminder uses when the task has a date but no time. */
    val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)

    /** The moment a task's own reminder fires, or null if it has none. */
    fun taskReminderAt(task: Task, zone: ZoneId, defaultTime: LocalTime = DEFAULT_DUE_TIME): Instant? {
        val offset = task.reminderOffsetMinutes ?: return null
        val date = task.dueDate ?: return null
        if (task.isDeleted || task.status == TaskStatus.Done) return null
        val due = ZonedDateTime.of(date, task.dueTime ?: defaultTime, zone)
        return due.minusMinutes(offset.toLong()).toInstant()
    }

    /** Stable identity of one delivery, so a reminder is never shown twice for the same due moment. */
    fun deliveryKey(task: Task, at: Instant): String = "${task.id}@${at.toEpochMilli()}"

    fun isQuietTime(time: LocalTime, settings: DigestSettings): Boolean {
        if (!settings.quietHoursEnabled || settings.quietStart == settings.quietEnd) return false
        val start = settings.quietStart
        val end = settings.quietEnd
        // A range that wraps past midnight, such as 23:00 → 07:00.
        return if (start < end) time >= start && time < end else time >= start || time < end
    }

    /**
     * The next digest after [now]: the next fixed time of day, or the next
     * repeat interval after [lastFiredAt], whichever is sooner, moved past
     * quiet hours. Null when the digest is off or has nothing to schedule.
     */
    fun nextDigestAt(now: Instant, zone: ZoneId, settings: DigestSettings, lastFiredAt: Instant?): Instant? {
        if (!settings.enabled) return null
        val local = now.atZone(zone)
        val candidates = mutableListOf<ZonedDateTime>()

        for (time in settings.times.distinct()) {
            var at = local.toLocalDate().atTime(time).atZone(zone)
            if (!at.isAfter(local)) at = at.plusDays(1)
            // A fixed time that falls in quiet hours is skipped, not moved.
            if (!isQuietTime(time, settings)) candidates += at
        }

        if (settings.repeatEnabled) {
            val interval = settings.repeatIntervalMinutes.coerceAtLeast(DigestSettings.MIN_REPEAT_MINUTES).toLong()
            var at = (lastFiredAt ?: now).atZone(zone).plusMinutes(interval)
            if (!at.isAfter(local)) at = local.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)
            candidates += skipQuiet(at, settings)
        }
        return candidates.minOrNull()?.toInstant()
    }

    private fun skipQuiet(at: ZonedDateTime, settings: DigestSettings): ZonedDateTime {
        if (!isQuietTime(at.toLocalTime(), settings)) return at
        var end = at.toLocalDate().atTime(settings.quietEnd).atZone(at.zone)
        if (!end.isAfter(at)) end = end.plusDays(1)
        return end
    }

    /**
     * The tasks a digest lists, most urgent first: open tasks that pass the
     * priority, status, deadline and age filters.
     */
    fun digestTasks(items: List<TaskItem>, settings: DigestSettings, today: LocalDate, now: Instant): List<TaskItem> =
        items.asSequence()
            .filter { !it.task.isDeleted && it.task.status != TaskStatus.Done }
            .filter { it.task.status in settings.statuses }
            .filter { item ->
                val priority = item.task.priority
                if (priority == null) settings.includeWithoutPriority else priority in settings.priorities
            }
            .filter { item ->
                val deadline = matchesDeadline(item.task, settings, today)
                if (settings.createdWithinDays <= 0) return@filter deadline
                val ageDays = ChronoUnit.DAYS.between(Instant.ofEpochMilli(item.task.createdAt), now)
                val recent = ageDays < settings.createdWithinDays
                when (settings.ageFilterMode) {
                    AgeFilterMode.Only -> recent
                    AgeFilterMode.Or -> recent || deadline
                    AgeFilterMode.And -> recent && deadline
                }
            }
            .sortedWith(
                compareBy<TaskItem>(
                    { -(it.task.priority?.rank ?: 0) },
                    { it.task.dueDate?.let { d -> TaskQuery.daysUntil(d, today) } ?: Long.MAX_VALUE },
                    { it.task.sortKey },
                ),
            )
            .toList()

    private fun matchesDeadline(task: Task, settings: DigestSettings, today: LocalDate): Boolean {
        val due = task.dueDate ?: return settings.includeWithoutDeadline
        val days = TaskQuery.daysUntil(due, today)
        return if (days < 0) settings.includeOverdue else days <= settings.dueWithinDays
    }
}
