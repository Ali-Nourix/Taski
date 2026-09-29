package io.github.alinourix.taski.core.ui.component

import androidx.compose.animation.animateColorAsState
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.LocalClock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.theme.roles

/** Everything a task row can show beyond the task itself. */
data class RowExtras(
    val showProject: Boolean = false,
    /** The countdown on this device, shown as a live chip on the task it belongs to. */
    val timer: FocusTimerState? = null,
)

/**
 * One task: the checkbox, the title, and a row of the plugin's chips. The
 * chips wrap rather than truncate, so nothing a task carries is hidden.
 */
@Composable
fun TaskRow(
    item: TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onStatusMenu: (() -> Unit)? = null,
    extras: RowExtras = RowExtras(),
    shape: Shape = MaterialTheme.shapes.large,
) {
    val task = item.task
    val config = LocalUiConfig.current
    val done = task.status == TaskStatus.Done
    val titleAlpha by animateFloatAsState(if (done) 0.6f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "alpha")
    val container by animateColorAsState(
        if (task.status == TaskStatus.InProgress) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "container",
    )

    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = container) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onStatusMenu)
                .padding(start = 4.dp, end = 16.dp, top = if (config.compact) 0.dp else 4.dp, bottom = if (config.compact) 0.dp else 4.dp),
            verticalAlignment = if (config.compact) Alignment.CenterVertically else Alignment.Top,
        ) {
            TaskCheckbox(status = task.status, onToggle = onToggle, priority = task.priority, onLongPress = onStatusMenu)
            Column(
                modifier = Modifier.weight(1f).padding(top = if (config.compact) 0.dp else 12.dp, bottom = if (config.compact) 0.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (config.compact) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    modifier = Modifier.alpha(titleAlpha),
                )
                if (!config.compact) {
                    ChipRow(item, extras)
                }
            }
            if (config.compact && task.dueDate != null) {
                DueChip(task)
            }
        }
    }
}

@Composable
private fun ChipRow(item: TaskItem, extras: RowExtras) {
    val task = item.task
    val persian = LocalUiConfig.current.persian
    val hasChips = task.dueDate != null || task.priority != null || task.repeat != null || item.progressFraction != null ||
        item.tags.isNotEmpty() || item.isBlocked || extras.timer?.taskId == task.id || (extras.showProject && item.project != null)
    if (!hasChips) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        task.dueDate?.let { DueChip(task) }
        task.priority?.let { PriorityChip(it) }
        task.repeat?.let { RepeatChip(it) }
        extras.timer?.takeIf { it.taskId == task.id }?.let { LiveTimerChip(it) }
        if (item.subtaskCount > 0) {
            val scheme = MaterialTheme.colorScheme
            MetaChip(
                stringResource(R.string.subtasks_count, item.subtasksDone, item.subtaskCount).localizeDigits(persian),
                AccentRoles(scheme.onSurfaceVariant, scheme.surface, scheme.surfaceContainerHighest, scheme.onSurfaceVariant),
                icon = Icons.AutoMirrored.Rounded.List,
            )
        } else {
            item.progressFraction?.let { ProgressChip(it) }
        }
        if (item.isBlocked) BlockedChip()
        if (extras.showProject) item.project?.let { MetaChip(it.name, it.color.roles()) }
        item.tags.forEach { TagChip(it) }
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
    onStatusMenu: (() -> Unit)? = null,
    extras: RowExtras = RowExtras(),
    shape: Shape = MaterialTheme.shapes.large,
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
        modifier = modifier.clip(shape),
        backgroundContent = {
            val direction = state.dismissDirection
            val scheme = MaterialTheme.colorScheme
            val (color, icon, align) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(scheme.primaryContainer, Icons.Rounded.DoneAll, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(scheme.errorContainer, Icons.Rounded.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(scheme.surfaceContainerLow, null, Alignment.Center)
            }
            val label = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> stringResource(R.string.swipe_complete)
                SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.swipe_delete)
                SwipeToDismissBoxValue.Settled -> null
            }
            Box(Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp), contentAlignment = align) {
                if (icon != null) {
                    val scale = (state.progress * 1.4f).coerceIn(0.6f, 1.2f)
                    Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                }
            }
        },
    ) {
        TaskRow(item, onToggle, onClick, onStatusMenu = onStatusMenu, extras = extras, shape = shape)
    }
}

/** A timer chip that ticks on its own, so the list around it never recomposes per second. */
@Composable
fun LiveTimerChip(state: FocusTimerState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
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
    TimerChip(FocusTimerState.formatClock(state.remainingSeconds(now)).localizeDigits(persian), state.isRunning, modifier, onClick)
}
