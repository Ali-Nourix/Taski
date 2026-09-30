package io.github.alinourix.taski.feature.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.schedule.Span
import io.github.alinourix.taski.core.domain.schedule.SpanKind
import io.github.alinourix.taski.core.domain.schedule.TaskSchedule
import io.github.alinourix.taski.core.ui.LocalClock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.TaskCheckbox
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max

private val TimeColumn = 72.dp
private val RailColumn = 52.dp
private const val GapMinutes = 60

private data class Entry(val item: TaskItem, val span: Span, val minutes: IntRange)

private enum class Period(val icon: ImageVector) {
    Morning(Icons.Rounded.WbSunny),
    Afternoon(Icons.Rounded.LightMode),
    Evening(Icons.Rounded.Bedtime),
}

private fun periodOf(minute: Int) = when {
    minute < 12 * 60 -> Period.Morning
    minute < 17 * 60 -> Period.Afternoon
    else -> Period.Evening
}

private sealed interface AgendaRow {
    data class Header(val period: Period, val count: Int) : AgendaRow
    data class Item(val entry: Entry) : AgendaRow
    data class Gap(val from: Int, val to: Int) : AgendaRow
    data class Now(val minute: Int) : AgendaRow
}

/** The day in order: a header when the part of the day changes, a gap where an hour or more is free, a marker for now. */
private fun agendaRows(entries: List<Entry>, nowMinute: Int?): List<AgendaRow> {
    val sorted = entries.sortedWith(compareBy({ it.minutes.first }, { it.item.id }))
    val counts = sorted.groupingBy { periodOf(it.minutes.first) }.eachCount()
    val rows = mutableListOf<AgendaRow>()
    var lastPeriod: Period? = null
    var busyUntil = -1
    var nowPlaced = nowMinute == null
    for (entry in sorted) {
        val start = entry.minutes.first
        if (!nowPlaced && nowMinute!! < start) {
            rows += AgendaRow.Now(nowMinute)
            nowPlaced = true
        }
        if (busyUntil >= 0 && start - busyUntil >= GapMinutes) rows += AgendaRow.Gap(busyUntil, start)
        val period = periodOf(start)
        if (period != lastPeriod) {
            rows += AgendaRow.Header(period, counts.getValue(period))
            lastPeriod = period
        }
        rows += AgendaRow.Item(entry)
        busyUntil = max(busyUntil, entry.minutes.last + 1)
    }
    if (!nowPlaced) rows += AgendaRow.Now(nowMinute!!)
    return rows
}

private fun Int.asTime(): LocalTime = LocalTime.of((this / 60) % 24, this % 60)

@Composable
internal fun durationLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    val h = if (hours > 0) stringResource(R.string.duration_h, hours) else null
    val m = if (rest > 0 || hours == 0) stringResource(R.string.duration_m, rest) else null
    val text = when {
        h != null && m != null -> stringResource(R.string.duration_join, h, m)
        else -> h ?: m.orEmpty()
    }
    return text.localizeDigits(LocalUiConfig.current.persian)
}

/** A vertical line that runs through every row, so the day reads as one rail. */
private fun Modifier.agendaRail(): Modifier = drawBehind {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val fromStart = (16.dp + TimeColumn - 16.dp + RailColumn / 2).toPx()
    val x = if (rtl) size.width - fromStart else fromStart
    drawLine(RailColor, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round)
}

private val RailColor = Color(0x33808080)

/**
 * One day as an agenda on a rail — Tiimo's and Structured's idea in Material 3 Expressive shapes: no empty hours,
 * only what is planned, in order, each on a pill whose height is how long it takes. Where an hour or more is
 * free there is a tappable "3h free" to fill it; the part of the day changes under a soft header; now is a marker
 * on the rail. The times at the left are buttons that open the start and the end.
 */
