package io.github.alinourix.taski.core.ui.format

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.query.TaskQuery
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import java.time.LocalDate

object TaskIcons {
    val Due = Icons.Rounded.Event
    val Repeat = Icons.Rounded.Repeat
    val Timer = Icons.Rounded.Timer
    val Progress = Icons.Rounded.DonutLarge
    val Tag = Icons.AutoMirrored.Rounded.Label
    val Blocked = Icons.Rounded.Block
    val Reminder = Icons.Rounded.NotificationsActive

    fun status(status: TaskStatus): ImageVector = when (status) {
        TaskStatus.NotStarted -> Icons.Rounded.RadioButtonUnchecked
        TaskStatus.InProgress -> Icons.Rounded.Timelapse
        TaskStatus.Done -> Icons.Rounded.CheckCircle
        TaskStatus.NotDone -> Icons.Rounded.Report
    }

    /** The plugin's chevrons: two up, one up, equal, one down, two down. */
    fun priority(priority: Priority): ImageVector = when (priority) {
        Priority.Highest -> Icons.Rounded.KeyboardDoubleArrowUp
        Priority.High -> Icons.Rounded.KeyboardArrowUp
        Priority.Medium -> Icons.Rounded.DragHandle
        Priority.Low -> Icons.Rounded.KeyboardArrowDown
        Priority.Lowest -> Icons.Rounded.KeyboardDoubleArrowDown
    }
}

@Composable
fun statusLabel(status: TaskStatus): String = stringResource(
    when (status) {
        TaskStatus.NotStarted -> R.string.status_not_started
        TaskStatus.InProgress -> R.string.status_in_progress
        TaskStatus.Done -> R.string.status_done
        TaskStatus.NotDone -> R.string.status_not_done
    },
)

@Composable
fun priorityLabel(priority: Priority?): String = stringResource(
    when (priority) {
        Priority.Highest -> R.string.priority_highest
        Priority.High -> R.string.priority_high
        Priority.Medium -> R.string.priority_medium
        Priority.Low -> R.string.priority_low
        Priority.Lowest -> R.string.priority_lowest
        null -> R.string.priority_none
    },
)

@Composable
fun repeatLabel(rule: RepeatRule?): String {
    val persian = LocalUiConfig.current.persian
    if (rule == null) return stringResource(R.string.repeat_none)
    val text = when (rule.unit) {
        RepeatUnit.Day -> pluralStringResource(R.plurals.repeat_every_n_days, rule.every, rule.every)
        RepeatUnit.Week -> pluralStringResource(R.plurals.repeat_every_n_weeks, rule.every, rule.every)
        RepeatUnit.Month -> pluralStringResource(R.plurals.repeat_every_n_months, rule.every, rule.every)
    }
    return text.localizeDigits(persian)
}

/** Reminder offsets offered in the picker, in minutes before the due moment. */
val REMINDER_OFFSETS = listOf(0, 5, 15, 30, 60, 120, 24 * 60)

@Composable
fun reminderLabel(offsetMinutes: Int?): String {
    val persian = LocalUiConfig.current.persian
    val text = when {
        offsetMinutes == null -> stringResource(R.string.reminder_none)
        offsetMinutes == 0 -> stringResource(R.string.reminder_at_time)
        offsetMinutes % (24 * 60) == 0 -> (offsetMinutes / (24 * 60)).let { pluralStringResource(R.plurals.reminder_days_before, it, it) }
        offsetMinutes % 60 == 0 -> (offsetMinutes / 60).let { pluralStringResource(R.plurals.reminder_hours_before, it, it) }
        else -> pluralStringResource(R.plurals.reminder_minutes_before, offsetMinutes, offsetMinutes)
    }
    return text.localizeDigits(persian)
}

enum class Urgency { Overdue, Today, Soon, Later, Met }

data class DueText(val label: String, val urgency: Urgency)

/** The deadline chip's text and tone, as the plugin shows it: relative when near, a date when far. */
@Composable
fun dueText(task: Task, today: LocalDate = LocalUiConfig.current.today): DueText? {
    val due = task.dueDate ?: return null
    val config = LocalUiConfig.current
    val days = TaskQuery.daysUntil(due, today)
    val time = task.dueTime?.let { " " + CalendarText.time(it, config.persian) }.orEmpty()
    if (task.status == TaskStatus.Done) {
        return DueText(CalendarText.date(due, config.calendar, config.persian, today) + time, Urgency.Met)
    }
    val label = when {
        days < -1 -> pluralStringResource(R.plurals.due_days_overdue, (-days).toInt(), (-days).toInt()).localizeDigits(config.persian)
        days == -1L -> stringResource(R.string.due_yesterday)
        days == 0L -> stringResource(R.string.due_today) + time
        days == 1L -> stringResource(R.string.due_tomorrow) + time
        days <= 6 -> pluralStringResource(R.plurals.due_days_left, days.toInt(), days.toInt()).localizeDigits(config.persian)
        else -> CalendarText.date(due, config.calendar, config.persian, today) + time
    }
    val urgency = when {
        days < 0 -> Urgency.Overdue
        days == 0L -> Urgency.Today
        days <= 2 -> Urgency.Soon
        else -> Urgency.Later
    }
    return DueText(label, urgency)
}
