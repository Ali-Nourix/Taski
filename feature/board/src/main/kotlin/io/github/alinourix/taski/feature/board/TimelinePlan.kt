package io.github.alinourix.taski.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.schedule.PlanRow
import io.github.alinourix.taski.core.domain.schedule.PlanSlot
import io.github.alinourix.taski.core.domain.schedule.Rail
import io.github.alinourix.taski.core.domain.schedule.SchedulePlan
import io.github.alinourix.taski.core.domain.schedule.SlotKind
import io.github.alinourix.taski.core.domain.schedule.Span
import io.github.alinourix.taski.core.domain.schedule.SpanKind
import io.github.alinourix.taski.core.domain.schedule.TaskSchedule
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.TaskCheckbox
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val RailStep = 12.dp
private val RailStroke = 6.dp
private val DateColumn = 76.dp
private val RailCapY = 44.dp

/**
 * A week or a month as the days that matter, and big: each day something begins or ends is a section
 * with its date in a large badge and its tasks as large tonal cards; the stretches between fold into "3 free
 * days". A task over several days is a card with a ribbon — its first day and its last, each a button that
 * opens its picker, a wavy line for how far along it is — and a rail down the side that ties its days together.
 */
@Composable
internal fun PlanTimeline(
    days: List<LocalDate>,
    focus: LocalDate?,
    items: List<TaskItem>,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onCreateOn: (LocalDate) -> Unit,
    unscheduled: Int,
    onUnscheduled: () -> Unit,
    bottomPadding: Dp,
) {
    val config = LocalUiConfig.current
    val today = config.today
    val byId = remember(items) { items.associateBy { it.id } }
    val rows = remember(items, days, today) {
        val spans = items.mapNotNull { item -> TaskSchedule.spanOf(item.task.schedule)?.let { item.id to it } }
        SchedulePlan.build(spans, days.first(), days.size, today)
    }
    val railColors = rows.flatMap { it.rails }.map { it.id }.distinct().associateWith { id -> byId[id]?.let { taskRoles(it).accent } ?: MaterialTheme.colorScheme.outline }
    val laneCount = (rows.flatMap { it.rails }.maxOfOrNull { it.lane } ?: -1) + 1
    val gutter = if (laneCount == 0) 8.dp else 8.dp + RailStep * laneCount + 4.dp

    val listState = rememberLazyListState()
    // A week opens at its start; a long month opens at the day in focus, with the row before it in view.
    LaunchedEffect(days.first(), days.size, focus) {
        val index = focus?.let { day -> rows.indexOfFirst { it is PlanRow.Day && it.date == day } } ?: -1
        listState.scrollToItem(if (index > 1) index - 1 else 0)
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)) {
        items(rows, key = { row -> if (row is PlanRow.Day) "d${row.date.toEpochDay()}" else "q${(row as PlanRow.Quiet).from.toEpochDay()}" }) { row ->
            when (row) {
                is PlanRow.Day -> DaySection(row, byId, railColors, gutter, today, onOpenTask, onEdit, onToggle, onCreateOn)
                is PlanRow.Quiet -> QuietSection(row, railColors, gutter, onCreateOn)
            }
        }
        item(key = "unscheduled") { UnscheduledFooter(unscheduled, onUnscheduled) }
    }
}

