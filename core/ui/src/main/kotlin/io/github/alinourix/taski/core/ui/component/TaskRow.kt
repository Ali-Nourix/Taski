package io.github.alinourix.taski.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.LocalClock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.localizeDigits
import kotlinx.coroutines.delay

/** Everything a task row can show beyond the task itself. */
data class RowExtras(
    val showProject: Boolean = false,
    /** The countdown on this device, shown as a live token on the task it belongs to. */
    val timer: FocusTimerState? = null,
)

enum class RowStyle {
    /** A line on the page, divided from the next by a hairline. */
    List,
    /** A card on a board column: white, a 1dp border, crisp corners. */
    Card,
}

/**
 * One task: the checkbox, the title, and one quiet line of properties. Each
 * property is edited where it is read — tapping the date opens the date
 * picker, the priority opens priorities, a tag opens the tag picker — and a
 * long-press opens the whole task sheet, like the plugin's chip menus.
 */
@Composable
fun TaskRow(
    item: TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: ((TaskProperty) -> Unit)? = null,
    extras: RowExtras = RowExtras(),
    style: RowStyle = RowStyle.List,
    showDivider: Boolean = true,
    /** Off on board cards, where a long-press picks the card up instead. */
    longPressMenu: Boolean = true,
    /** The container the row sits in; the row draws it itself so a swipe never shows the page behind. */
    containerColor: Color = Color.Transparent,
) {
    val task = item.task
    val menu = onEdit?.takeIf { longPressMenu }
    val config = LocalUiConfig.current
    val done = task.status == TaskStatus.Done
    val titleAlpha by animateFloatAsState(if (done) 0.55f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "alpha")

    val body = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = menu?.let { { it(TaskProperty.Menu) } })
                .padding(start = 4.dp, end = 12.dp, top = if (config.compact) 0.dp else 2.dp, bottom = if (config.compact) 0.dp else 6.dp),
            verticalAlignment = if (config.compact) Alignment.CenterVertically else Alignment.Top,
        ) {
            TaskCheckbox(
                status = task.status,
                onToggle = onToggle,
                priority = task.priority,
                onLongPress = menu?.let { { it(TaskProperty.Status) } },
                size = 22.dp,
            )
            Column(
                modifier = Modifier.weight(1f).padding(top = if (config.compact) 0.dp else 13.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (config.compact) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    modifier = Modifier.alpha(titleAlpha),
                )
                if (!config.compact) PropertyLine(item, extras, onEdit)
            }
            if (config.compact && (task.dueDate != null || task.startDate != null)) {
                DueChip(task, Modifier.padding(start = 8.dp), onClick = onEdit?.let { { it(if (task.dueDate != null) TaskProperty.Due else TaskProperty.Start) } })
            }
        }
    }

    when (style) {
        RowStyle.List -> Column(modifier.fillMaxWidth().background(containerColor)) {
            body()
            if (showDivider) HorizontalDivider(Modifier.padding(start = 52.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        // A card is one tonal step above its column: no border, the surface says it.
        RowStyle.Card -> Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceBright,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) { body() }
    }
}

@Composable
private fun PropertyLine(item: TaskItem, extras: RowExtras, onEdit: ((TaskProperty) -> Unit)?) {
    val task = item.task
    val persian = LocalUiConfig.current.persian
    fun edit(property: TaskProperty): (() -> Unit)? = onEdit?.let { { it(property) } }
    val timer = extras.timer?.takeIf { it.taskId == task.id }
    val hasAny = task.dueDate != null || task.startDate != null || task.priority != null || task.repeat != null || item.progressFraction != null ||
        item.tags.isNotEmpty() || item.isBlocked || timer != null || (extras.showProject && item.project != null)
    if (!hasAny) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (task.dueDate != null || task.startDate != null) {
            DueChip(task, onClick = edit(if (task.dueDate != null) TaskProperty.Due else TaskProperty.Start))
        }
        task.priority?.let { PriorityChip(it, onClick = edit(TaskProperty.Priority), showLabel = false) }
        task.repeat?.let { RepeatChip(it, onClick = edit(TaskProperty.Repeat), showLabel = false) }
        timer?.let { LiveTimerChip(it) }
        if (item.subtaskCount > 0) {
            PropertyToken(
                stringResource(R.string.subtasks_count, item.subtasksDone, item.subtaskCount).localizeDigits(persian),
                icon = Icons.AutoMirrored.Rounded.List,
            )
        } else {
            item.progressFraction?.let { ProgressChip(it) }
        }
        if (item.isBlocked) BlockedChip()
        if (extras.showProject) item.project?.let {
            PropertyToken(it.name, icon = Icons.Rounded.Folder, onClick = edit(TaskProperty.Project))
        }
        if (item.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.tags.forEach { TagChip(it, onClick = edit(TaskProperty.Tags)) }
            }
        }
    }
}

/**
 * A row with the two swipes every list shares: toward the end to complete,
 * toward the start to delete. The row springs back; the caller shows the
 * snackbar that can undo either.
 */
@Composable
fun SwipeableTaskRow(
    item: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: ((TaskProperty) -> Unit)? = null,
    extras: RowExtras = RowExtras(),
    showDivider: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onToggle()
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            val scheme = MaterialTheme.colorScheme
            val (color, icon, align) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(scheme.primaryContainer, Icons.Rounded.DoneAll, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(scheme.errorContainer, Icons.Rounded.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(containerColor, null, Alignment.Center)
            }
            val label = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> stringResource(R.string.swipe_complete)
                SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.swipe_delete)
                SwipeToDismissBoxValue.Settled -> null
            }
            Box(Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp), contentAlignment = align) {
                if (icon != null) {
                    val scale = (state.progress * 1.4f).coerceIn(0.6f, 1.2f)
                    Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                }
            }
        },
    ) {
        TaskRow(item, onToggle, onClick, onEdit = onEdit, extras = extras, showDivider = showDivider, containerColor = containerColor)
    }
}

/** The countdown as text that ticks on its own, so nothing around it recomposes per second. */
@Composable
fun rememberTimerLabel(state: FocusTimerState): String {
    val persian = LocalUiConfig.current.persian
    val clock = LocalClock.current
    var now by remember { mutableLongStateOf(clock.nowMillis()) }
    LaunchedEffect(state) {
        while (state.isRunning) {
            now = clock.nowMillis()
            delay(1_000 - now % 1_000)
        }
        now = clock.nowMillis()
    }
    return FocusTimerState.formatClock(state.remainingSeconds(now)).localizeDigits(persian)
}

/** A timer token that ticks on its own. */
@Composable
fun LiveTimerChip(state: FocusTimerState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    TimerChip(rememberTimerLabel(state), state.isRunning, modifier, onClick)
}
