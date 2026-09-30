package io.github.alinourix.taski.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.schedule.Schedule
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.picker.OptionItem
import io.github.alinourix.taski.core.ui.picker.PickerSheet
import io.github.alinourix.taski.core.ui.R as UiR
import io.github.alinourix.taski.core.domain.schedule.TaskSchedule
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The board as time: every task with a start or a due laid out on a day, a week or a month, so what a task
 * is for is seen as where it sits. A day is an hour grid you touch — press and drag a block to move it, drag its
 * edge to change when it ends. A week or a month is a plan of only the days that matter, big, with a ribbon on
 * each several-day task saying from where to where.
 */
@Composable
internal fun BoardTimeline(
    state: BoardState,
    timeline: TimelineState,
    viewModel: BoardViewModel,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    contentPadding: PaddingValues,
) {
    val config = LocalUiConfig.current
    var draft by remember { mutableStateOf<Schedule?>(null) }

    val scale = timeline.scale
    val anchor = timeline.anchor
    val days = remember(scale, anchor, config.calendar, config.persian) { visibleDays(scale, anchor, config.calendar, config.persian) }
    val items = remember(state.groups) { state.groups.flatMap { it.items }.distinctBy { it.id } }
    val unscheduled = remember(state.groups) { unscheduledOf(state) }
    val target = if (config.today in days) config.today else days.first()
    val bottom = contentPadding.calculateBottomPadding() + 104.dp

    val week = remember(anchor, config.calendar, config.persian) { visibleDays(TimelineScale.Week, anchor, config.calendar, config.persian) }
    val busy = remember(items, week) {
        week.filter { day -> items.any { TaskSchedule.spanOf(it.task.schedule)?.coversDay(day) == true } }.toSet()
    }
    val createAt = { at: LocalDateTime -> draft = Schedule(at.toLocalDate(), at.toLocalTime(), at.plusHours(1).toLocalDate(), at.plusHours(1).toLocalTime()) }
    val openUnscheduled = { timeline.unscheduledOpen = true }

    Column(Modifier.fillMaxSize()) {
        TimelineHeader(
            scale = scale,
            grid = timeline.grid,
            title = rangeTitle(scale, days),
            showToday = if (scale == TimelineScale.Day) anchor != config.today else config.today !in days,
            onScale = { newScale, grid -> timeline.scale = newScale; timeline.grid = grid },
            onToday = { timeline.anchor = config.today },
            onPrevious = { timeline.anchor = stepped(scale, anchor, -1, config.calendar) },
            onNext = { timeline.anchor = stepped(scale, anchor, 1, config.calendar) },
        )
        if (scale == TimelineScale.Day) WeekStrip(anchor, week, busy, onPick = { timeline.anchor = it })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                scale == TimelineScale.Day && !timeline.grid -> DayAgenda(
                    day = days.single(),
                    items = items,
                    onOpenTask = onOpenTask,
                    onEdit = onEdit,
                    onToggle = viewModel.actions::toggle,
                    onCreateAt = createAt,
                    unscheduled = unscheduled.size,
                    onUnscheduled = openUnscheduled,
                    bottomPadding = bottom,
                )
                scale == TimelineScale.Day -> DayTimeline(
                    day = days.single(),
                    items = items,
                    onOpenTask = onOpenTask,
                    onEdit = onEdit,
                    onSchedule = viewModel::schedule,
                    onCreateAt = createAt,
                    bottomPadding = bottom,
                )
                else -> PlanTimeline(
                    days = days,
                    focus = if (scale == TimelineScale.Month && anchor in days) anchor else null,
                    items = items,
                    onOpenTask = onOpenTask,
                    onEdit = onEdit,
                    onToggle = viewModel.actions::toggle,
                    onCreateOn = { day -> draft = Schedule(day, null, day, null) },
                    unscheduled = unscheduled.size,
                    onUnscheduled = openUnscheduled,
                    bottomPadding = bottom,
                )
            }
        }
    }

    if (timeline.unscheduledOpen) {
        UnscheduledSheet(
            items = unscheduled,
            target = target,
            onPick = { item -> viewModel.schedule(item.id, Schedule(target, null, target, null)) },
            onDismiss = { timeline.unscheduledOpen = false },
        )
    }
    draft?.let { schedule ->
        NewScheduledTaskDialog(
            schedule = schedule,
            onDismiss = { draft = null },
            onCreate = { title -> viewModel.addScheduled(title, schedule); draft = null },
        )
    }
}

/**
 * The timeline's whole header in one line: the scale as a chip that opens a small menu (day, day as an hour
 * grid, week, month), what is being looked at, a way back to today when away from it, and the two arrows.
 */
@Composable
private fun TimelineHeader(
    scale: TimelineScale,
    grid: Boolean,
    title: String,
    showToday: Boolean,
    onScale: (TimelineScale, Boolean) -> Unit,
    onToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        Triple(TimelineScale.Day, false, stringResource(R.string.timeline_day)),
        Triple(TimelineScale.Day, true, stringResource(R.string.timeline_day) + " · " + stringResource(R.string.timeline_view_grid)),
        Triple(TimelineScale.Week, false, stringResource(R.string.timeline_week)),
        Triple(TimelineScale.Month, false, stringResource(R.string.timeline_month)),
    )
    val label = when (scale) {
        TimelineScale.Day -> stringResource(R.string.timeline_day)
        TimelineScale.Week -> stringResource(R.string.timeline_week)
        TimelineScale.Month -> stringResource(R.string.timeline_month)
    }
    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(if (scale == TimelineScale.Day && grid) label + " · " + stringResource(R.string.timeline_view_grid) else label) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (optionScale, optionGrid, text) ->
                    val selected = optionScale == scale && (optionScale != TimelineScale.Day || optionGrid == grid)
                    DropdownMenuItem(
                        text = { Text(text) },
                        trailingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                        onClick = { onScale(optionScale, optionGrid); open = false },
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (showToday) {
            IconButton(onClick = onToday) { Icon(Icons.Rounded.Today, stringResource(R.string.timeline_to_today)) }
        }
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.timeline_prev)) }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.timeline_next)) }
    }
}

/** Tasks with no dates yet; tapping one puts it on the day being looked at. */
@Composable
private fun UnscheduledSheet(items: List<TaskItem>, target: LocalDate, onPick: (TaskItem) -> Unit, onDismiss: () -> Unit) {
    val config = LocalUiConfig.current
    val day = CalendarText.date(target, config.calendar, config.persian, config.today)
    PickerSheet(stringResource(R.string.timeline_schedule_on, day), onDismiss) {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.timeline_unscheduled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items.forEachIndexed { index, item ->
            OptionItem(
                label = item.task.title,
                selected = false,
                index = index,
                count = items.size,
                onClick = { onPick(item) },
                icon = TaskIcons.status(item.task.status),
            )
        }
    }
}

@Composable
private fun NewScheduledTaskDialog(schedule: Schedule, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val config = LocalUiConfig.current
    var title by rememberSaveable { mutableStateOf("") }
    val day = schedule.startDate?.let { CalendarText.date(it, config.calendar, config.persian, config.today) }.orEmpty()
    val time = schedule.startTime?.let { from ->
        val to = schedule.dueTime
        " " + CalendarText.time(from, config.persian) + (to?.let { "–" + CalendarText.time(it, config.persian) } ?: "")
    }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_new_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(day + time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.timeline_new_hint)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title) }, enabled = title.isNotBlank()) { Text(stringResource(UiR.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

