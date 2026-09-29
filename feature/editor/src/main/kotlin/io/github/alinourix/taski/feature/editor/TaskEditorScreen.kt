package io.github.alinourix.taski.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.DueChip
import io.github.alinourix.taski.core.ui.component.LiveTimerChip
import io.github.alinourix.taski.core.ui.component.TagChip
import io.github.alinourix.taski.core.ui.component.TaskRow
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.reminderLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.picker.DatePickerSheet
import io.github.alinourix.taski.core.ui.picker.PrioritySheet
import io.github.alinourix.taski.core.ui.picker.ProjectPickerSheet
import io.github.alinourix.taski.core.ui.picker.ReminderSheet
import io.github.alinourix.taski.core.ui.picker.RepeatSheet
import io.github.alinourix.taski.core.ui.picker.TagPickerSheet
import io.github.alinourix.taski.core.ui.picker.TaskPickerSheet
import io.github.alinourix.taski.core.ui.theme.roles
import kotlinx.coroutines.launch
import java.time.Instant
import io.github.alinourix.taski.core.ui.R as UiR

private enum class Sheet { Due, Repeat, Reminder, Priority, Project, Tags, Dependency }

@Composable
fun TaskEditorScreen(
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val refused = stringResource(R.string.editor_dependency_refused)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                EditorEvent.DependencyRefused -> snackbar.showSnackbar(refused)
                EditorEvent.Deleted -> onBack()
            }
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.flush() } }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.editor_copied)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    val item = state.item
                    if (item != null) {
                        FilledTonalButton(
                            onClick = { viewModel.startFocus(); onOpenTimer() },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.editor_start_focus), Modifier.padding(start = 6.dp))
                        }
                        IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.MoreVert, stringResource(UiR.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_copy_markdown)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.markdownLine()?.let { clipboard.setText(AnnotatedString(it)) }
                                    scope.launch { snackbar.showSnackbar(copied) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menuOpen = false; viewModel.delete() },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.item == null -> EmptyState(Icons.Rounded.History, stringResource(R.string.editor_missing), Modifier.padding(padding))
            else -> EditorContent(state, viewModel, onOpenTask, onOpenTimer, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp))
        }
    }
}

