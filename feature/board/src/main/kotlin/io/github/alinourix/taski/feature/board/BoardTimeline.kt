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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
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

    Column(Modifier.fillMaxSize()) {
        ScaleToggle(scale, onScale = { timeline.scale = it })
        if (scale == TimelineScale.Day) {
            DayHeader(
                anchor = anchor,
                week = week,
                busy = busy,
                grid = timeline.grid,
                onGrid = { timeline.grid = it },
                onPick = { timeline.anchor = it },
                onPreviousWeek = { timeline.anchor = anchor.minusDays(7) },
                onNextWeek = { timeline.anchor = anchor.plusDays(7) },
            )
        } else {
            RangeNav(
                title = rangeTitle(scale, days),
                onPrevious = { timeline.anchor = stepped(scale, anchor, -1, config.calendar) },
                onNext = { timeline.anchor = stepped(scale, anchor, 1, config.calendar) },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                scale == TimelineScale.Day && !timeline.grid -> DayAgenda(
                    day = days.single(),
                    items = items,
                    onOpenTask = onOpenTask,
                    onEdit = onEdit,
                    onToggle = viewModel.actions::toggle,
                    onCreateAt = createAt,
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

@Composable
private fun ScaleToggle(scale: TimelineScale, onScale: (TimelineScale) -> Unit) {
    val labels = mapOf(
        TimelineScale.Day to stringResource(R.string.timeline_day),
        TimelineScale.Week to stringResource(R.string.timeline_week),
        TimelineScale.Month to stringResource(R.string.timeline_month),
    )
    ConnectedToggleGroup(
        options = TimelineScale.entries,
        selected = scale,
        onSelect = onScale,
        label = labels::getValue,
        height = 40.dp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
    )
}

@Composable
private fun RangeNav(title: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.timeline_prev))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.timeline_next))
        }
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

