package io.github.alinourix.taski.feature.board

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.schedule.Schedule
import io.github.alinourix.taski.core.domain.schedule.Span
import io.github.alinourix.taski.core.domain.schedule.SpanKind
import io.github.alinourix.taski.core.domain.schedule.TaskSchedule
import io.github.alinourix.taski.core.ui.LocalClock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.theme.roles
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val HourHeight = 88.dp
private val Gutter = 60.dp
private const val SnapMinutes = 15

/** A project's colour when it has one, quiet grey when the task is done, the accent otherwise. */
@Composable
internal fun taskRoles(item: TaskItem): AccentRoles {
    val scheme = MaterialTheme.colorScheme
    val project = item.project?.color?.roles()
    return when {
        item.task.status == TaskStatus.Done -> AccentRoles(scheme.outline, scheme.surface, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        project != null -> project
        else -> AccentRoles(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}

private data class Block(val item: TaskItem, val span: Span, val minutes: IntRange)

/**
 * One day as hours, big and only where it matters: the grid opens an hour before the first thing and
 * closes two after the last, with "earlier" and "later" a tap away. Tasks with times are blocks placed
 * where they are, side by side when they overlap; press one and drag it to move it, drag its bottom edge to
 * change when it ends, tap the empty grid to add a task there. Tasks with only a date sit above, all day.
 */
@Composable
internal fun DayTimeline(
    day: LocalDate,
    items: List<TaskItem>,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    onSchedule: (String, Schedule) -> Unit,
    onCreateAt: (LocalDateTime) -> Unit,
    bottomPadding: Dp,
) {
    val config = LocalUiConfig.current
    val clock = LocalClock.current
    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    val entries = remember(items, day) {
        items.mapNotNull { item -> TaskSchedule.spanOf(item.task.schedule)?.takeIf { it.coversDay(day) }?.let { item to it } }
    }
    val allDay = entries.filter { !it.second.timed }
    val blocks = remember(entries) {
        entries.filter { it.second.timed }.mapNotNull { (item, span) -> TaskSchedule.minutesOn(span, day)?.let { Block(item, span, it) } }
    }
    val columns = remember(blocks) { TaskSchedule.columns(blocks.map { it.minutes }) }
    val scroll = rememberScrollState()
    val nowMinute = if (day == config.today) Instant.ofEpochMilli(clock.nowMillis()).atZone(config.zone).toLocalTime().minuteOfDay() else null
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    var earlier by rememberSaveable(day) { mutableIntStateOf(0) }
    var later by rememberSaveable(day) { mutableIntStateOf(0) }
    val baseFrom = blocks.minOfOrNull { it.minutes.first / 60 - 1 } ?: 8
    val baseTo = blocks.maxOfOrNull { (it.minutes.last + 1 + 59) / 60 + 2 } ?: 18
    val fromHour = (baseFrom - earlier).coerceIn(0, 23)
    val toHour = max(baseTo + later, fromHour + 6).coerceAtMost(24)
    val hours = toHour - fromHour
    val gridHeight = HourHeight * hours
    fun yOf(minute: Int): Dp = HourHeight * ((minute - fromHour * 60) / 60f)

    // With nothing on the day, open near now rather than at the top of the morning.
    LaunchedEffect(day, fromHour, toHour, blocks.isEmpty()) {
        val target = if (blocks.isEmpty() && nowMinute != null && nowMinute in (fromHour * 60)..(toHour * 60)) {
            ((nowMinute - fromHour * 60 - 90).coerceAtLeast(0) / 60f * hourPx).roundToInt()
        } else 0
        snapshotFlow { scroll.maxValue }.first { it >= 0 }
        scroll.scrollTo(target.coerceAtMost(scroll.maxValue))
    }

    Column(Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) AllDayStrip(allDay, onOpenTask, onEdit)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = bottomPadding)) {
            if (fromHour > 0) Expander(stringResource(R.string.timeline_earlier), up = true) { earlier += 3 }
            Row(Modifier.padding(top = 10.dp)) {
                Box(Modifier.width(Gutter).height(gridHeight)) {
                    for (hour in fromHour until toHour) {
                        Text(
                            CalendarText.time(LocalTime.of(hour, 0), config.persian),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = 14.dp, y = HourHeight * (hour - fromHour) - 8.dp),
                        )
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .height(gridHeight)
                        .padding(end = 12.dp)
                        .drawBehind { for (i in 0..hours) drawLine(lineColor, Offset(0f, i * hourPx), Offset(size.width, i * hourPx), 1f) }
                        .pointerInput(day, fromHour) {
                            detectTapGestures { offset ->
                                val minute = fromHour * 60 + (offset.y / hourPx * 60).toInt() / 30 * 30
                                onCreateAt(day.atMinute(minute.coerceIn(0, TaskSchedule.MinutesPerDay - 60)))
                            }
                        },
                ) {
                    blocks.forEachIndexed { index, block ->
                        val column = columns[index]
                        val width = maxWidth / column.count
                        DayBlock(
                            block = block,
                            hourPx = hourPx,
                            modifier = Modifier.offset(x = width * column.index, y = yOf(block.minutes.first)).width(width),
                            onOpen = { onOpenTask(block.item.id) },
                            onMove = { minutes ->
                                TaskSchedule.movedByMinutes(block.item.task.schedule, minutes)?.let { onSchedule(block.item.id, it) }
                            },
                            onResize = { minutes ->
                                onSchedule(block.item.id, TaskSchedule.withEnd(block.item.task.schedule, block.span.end.plusMinutes(minutes)))
                            },
                        )
                    }
                    if (nowMinute != null && nowMinute in (fromHour * 60)..(toHour * 60)) {
                        val scheme = MaterialTheme.colorScheme
                        Box(Modifier.offset(y = yOf(nowMinute) - 1.dp).fillMaxWidth().height(2.dp).background(scheme.primary))
                        Box(Modifier.offset(x = (-6).dp, y = yOf(nowMinute) - 6.dp).size(12.dp).clip(CircleShape).background(scheme.primary))
                    }
                }
            }
            if (toHour < 24) Expander(stringResource(R.string.timeline_later), up = false) { later += 3 }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.timeline_empty_day),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Expander(label: String, up: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        AssistChip(
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = { Icon(if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null) },
        )
    }
}

@Composable
private fun AllDayStrip(entries: List<Pair<TaskItem, Span>>, onOpenTask: (String) -> Unit, onEdit: (String) -> (TaskProperty) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Text(
            stringResource(R.string.timeline_all_day),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(Gutter).padding(start = 14.dp, top = 12.dp),
        )
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { (item, span) ->
                val roles = taskRoles(item)
                MorphCard(
                    onClick = { onOpenTask(item.id) },
                    onLongClick = { onEdit(item.id)(TaskProperty.Menu) },
                    color = roles.container,
                    restRadius = 20.dp,
                    pressedRadius = 10.dp,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (span.kind == SpanKind.Deadline) TaskIcons.Due else TaskIcons.Range, null, tint = roles.accent, modifier = Modifier.size(18.dp))
                        Text(
                            item.task.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = roles.onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.task.status == TaskStatus.Done) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBlock(
    block: Block,
    hourPx: Float,
    modifier: Modifier,
    onOpen: () -> Unit,
    onMove: (Long) -> Unit,
    onResize: (Long) -> Unit,
) {
    val config = LocalUiConfig.current
    val density = LocalDensity.current
    val roles = taskRoles(block.item)
    val snap = hourPx * SnapMinutes / 60f
    var moveDy by remember { mutableFloatStateOf(0f) }
    var resizeDy by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    // The preview stays where it was dropped until the saved position arrives.
    LaunchedEffect(block.minutes, block.span) { moveDy = 0f; resizeDy = 0f }

    val movePx = (moveDy / snap).roundToInt() * snap
    val resizePx = (resizeDy / snap).roundToInt() * snap
    val baseHeightPx = (block.minutes.last + 1 - block.minutes.first) / 60f * hourPx
    val heightPx = (baseHeightPx + resizePx).coerceAtLeast(snap * 2)
    val height = with(density) { heightPx.toDp() }
    val dragging = moveDy != 0f || resizeDy != 0f
    val radius by animateDpAsState(if (pressed || dragging) 10.dp else 22.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "radius")
    val span = block.span
    val range = if (span.kind == SpanKind.Deadline) CalendarText.time(span.end.toLocalTime(), config.persian)
    else CalendarText.time(span.start.toLocalTime(), config.persian) + "–" + CalendarText.time(span.end.toLocalTime(), config.persian)
    val handle = stringResource(R.string.timeline_handle_end)

    Surface(
        modifier = modifier
            .height(height)
            .graphicsLayer { translationY = movePx }
            .padding(2.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onOpen() })
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {},
                    onDrag = { change, drag -> change.consume(); moveDy += drag.y },
                    onDragEnd = {
                        val minutes = ((moveDy / snap).roundToInt() * SnapMinutes).toLong()
                        if (minutes == 0L) moveDy = 0f else onMove(minutes)
                    },
                    onDragCancel = { moveDy = 0f },
                )
            },
        shape = RoundedCornerShape(radius),
        color = roles.container,
        contentColor = roles.onContainer,
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.padding(vertical = 10.dp, horizontal = 8.dp).width(5.dp).fillMaxHeight().clip(CircleShape).background(roles.accent))
                Column(Modifier.weight(1f).padding(end = 12.dp, top = 12.dp, bottom = if (span.kind == SpanKind.Range) 22.dp else 12.dp)) {
                    Text(
                        block.item.task.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = if (height >= 120.dp) 3 else if (height >= 84.dp) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (block.item.task.status == TaskStatus.Done) TextDecoration.LineThrough else null,
                    )
                    if (height >= 72.dp) {
                        Text(range, style = MaterialTheme.typography.labelLarge, color = roles.onContainer.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
            if (span.kind == SpanKind.Range) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .semantics { contentDescription = handle }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val minutes = ((resizeDy / snap).roundToInt() * SnapMinutes).toLong()
                                    if (minutes == 0L) resizeDy = 0f else onResize(minutes)
                                },
                                onDragCancel = { resizeDy = 0f },
                            ) { change, dy -> change.consume(); resizeDy += dy }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(roles.onContainer.copy(alpha = 0.35f)))
                }
            }
        }
    }
}