private fun Modifier.rails(rails: List<Rail>, colors: Map<String, Color>): Modifier = drawBehind {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val step = RailStep.toPx()
    val stroke = RailStroke.toPx()
    for (rail in rails) {
        val fromStart = 8.dp.toPx() + step * rail.lane + step / 2
        val x = if (rtl) size.width - fromStart else fromStart
        val top = if (rail.begins) RailCapY.toPx() else 0f
        val bottom = if (rail.ends) RailCapY.toPx() else size.height
        if (bottom > top) drawLine(colors[rail.id] ?: Color.Gray, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
    }
}

@Composable
private fun DaySection(
    row: PlanRow.Day,
    byId: Map<String, TaskItem>,
    railColors: Map<String, Color>,
    gutter: Dp,
    today: LocalDate,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onCreateOn: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth().rails(row.rails, railColors)) {
        Spacer(Modifier.width(gutter))
        DateBadge(row.date, isToday = row.date == today, onClick = { onCreateOn(row.date) })
        Column(Modifier.weight(1f).padding(end = 16.dp, top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (row.slots.isEmpty()) {
                EmptyDay(onClick = { onCreateOn(row.date) })
            }
            row.slots.forEach { slot ->
                val item = byId[slot.id] ?: return@forEach
                when (slot.kind) {
                    SlotKind.Single, SlotKind.Start -> PlanCard(slot, item, today, onOpenTask, onEdit, onToggle)
                    SlotKind.End -> EndPill(slot, item, onOpenTask, onEdit, onToggle)
                    SlotKind.Continue -> ContinuePill(slot, item, onOpenTask, onEdit)
                }
            }
        }
    }
}

@Composable
private fun QuietSection(row: PlanRow.Quiet, railColors: Map<String, Color>, gutter: Dp, onCreateOn: (LocalDate) -> Unit) {
    val persian = LocalUiConfig.current.persian
    val label = (if (row.rails.isEmpty()) pluralStringResource(R.plurals.timeline_free_days, row.days, row.days)
    else pluralStringResource(R.plurals.timeline_days, row.days, row.days)).localizeDigits(persian)
    Row(Modifier.fillMaxWidth().rails(row.rails, railColors).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(gutter))
        Spacer(Modifier.width(DateColumn))
        Surface(
            onClick = { onCreateOn(row.from) },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(vertical = 6.dp).semantics { contentDescription = label },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** The date, large: today's is a cookie in the accent colour, the others a quiet circle. */
@Composable
private fun DateBadge(day: LocalDate, isToday: Boolean, onClick: () -> Unit) {
    val config = LocalUiConfig.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (isToday) MaterialShapes.Cookie12Sided.toShape() else CircleShape
    val weekday = CalendarText.weekdayLong(day.dayOfWeek, config.persian).let { if (config.persian) it else it.take(3) }
    val addHere = stringResource(R.string.timeline_add_here)
    Column(Modifier.width(DateColumn).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(weekday, style = MaterialTheme.typography.labelLarge, color = if (isToday) scheme.primary else scheme.onSurfaceVariant, maxLines = 1)
        Box(
            Modifier
                .size(58.dp)
                .clip(shape)
                .background(if (isToday) scheme.primary else scheme.surfaceContainerHigh)
                .clickable(onClickLabel = addHere, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                CalendarText.dayOfMonth(day, config.calendar).toString().localizeDigits(config.persian),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = if (isToday) scheme.onPrimary else scheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyDay(onClick: () -> Unit) {
    val add = stringResource(R.string.timeline_add_here)
    MorphCard(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, restRadius = 24.dp, pressedRadius = 12.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp).semantics { contentDescription = add }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_nothing_today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(
    slot: PlanSlot,
    item: TaskItem,
    today: LocalDate,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    onToggle: (TaskItem) -> Unit,
) {
    val roles = taskRoles(item)
    val task = item.task
    val done = task.status == TaskStatus.Done
    val edit = onEdit(item.id)
    MorphCard(onClick = { onOpenTask(item.id) }, onLongClick = { edit(TaskProperty.Menu) }, color = roles.container) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = roles.onContainer,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                    WhenLine(slot.span, roles.onContainer.copy(alpha = 0.78f))
                }
                TaskCheckbox(task.status, onToggle = { onToggle(item) }, priority = task.priority, onLongPress = { edit(TaskProperty.Status) }, size = 28.dp)
            }
            if (slot.kind == SlotKind.Start) {
                Ribbon(slot.span, today, roles.accent, roles.onContainer, onEditStart = { edit(TaskProperty.Start) }, onEditEnd = { edit(TaskProperty.Due) })
            }
        }
    }
}

/** When, in words: `09:00–10:30`, `All day`, `Due 14:30`, or how many days it runs. */
@Composable
private fun WhenLine(span: Span, color: Color) {
    val config = LocalUiConfig.current
    val text = when {
        span.kind == SpanKind.Deadline -> stringResource(R.string.timeline_due) + if (span.timed) " " + CalendarText.time(span.end.toLocalTime(), config.persian) else ""
        span.dayCount > 1 -> pluralStringResource(R.plurals.timeline_days, span.dayCount, span.dayCount).localizeDigits(config.persian)
        span.timed -> CalendarText.time(span.start.toLocalTime(), config.persian) + "–" + CalendarText.time(span.end.toLocalTime(), config.persian)
        else -> stringResource(R.string.timeline_all_day)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (span.kind == SpanKind.Deadline) TaskIcons.Due else Icons.Rounded.Schedule, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/**
 * From where to where: the first day and the last as two big buttons, and between them a wavy
 * line that fills as the days pass. Tapping an end opens its picker, so the range is edited on the
 * thing that shows it.
 */
@Composable
private fun Ribbon(span: Span, today: LocalDate, accent: Color, onContainer: Color, onEditStart: () -> Unit, onEditEnd: () -> Unit) {
    val config = LocalUiConfig.current
    val elapsed = when {
        today.isBefore(span.firstDay) -> 0f
        today.isAfter(span.lastDay) -> 1f
        else -> (ChronoUnit.DAYS.between(span.firstDay, today).toInt() + 1).toFloat() / span.dayCount
    }
    fun label(date: LocalDate, time: java.time.LocalTime?) = CalendarText.date(date, config.calendar, config.persian, today) + (time?.let { " " + CalendarText.time(it, config.persian) } ?: "")
    val startText = label(span.firstDay, span.start.toLocalTime().takeIf { span.timed })
    val endText = label(span.lastDay, span.end.toLocalTime().takeIf { span.timed && it != java.time.LocalTime.MIN })
    val startCd = stringResource(R.string.timeline_handle_start)
    val endCd = stringResource(R.string.timeline_handle_end)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EndCap(startText, onContainer, onEditStart, Modifier.testTag("range-start").semantics { contentDescription = "$startCd: $startText" })
        LinearWavyProgressIndicator(
            progress = { elapsed },
            modifier = Modifier.weight(1f),
            color = accent,
            trackColor = onContainer.copy(alpha = 0.18f),
            amplitude = { if (it > 0f && it < 1f) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f },
        )
        EndCap(endText, onContainer, onEditEnd, Modifier.testTag("range-end").semantics { contentDescription = "$endCd: $endText" })
    }
}

@Composable
private fun EndCap(text: String, onContainer: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = onContainer.copy(alpha = 0.10f), contentColor = onContainer, modifier = modifier.minimumInteractiveComponentSize()) {
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** The last day of a several-day task: a compact reminder that it is due here. */
@Composable
private fun EndPill(slot: PlanSlot, item: TaskItem, onOpenTask: (String) -> Unit, onEdit: (String) -> (TaskProperty) -> Unit, onToggle: (TaskItem) -> Unit) {
    val roles = taskRoles(item)
    val config = LocalUiConfig.current
    val edit = onEdit(item.id)
    val due = stringResource(R.string.timeline_due) + if (slot.span.timed) " " + CalendarText.time(slot.span.end.toLocalTime(), config.persian) else ""
    MorphCard(onClick = { onOpenTask(item.id) }, onLongClick = { edit(TaskProperty.Menu) }, color = roles.container, restRadius = 24.dp, pressedRadius = 12.dp) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(TaskIcons.Due, null, tint = roles.accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = roles.onContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.task.status == TaskStatus.Done) TextDecoration.LineThrough else null,
                )
                Text(due, style = MaterialTheme.typography.labelLarge, color = roles.onContainer.copy(alpha = 0.75f))
            }
            TaskCheckbox(item.task.status, onToggle = { onToggle(item) }, priority = item.task.priority, onLongPress = { edit(TaskProperty.Status) }, size = 24.dp)
        }
    }
}

/** A several-day task passing through a day that is shown anyway. */
@Composable
private fun ContinuePill(slot: PlanSlot, item: TaskItem, onOpenTask: (String) -> Unit, onEdit: (String) -> (TaskProperty) -> Unit) {
    val roles = taskRoles(item)
    val config = LocalUiConfig.current
    val progress = stringResource(R.string.timeline_day_of, slot.dayNumber, slot.span.dayCount).localizeDigits(config.persian)
    MorphCard(
        onClick = { onOpenTask(item.id) },
        onLongClick = { onEdit(item.id)(TaskProperty.Menu) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        restRadius = 22.dp,
        pressedRadius = 11.dp,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(roles.accent))
            Text(item.task.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(progress, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}

/** The tasks with no date, offered where the plan ends rather than in a control that is always on screen. */
@Composable
internal fun UnscheduledFooter(count: Int, onClick: () -> Unit) {
    if (count == 0) return
    val persian = LocalUiConfig.current.persian
    MorphCard(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, restRadius = 24.dp, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pluralStringResource(R.plurals.timeline_unscheduled_count, count, count).localizeDigits(persian),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