@Composable
internal fun DayAgenda(
    day: LocalDate,
    items: List<TaskItem>,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onCreateAt: (LocalDateTime) -> Unit,
    bottomPadding: Dp,
) {
    val config = LocalUiConfig.current
    val clock = LocalClock.current
    val spans = remember(items, day) {
        items.mapNotNull { item -> TaskSchedule.spanOf(item.task.schedule)?.takeIf { it.coversDay(day) }?.let { item to it } }
    }
    val allDay = spans.filter { !it.second.timed }
    val entries = remember(spans) { spans.filter { it.second.timed }.mapNotNull { (item, span) -> TaskSchedule.minutesOn(span, day)?.let { Entry(item, span, it) } } }
    val nowMinute = if (day == config.today) Instant.ofEpochMilli(clock.nowMillis()).atZone(config.zone).toLocalTime().minuteOfDay() else null
    val rows = remember(entries, nowMinute) { agendaRows(entries, nowMinute) }

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)) {
        if (allDay.isNotEmpty()) item(key = "all-day") { AllDayStrip(allDay, onOpenTask, onEdit) }
        if (rows.isEmpty() && allDay.isEmpty()) {
            item(key = "empty") { EmptyAgenda(onClick = { onCreateAt(day.atMinute(nowMinute?.let { (it / 30 + 1) * 30 } ?: (9 * 60))) }) }
        }
        items(rows, key = { row ->
            when (row) {
                is AgendaRow.Header -> "h${row.period.name}"
                is AgendaRow.Item -> "i${row.entry.item.id}"
                is AgendaRow.Gap -> "g${row.from}"
                is AgendaRow.Now -> "now"
            }
        }) { row ->
            when (row) {
                is AgendaRow.Header -> PeriodHeader(row)
                is AgendaRow.Item -> AgendaItem(row.entry, onOpenTask, onEdit, onToggle)
                is AgendaRow.Gap -> GapRow(row, onClick = { onCreateAt(day.atMinute(row.from)) })
                is AgendaRow.Now -> NowRow(row.minute)
            }
        }
    }
}