@Composable
private fun EditorContent(
    state: EditorState,
    viewModel: TaskEditorViewModel,
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
    contentPadding: PaddingValues,
) {
    val item = state.item ?: return
    val task = item.task
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = contentPadding) {
        state.parent?.let { parent ->
            item {
                AssistChip(
                    onClick = { onOpenTask(parent.id) },
                    label = { Text(stringResource(R.string.editor_parent, parent.task.title), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.SubdirectoryArrowRight, null, Modifier.size(18.dp)) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item {
            TextField(
                value = title,
                onValueChange = { title = it; viewModel.setTitle(it) },
                textStyle = MaterialTheme.typography.headlineMediumEmphasized,
                placeholder = { Text(stringResource(R.string.editor_title_hint), style = MaterialTheme.typography.headlineMediumEmphasized) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectedToggleGroup(
                    options = listOf(TaskStatus.NotStarted, TaskStatus.InProgress, TaskStatus.Done, TaskStatus.NotDone),
                    selected = task.status,
                    onSelect = viewModel::setStatus,
                    label = { it.code },
                    icon = TaskIcons::status,
                    showLabels = false,
                )
                Text(
                    statusLabel(task.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = task.status.roles().accent,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        item { SectionHeader(stringResource(R.string.editor_details)) }
        item {
            val rows = 6
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap)) {
                PropertyRow(TaskIcons.Due, stringResource(UiR.string.pick_date), 0, rows, { sheet = Sheet.Due }) {
                    if (task.dueDate != null) DueChip(task) else Text(stringResource(UiR.string.no_date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PropertyRow(TaskIcons.Repeat, stringResource(UiR.string.pick_repeat), 1, rows, { sheet = Sheet.Repeat }) {
                    Text(repeatLabel(task.repeat), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PropertyRow(TaskIcons.Reminder, stringResource(UiR.string.pick_reminder), 2, rows, { sheet = Sheet.Reminder }) {
                    Text(reminderLabel(task.reminderOffsetMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PropertyRow(
                    task.priority?.let(TaskIcons::priority) ?: TaskIcons.priority(io.github.alinourix.taski.core.domain.model.Priority.Medium),
                    stringResource(UiR.string.pick_priority), 3, rows, { sheet = Sheet.Priority },
                    iconTint = task.priority?.roles()?.accent,
                ) {
                    Text(priorityLabel(task.priority), color = task.priority?.roles()?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PropertyRow(
                    if (item.project == null) Icons.Rounded.Inbox else Icons.Rounded.Folder,
                    stringResource(UiR.string.pick_project), 4, rows, { sheet = Sheet.Project },
                    iconTint = item.project?.color?.roles()?.accent,
                ) {
                    Text(item.project?.name ?: stringResource(UiR.string.inbox), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PropertyRow(TaskIcons.Tag, stringResource(UiR.string.pick_tags), 5, rows, { sheet = Sheet.Tags }) {
                    if (item.tags.isEmpty()) {
                        Text(stringResource(R.string.editor_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.tags.forEach { TagChip(it) }
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.editor_capabilities)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap)) {
                ProgressCapability(task.progress, 0, { viewModel.edit(TaskEdit.Progress(it)) })
                TimerCapability(
                    minutes = task.timerMinutes,
                    defaultMinutes = state.defaultTimerMinutes,
                    active = state.activeTimer?.takeIf { it.taskId == task.id },
                    onChange = { viewModel.edit(TaskEdit.Timer(it)) },
                    onStart = { viewModel.startFocus(); onOpenTimer() },
                    onOpenTimer = onOpenTimer,
                )
                Text(
                    stringResource(R.string.editor_capabilities_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; viewModel.setNotes(it) },
                label = { Text(stringResource(R.string.editor_notes_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.editor_subtasks), count = state.subtasks.size.takeIf { it > 0 }) }
        items(state.subtasks, key = { it.id }) { sub ->
            TaskRow(
                item = sub,
                onToggle = { viewModel.toggle(sub.id) },
                onClick = { onOpenTask(sub.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                shape = GroupedShapes.forIndex(state.subtasks.indexOf(sub), state.subtasks.size + 1),
            )
        }
        item { AddLine(stringResource(R.string.editor_add_subtask), state.subtasks.size, viewModel::addSubtask) }

        item { SectionHeader(stringResource(R.string.editor_blocked_by), count = state.blockers.size.takeIf { it > 0 }) }
        items(state.blockers, key = { "dep-" + it.id }) { blocker ->
            Surface(
                onClick = { onOpenTask(blocker.id) },
                shape = GroupedShapes.forIndex(state.blockers.indexOf(blocker), state.blockers.size + 1),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
            ) {
                ListItem(
                    headlineContent = { Text(blocker.title) },
                    leadingContent = { Icon(TaskIcons.status(blocker.status), null, tint = blocker.status.roles().accent) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeDependency(blocker.id) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.editor_remove))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item {
            Surface(
                onClick = { sheet = Sheet.Dependency },
                shape = GroupedShapes.forIndex(state.blockers.size, state.blockers.size + 1),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.editor_add_dependency)) },
                    leadingContent = { Icon(Icons.Rounded.Link, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        item { SectionHeader(stringResource(R.string.editor_history)) }
        item { History(state) }
    }

    when (sheet) {
        Sheet.Due -> DatePickerSheet(task.dueDate, task.dueTime, onDismiss = { sheet = null }) { date, time ->
            viewModel.edit(TaskEdit.Due(date, time)); sheet = null
        }
        Sheet.Repeat -> RepeatSheet(task.repeat, onDismiss = { sheet = null }) { viewModel.edit(TaskEdit.Repeat(it)); sheet = null }
        Sheet.Reminder -> ReminderSheet(task.reminderOffsetMinutes, onDismiss = { sheet = null }) { viewModel.edit(TaskEdit.Reminder(it)); sheet = null }
        Sheet.Priority -> PrioritySheet(task.priority, onDismiss = { sheet = null }) { viewModel.edit(TaskEdit.SetPriority(it)); sheet = null }
        Sheet.Project -> ProjectPickerSheet(state.projects, task.projectId, onDismiss = { sheet = null }) {
            viewModel.edit(TaskEdit.MoveToProject(it)); sheet = null
        }
        Sheet.Tags -> TagPickerSheet(
            tags = state.tags,
            selected = item.tags.map { it.id }.toSet(),
            onToggle = viewModel::toggleTag,
            onCreate = viewModel::createTag,
            onDismiss = { sheet = null },
        )
        Sheet.Dependency -> TaskPickerSheet(
            title = stringResource(R.string.editor_pick_dependency),
            searchHint = stringResource(R.string.editor_search_tasks),
            tasks = state.candidates.filter { c -> state.blockers.none { it.id == c.id } },
            onDismiss = { sheet = null },
        ) { viewModel.addDependency(it.id); sheet = null }
        null -> Unit
    }
}

@Composable
private fun PropertyRow(
    icon: ImageVector,
    label: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    iconTint: Color? = null,
    value: @Composable () -> Unit,
) {
    Surface(onClick = onClick, shape = GroupedShapes.forIndex(index, count), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            supportingContent = { Column(Modifier.padding(top = 4.dp)) { value() } },
            leadingContent = { Icon(icon, null, tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ProgressCapability(progress: StepProgress?, index: Int, onChange: (StepProgress?) -> Unit) {
    val persian = LocalUiConfig.current.persian
    Surface(shape = GroupedShapes.forIndex(index, 2), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TaskIcons.Progress, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.editor_progress), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 16.dp))
                if (progress == null) {
                    FilledTonalButton(onClick = { onChange(StepProgress(0, 5)) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.editor_add_capability), Modifier.padding(start = 6.dp))
                    }
                } else {
                    IconButton(onClick = { onChange(null) }) { Icon(Icons.Rounded.Close, stringResource(R.string.editor_remove)) }
                }
            }
            if (progress != null) {
                LinearWavyProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { onChange(progress.copy(done = (progress.clampedDone - 1).coerceAtLeast(0))) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Remove, null)
                    }
                    Text(
                        stringResource(R.string.editor_progress_steps, progress.clampedDone, progress.total).localizeDigits(persian),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(onClick = { onChange(progress.copy(done = (progress.clampedDone + 1).coerceAtMost(progress.total))) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, null)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.editor_progress_total), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { if (progress.total > 1) onChange(StepProgress(progress.clampedDone.coerceAtMost(progress.total - 1), progress.total - 1)) }) {
                        Icon(Icons.Rounded.Remove, null)
                    }
                    Text(progress.total.toString().localizeDigits(persian), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onChange(progress.copy(total = progress.total + 1)) }) { Icon(Icons.Rounded.Add, null) }
                }
            }
        }
    }
}

@Composable
private fun TimerCapability(
    minutes: Int?,
    defaultMinutes: Int,
    active: FocusTimerState?,
    onChange: (Int?) -> Unit,
    onStart: () -> Unit,
    onOpenTimer: () -> Unit,
) {
    val persian = LocalUiConfig.current.persian
    Surface(shape = GroupedShapes.forIndex(1, 2), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TaskIcons.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.editor_timer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 16.dp))
                when {
                    active != null -> LiveTimerChip(active, onClick = onOpenTimer)
                    minutes == null -> FilledTonalButton(onClick = { onChange(defaultMinutes) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.editor_add_capability), Modifier.padding(start = 6.dp))
                    }
                    else -> IconButton(onClick = { onChange(null) }) { Icon(Icons.Rounded.Close, stringResource(R.string.editor_remove)) }
                }
            }
            if (minutes != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onChange((minutes - 5).coerceAtLeast(5)) }) { Icon(Icons.Rounded.Remove, null) }
                    Text(
                        stringResource(R.string.editor_timer_minutes, minutes).localizeDigits(persian),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    IconButton(onClick = { onChange(minutes + 5) }) { Icon(Icons.Rounded.Add, null) }
                    Row(Modifier.weight(1f)) {}
                    FilledIconButton(onClick = onStart, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.editor_start_focus))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddLine(hint: String, existing: Int, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Surface(
        shape = GroupedShapes.forIndex(existing, existing + 1),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(hint) },
            leadingIcon = { Icon(Icons.Rounded.Add, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd(text); text = "" }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun History(state: EditorState) {
    val config = LocalUiConfig.current
    fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(config.zone).let {
        CalendarText.date(it.toLocalDate(), config.calendar, config.persian, config.today) + " " + CalendarText.time(it.toLocalTime(), config.persian)
    }
    val item = state.item ?: return
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val focused = state.sessions.sumOf { it.seconds }
        if (focused > 0) {
            Text(stringResource(R.string.editor_focused_total, FocusTimerState.formatClock(focused).localizeDigits(config.persian)), style = MaterialTheme.typography.bodyMedium)
        }
        state.completions.take(10).forEach {
            Text(stringResource(R.string.editor_completed_on, dateOf(it.completedAt)), style = MaterialTheme.typography.bodyMedium)
        }
        state.activity.filter { it.kind != ActivityKind.Completed }.take(20).forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(activityLabel(entry.kind), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(dateOf(entry.occurredAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.editor_created, dateOf(item.task.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun activityLabel(kind: ActivityKind): String = stringResource(
    when (kind) {
        ActivityKind.Created -> R.string.activity_created
        ActivityKind.StatusChanged -> R.string.activity_status_changed
        ActivityKind.Completed -> R.string.activity_completed
        ActivityKind.RolledForward -> R.string.activity_rolled_forward
        ActivityKind.Deleted -> R.string.activity_deleted
        ActivityKind.Restored -> R.string.activity_restored
        ActivityKind.MovedToInbox -> R.string.activity_moved_to_inbox
        ActivityKind.CycleEdgeDropped -> R.string.activity_cycle_edge_dropped
    },
)
