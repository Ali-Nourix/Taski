package io.github.alinourix.taski.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.reminderLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.picker.DatePickerSheet
import io.github.alinourix.taski.core.ui.picker.PickerSheet
import io.github.alinourix.taski.core.ui.picker.PrioritySheet
import io.github.alinourix.taski.core.ui.picker.ProjectPickerSheet
import io.github.alinourix.taski.core.ui.picker.ReminderSheet
import io.github.alinourix.taski.core.ui.picker.RepeatSheet
import io.github.alinourix.taski.core.ui.picker.StatusSheet
import io.github.alinourix.taski.core.ui.picker.TagPickerSheet
import io.github.alinourix.taski.core.ui.theme.roles
import kotlinx.coroutines.launch

/** A property of a task that can be edited where it is shown. */
enum class TaskProperty { Menu, Status, Due, Priority, Repeat, Reminder, Tags, Project }

/** Which task's which property is being edited in place, if any. */
@Stable
class TaskSheetState {
    var target by mutableStateOf<Pair<String, TaskProperty>?>(null)
        private set

    fun open(taskId: String, property: TaskProperty) {
        target = taskId to property
    }

    fun close() {
        target = null
    }
}

@Composable
fun rememberTaskSheetState(): TaskSheetState = rememberSaveable(saver = androidx.compose.runtime.saveable.Saver(
    save = { it.target?.let { (id, p) -> "$id|${p.name}" } },
    restore = { saved -> TaskSheetState().apply { saved.split('|').takeIf { it.size == 2 }?.let { open(it[0], TaskProperty.valueOf(it[1])) } } },
)) { TaskSheetState() }

/**
 * Shows the picker for the property being edited in [state] — the same
 * pickers the task page uses — or, for [TaskProperty.Menu], the task sheet:
 * the plugin's status-chip menu, with every property, the capabilities, and
 * the task's actions in one place.
 */
@Composable
fun TaskSheetHost(
    state: TaskSheetState,
    item: (String) -> TaskItem?,
    tags: List<Tag>,
    projects: List<Project>,
    actions: TaskActions,
    onOpenTask: (String) -> Unit,
) {
    val (id, property) = state.target ?: return
    val task = item(id)
    if (task == null) {
        state.close()
        return
    }
    val t = task.task
    val close = state::close
    when (property) {
        TaskProperty.Status -> StatusSheet(t.status, onDismiss = close) { actions.setStatus(task, it); close() }
        TaskProperty.Due -> DatePickerSheet(t.dueDate, t.dueTime, onDismiss = close) { date, time ->
            actions.edit(id, TaskEdit.Due(date, time)); close()
        }
        TaskProperty.Priority -> PrioritySheet(t.priority, onDismiss = close) { actions.edit(id, TaskEdit.SetPriority(it)); close() }
        TaskProperty.Repeat -> RepeatSheet(t.repeat, onDismiss = close) { actions.edit(id, TaskEdit.Repeat(it)); close() }
        TaskProperty.Reminder -> ReminderSheet(t.reminderOffsetMinutes, onDismiss = close) { actions.edit(id, TaskEdit.Reminder(it)); close() }
        TaskProperty.Project -> ProjectPickerSheet(projects, t.projectId, onDismiss = close) { actions.edit(id, TaskEdit.MoveToProject(it)); close() }
        TaskProperty.Tags -> TagPickerSheet(
            tags = tags,
            selected = task.tags.map { it.id }.toSet(),
            onToggle = { actions.toggleTag(task, it) },
            onCreate = { name, color -> actions.createTag(task, name, color) },
            onDismiss = close,
        )
        TaskProperty.Menu -> TaskMenuSheet(task, actions, onEdit = { state.open(id, it) }, onOpenTask = { close(); onOpenTask(id) }, onDismiss = close)
    }
}

@Composable
private fun TaskMenuSheet(
    item: TaskItem,
    actions: TaskActions,
    onEdit: (TaskProperty) -> Unit,
    onOpenTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    val task = item.task
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.task_copied)
    PickerSheet(task.title, onDismiss) {
        val statuses = listOf(TaskStatus.NotStarted, TaskStatus.InProgress, TaskStatus.Done, TaskStatus.NotDone)
        ConnectedToggleGroup(
            options = statuses,
            selected = task.status,
            onSelect = { actions.setStatus(item, it) },
            label = { it.code },
            icon = TaskIcons::status,
            showLabels = false,
        )
        Text(
            statusLabel(task.status),
            style = MaterialTheme.typography.labelLarge,
            color = task.status.roles().accent,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 12.dp),
        )

        PropertyLine(TaskIcons.Due, stringResource(R.string.pick_date), { onEdit(TaskProperty.Due) }) {
            if (task.dueDate != null) DueChip(task) else Muted(stringResource(R.string.no_date))
        }
        PropertyLine(task.priority?.let(TaskIcons::priority) ?: TaskIcons.priority(io.github.alinourix.taski.core.domain.model.Priority.Medium),
            stringResource(R.string.pick_priority), { onEdit(TaskProperty.Priority) }) {
            if (task.priority != null) PriorityChip(task.priority!!) else Muted(priorityLabel(null))
        }
        PropertyLine(TaskIcons.Repeat, stringResource(R.string.pick_repeat), { onEdit(TaskProperty.Repeat) }) { Muted(repeatLabel(task.repeat)) }
        PropertyLine(TaskIcons.Reminder, stringResource(R.string.pick_reminder), { onEdit(TaskProperty.Reminder) }) { Muted(reminderLabel(task.reminderOffsetMinutes)) }
        PropertyLine(TaskIcons.Tag, stringResource(R.string.pick_tags), { onEdit(TaskProperty.Tags) }) {
            if (item.tags.isEmpty()) Muted("—") else FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.tags.forEach { TagChip(it) }
            }
        }
        PropertyLine(if (item.project == null) Icons.Rounded.Inbox else Icons.Rounded.Folder, stringResource(R.string.pick_project), { onEdit(TaskProperty.Project) }) {
            Muted(item.project?.name ?: stringResource(R.string.inbox))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.task_capabilities), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        if (task.progress == null) {
            ActionLine(TaskIcons.Progress, stringResource(R.string.task_add_progress)) { actions.edit(task.id, TaskEdit.Progress(StepProgress(0, 5))) }
        }
        if (task.timerMinutes == null) {
            ActionLine(TaskIcons.Timer, stringResource(R.string.task_add_timer)) { actions.edit(task.id, TaskEdit.Timer(25)) }
        }
        if (actions.canFocus) ActionLine(Icons.Rounded.PlayArrow, stringResource(R.string.task_focus)) { actions.startFocus(item); onDismiss() }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ActionLine(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.task_open), onClick = onOpenTask)
        ActionLine(Icons.Rounded.ContentCopy, stringResource(R.string.task_copy_markdown)) {
            clipboard.setText(AnnotatedString(actions.markdownLine(item)))
            scope.launch { snackbar.showSnackbar(copied) }
            onDismiss()
        }
        ActionLine(Icons.Rounded.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) { actions.delete(item); onDismiss() }
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Notion's property row: icon and label in a fixed column, the value beside it. */
@Composable
fun PropertyLine(icon: ImageVector, label: String, onClick: () -> Unit, iconTint: Color? = null, value: @Composable () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(132.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(Modifier.weight(1f)) { value() }
        }
    }
}

@Composable
private fun ActionLine(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(label, color = tint ?: MaterialTheme.colorScheme.onSurface) },
            leadingContent = { Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