@Composable
private fun EmptyAgenda(onClick: () -> Unit) {
    MorphCard(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Text(
                stringResource(R.string.timeline_empty_day),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PeriodHeader(row: AgendaRow.Header) {
    val label = stringResource(
        when (row.period) {
            Period.Morning -> R.string.timeline_morning
            Period.Afternoon -> R.string.timeline_afternoon
            Period.Evening -> R.string.timeline_evening
        },
    )
    val persian = LocalUiConfig.current.persian
    Row(Modifier.fillMaxWidth().agendaRail().padding(start = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(row.period.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(row.count.toString().localizeDigits(persian), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AgendaItem(entry: Entry, onOpenTask: (String) -> Unit, onEdit: (String) -> (TaskProperty) -> Unit, onToggle: (TaskItem) -> Unit) {
    val config = LocalUiConfig.current
    val item = entry.item
    val task = item.task
    val roles = taskRoles(item)
    val edit = onEdit(item.id)
    val scheme = MaterialTheme.colorScheme
    val deadline = entry.span.kind == SpanKind.Deadline
    val minutes = entry.minutes.last + 1 - entry.minutes.first
    val pill = (minutes / 60f * 36f).dp.coerceIn(48.dp, 132.dp)
    val start = CalendarText.time(entry.minutes.first.asTime(), config.persian)
    val end = CalendarText.time((entry.minutes.last + 1).asTime(), config.persian)
    val done = task.status == TaskStatus.Done

    Row(Modifier.fillMaxWidth().agendaRail().padding(end = 16.dp).heightIn(min = pill + 20.dp)) {
        Column(Modifier.width(TimeColumn).padding(start = 16.dp, top = 12.dp)) {
            if (deadline) {
                Text(stringResource(R.string.timeline_due), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(CalendarText.time(entry.span.end.toLocalTime(), config.persian), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.clickable { edit(TaskProperty.Due) })
            } else {
                Text(start, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.clickable { edit(TaskProperty.Start) })
                Text(end, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.clickable { edit(TaskProperty.Due) })
            }
        }
        Box(Modifier.width(RailColumn).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(40.dp).height(pill).clip(RoundedCornerShape(20.dp)).background(roles.accent),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    val letter = item.project?.name?.trim()?.take(1)?.uppercase()
                    if (letter != null && !done) {
                        Text(letter, style = MaterialTheme.typography.titleMedium, color = roles.onAccent)
                    } else {
                        Icon(if (deadline) TaskIcons.Due else TaskIcons.status(task.status), null, tint = roles.onAccent, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        MorphCard(
            onClick = { onOpenTask(item.id) },
            onLongClick = { edit(TaskProperty.Menu) },
            color = roles.container,
            restRadius = 24.dp,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        ) {
            Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = roles.onContainer,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                    if (!deadline) {
                        Text(durationLabel(minutes), style = MaterialTheme.typography.labelLarge, color = roles.onContainer.copy(alpha = 0.75f))
                    }
                }
                TaskCheckbox(task.status, onToggle = { onToggle(item) }, priority = task.priority, onLongPress = { edit(TaskProperty.Status) }, size = 26.dp)
            }
        }
    }
}

@Composable
private fun GapRow(row: AgendaRow.Gap, onClick: () -> Unit) {
    val free = stringResource(R.string.timeline_free, durationLabel(row.to - row.from))
    Row(Modifier.fillMaxWidth().agendaRail().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(TimeColumn + RailColumn))
        Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(free, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NowRow(minute: Int) {
    val config = LocalUiConfig.current
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().agendaRail().padding(end = 16.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(TimeColumn).padding(start = 16.dp)) {
            Text(stringResource(R.string.timeline_now), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primary)
            Text(CalendarText.time(minute.asTime(), config.persian), style = MaterialTheme.typography.labelSmall, color = primary)
        }
        Box(Modifier.width(RailColumn), contentAlignment = Alignment.Center) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(primary.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(primary))
            }
        }
        Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.45f)))
    }
}

/** The day's own header, Tiimo's way: the weekday large, then the week as a strip to jump along. */
@Composable
internal fun DayHeader(
    anchor: LocalDate,
    week: List<LocalDate>,
    busy: Set<LocalDate>,
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
    onPick: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val config = LocalUiConfig.current
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CalendarText.weekdayLong(anchor.dayOfWeek, config.persian), style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 1)
                Text(
                    CalendarText.date(anchor, config.calendar, config.persian, null),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            ViewToggle(grid, onGrid)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousWeek, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.timeline_prev_week), modifier = Modifier.size(20.dp))
            }
            week.forEach { day ->
                StripDay(day, selected = day == anchor, today = day == config.today, busy = day in busy, onClick = { onPick(day) }, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onNextWeek, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.timeline_next_week), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StripDay(day: LocalDate, selected: Boolean, today: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val config = LocalUiConfig.current
    val scheme = MaterialTheme.colorScheme
    val weekday = CalendarText.weekdayLong(day.dayOfWeek, config.persian).let { it.take(1) }
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(weekday, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.primary else Color.Transparent)
                .then(if (today && !selected) Modifier.background(scheme.primaryContainer, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                CalendarText.dayOfMonth(day, config.calendar).toString().localizeDigits(config.persian),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    selected -> scheme.onPrimary
                    today -> scheme.onPrimaryContainer
                    else -> scheme.onSurface
                },
            )
        }
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (busy) scheme.primary.copy(alpha = 0.6f) else Color.Transparent))
    }
}

/** Agenda or hour grid: two icons, the chosen one filled. */
@Composable
private fun ViewToggle(grid: Boolean, onGrid: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val agenda = stringResource(R.string.timeline_view_agenda)
    val hours = stringResource(R.string.timeline_view_grid)
    Row(Modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh).padding(3.dp)) {
        listOf(false to agenda, true to hours).forEach { (isGrid, label) ->
            val selected = isGrid == grid
            Box(
                Modifier
                    .size(width = 46.dp, height = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) scheme.primaryContainer else Color.Transparent)
                    .clickable(onClickLabel = label) { onGrid(isGrid) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isGrid) Icons.Rounded.CalendarViewDay else Icons.Rounded.Timeline,
                    null,
                    tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
