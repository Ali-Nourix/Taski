package io.github.alinourix.taski.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
import io.github.alinourix.taski.core.designsystem.component.Sections
import io.github.alinourix.taski.core.designsystem.component.sectionRow
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.DueChip
import io.github.alinourix.taski.core.ui.component.LiveTimerChip
import io.github.alinourix.taski.core.ui.component.MetaChip
import io.github.alinourix.taski.core.ui.component.NewTaskRow
import io.github.alinourix.taski.core.ui.component.PriorityChip
import io.github.alinourix.taski.core.ui.component.PropertyLine
import io.github.alinourix.taski.core.ui.component.StatusChip
import io.github.alinourix.taski.core.ui.component.TagChip
import io.github.alinourix.taski.core.ui.component.TaskRow
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.reminderLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.picker.DatePickerSheet
import io.github.alinourix.taski.core.ui.picker.PickerSheet
import io.github.alinourix.taski.core.ui.picker.PrioritySheet
import io.github.alinourix.taski.core.ui.picker.ProjectPickerSheet
import io.github.alinourix.taski.core.ui.picker.ReminderSheet
import io.github.alinourix.taski.core.ui.picker.RepeatSheet
import io.github.alinourix.taski.core.ui.picker.StatusSheet
import io.github.alinourix.taski.core.ui.picker.TagPickerSheet
import io.github.alinourix.taski.core.ui.picker.TaskPickerSheet
import io.github.alinourix.taski.core.ui.theme.roles
import kotlinx.coroutines.launch
import java.time.Instant
import io.github.alinourix.taski.core.ui.R as UiR

private enum class Sheet { Status, Due, Repeat, Reminder, Priority, Project, Tags, Dependency, Progress, Timer }

/**
 * A task as a page, the way Notion opens one: a breadcrumb to its parent, the
 * name as the page title, its properties as a table of label and value that
 * each open their picker, then notes as body text, subtasks, what it waits
 * for, and its history.
 */
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
                title = {
                    if (scrollBehavior.state.overlappedFraction > 0.5f) {
                        Text(state.item?.task?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    val item = state.item
                    if (item != null) {
                        IconButton(onClick = { viewModel.startFocus(); onOpenTimer() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.editor_start_focus))
                        }
                        Box {
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.item == null -> EmptyState(Icons.Rounded.History, stringResource(R.string.editor_missing), Modifier.padding(padding))
            else -> EditorContent(state, viewModel, onOpenTask, onOpenTimer, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp))
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
    var addingSubtask by rememberSaveable { mutableStateOf(false) }
    val activeTimer = state.activeTimer?.takeIf { it.taskId == task.id }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = contentPadding) {
        state.parent?.let { parent ->
            item(key = "parent") {
                Row(
                    Modifier.padding(horizontal = 12.dp).clip(MaterialTheme.shapes.small).clickable { onOpenTask(parent.id) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.SubdirectoryArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(parent.task.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item(key = "title") {
            PlainField(
                value = title,
                onValueChange = { title = it; viewModel.setTitle(it) },
                placeholder = stringResource(R.string.editor_title_hint),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            )
        }
        item(key = "properties") {
            Column(
                Modifier
                    .padding(horizontal = Sections.Margin)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                PropertyLine(Icons.Rounded.Timelapse, stringResource(R.string.editor_status), { sheet = Sheet.Status }) {
                    StatusChip(task.status)
                }
                PropertyLine(TaskIcons.Due, stringResource(UiR.string.pick_date), { sheet = Sheet.Due }) {
                    if (task.dueDate != null) DueChip(task) else EmptyValue()
                }
                PropertyLine(TaskIcons.Repeat, stringResource(UiR.string.pick_repeat), { sheet = Sheet.Repeat }) {
                    if (task.repeat != null) ValueText(repeatLabel(task.repeat)) else EmptyValue()
                }
                PropertyLine(TaskIcons.Reminder, stringResource(UiR.string.pick_reminder), { sheet = Sheet.Reminder }) {
                    if (task.reminderOffsetMinutes != null) ValueText(reminderLabel(task.reminderOffsetMinutes)) else EmptyValue()
                }
                PropertyLine(Icons.Rounded.KeyboardDoubleArrowUp, stringResource(UiR.string.pick_priority), { sheet = Sheet.Priority }) {
                    task.priority?.let { PriorityChip(it) } ?: EmptyValue()
                }
                PropertyLine(Icons.Rounded.Folder, stringResource(UiR.string.pick_project), { sheet = Sheet.Project }) {
                    item.project?.let { MetaChip(it.name, it.color.roles(), icon = Icons.Rounded.Folder) } ?: ValueText(stringResource(UiR.string.inbox))
                }
                PropertyLine(TaskIcons.Tag, stringResource(UiR.string.pick_tags), { sheet = Sheet.Tags }) {
                    if (item.tags.isEmpty()) {
                        EmptyValue()
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.tags.forEach { TagChip(it) }
                        }
                    }
                }
                PropertyLine(TaskIcons.Progress, stringResource(R.string.editor_progress), {
                    if (task.progress == null) viewModel.edit(TaskEdit.Progress(StepProgress(0, 5)))
                    sheet = Sheet.Progress
                }) {
                    val progress = task.progress
                    if (progress == null) EmptyValue() else ProgressValue(progress) {
                        viewModel.edit(TaskEdit.Progress(progress.copy(done = (progress.clampedDone + 1).coerceAtMost(progress.total))))
                    }
                }
                PropertyLine(TaskIcons.Timer, stringResource(R.string.editor_timer), {
                    if (task.timerMinutes == null) viewModel.edit(TaskEdit.Timer(state.defaultTimerMinutes)) else sheet = Sheet.Timer
                }) {
                    val minutes = task.timerMinutes
                    when {
                        activeTimer != null -> LiveTimerChip(activeTimer, onClick = onOpenTimer)
                        minutes != null -> TimerValue(minutes) { viewModel.startFocus(); onOpenTimer() }
                        else -> EmptyValue()
                    }
                }
            }
        }
        item(key = "gap") { Spacer(Modifier.height(16.dp)) }
        item(key = "notes") {
            PlainField(
                value = notes,
                onValueChange = { notes = it; viewModel.setNotes(it) },
                placeholder = stringResource(R.string.editor_notes_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                singleLine = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).heightIn(min = 72.dp),
            )
        }

        item(key = "subtasks") { SectionHeader(stringResource(R.string.editor_subtasks), count = state.subtasks.size.takeIf { it > 0 }) }
        itemsIndexed(state.subtasks, key = { _, it -> it.id }) { index, sub ->
            TaskRow(
                item = sub,
                onToggle = { viewModel.toggle(sub.id) },
                onClick = { onOpenTask(sub.id) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.sectionRow(index, state.subtasks.size + 1),
            )
        }
        item(key = "add-subtask") {
            NewTaskRow(
                open = addingSubtask,
                onOpenChange = { addingSubtask = it },
                onAdd = viewModel::addSubtask,
                modifier = Modifier.sectionRow(state.subtasks.size, state.subtasks.size + 1),
                padding = PaddingValues(start = 18.dp, end = 4.dp),
            )
        }

        item(key = "blockers") { SectionHeader(stringResource(R.string.editor_blocked_by), count = state.blockers.size.takeIf { it > 0 }) }
        itemsIndexed(state.blockers, key = { _, it -> "dep-" + it.id }) { index, blocker ->
            Column(Modifier.sectionRow(index, state.blockers.size + 1)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTask(blocker.id) }.padding(start = 18.dp, end = 4.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(TaskIcons.status(blocker.status), null, tint = blocker.status.roles().accent, modifier = Modifier.size(20.dp))
                    Text(blocker.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeDependency(blocker.id) }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.editor_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(start = 52.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item(key = "add-blocker") {
            Row(
                Modifier
                    .sectionRow(state.blockers.size, state.blockers.size + 1)
                    .fillMaxWidth()
                    .clickable { sheet = Sheet.Dependency }
                    .padding(start = 18.dp, end = 16.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.editor_add_dependency), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item(key = "history-header") { SectionHeader(stringResource(R.string.editor_history)) }
        item(key = "history") { History(state) }
    }

    val close = { sheet = null }
    when (sheet) {
        Sheet.Status -> StatusSheet(task.status, onDismiss = close) { viewModel.setStatus(it); close() }
        Sheet.Due -> DatePickerSheet(task.dueDate, task.dueTime, onDismiss = close) { date, time ->
            viewModel.edit(TaskEdit.Due(date, time)); close()
        }
        Sheet.Repeat -> RepeatSheet(task.repeat, onDismiss = close) { viewModel.edit(TaskEdit.Repeat(it)); close() }
        Sheet.Reminder -> ReminderSheet(task.reminderOffsetMinutes, onDismiss = close) { viewModel.edit(TaskEdit.Reminder(it)); close() }
        Sheet.Priority -> PrioritySheet(task.priority, onDismiss = close) { viewModel.edit(TaskEdit.SetPriority(it)); close() }
        Sheet.Project -> ProjectPickerSheet(state.projects, task.projectId, onDismiss = close) {
            viewModel.edit(TaskEdit.MoveToProject(it)); close()
        }
        Sheet.Tags -> TagPickerSheet(
            tags = state.tags,
            selected = item.tags.map { it.id }.toSet(),
            onToggle = viewModel::toggleTag,
            onCreate = viewModel::createTag,
            onDismiss = close,
        )
        Sheet.Dependency -> TaskPickerSheet(
            title = stringResource(R.string.editor_pick_dependency),
            searchHint = stringResource(R.string.editor_search_tasks),
            tasks = state.candidates.filter { c -> state.blockers.none { it.id == c.id } },
            onDismiss = close,
        ) { viewModel.addDependency(it.id); close() }
        Sheet.Progress -> task.progress?.let { progress ->
            ProgressSheet(progress, onChange = { viewModel.edit(TaskEdit.Progress(it)) }, onRemove = { viewModel.edit(TaskEdit.Progress(null)); close() }, onDismiss = close)
        }
        Sheet.Timer -> task.timerMinutes?.let { minutes ->
            TimerSheet(
                minutes = minutes,
                onChange = { viewModel.edit(TaskEdit.Timer(it)) },
                onStart = { close(); viewModel.startFocus(); onOpenTimer() },
                onRemove = { viewModel.edit(TaskEdit.Timer(null)); close() },
                onDismiss = close,
            )
        }
        null -> Unit
    }
}

/** A borderless field that reads as the page's own text until it is tapped. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.outline)
                field()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun EmptyValue() {
    Text(stringResource(R.string.editor_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ValueText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ProgressValue(progress: StepProgress, onStep: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f),
        )
        Text("${progress.clampedDone}/${progress.total}".localizeDigits(persian), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalIconButton(onClick = onStep, enabled = progress.clampedDone < progress.total, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Add, stringResource(UiR.string.action_add), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TimerValue(minutes: Int, onStart: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ValueText(stringResource(R.string.editor_timer_minutes, minutes).localizeDigits(persian))
        Box(Modifier.weight(1f))
        FilledTonalIconButton(onClick = onStart, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.editor_start_focus), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProgressSheet(progress: StepProgress, onChange: (StepProgress) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    PickerSheet(stringResource(R.string.editor_progress), onDismiss) {
        LinearWavyProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp))
        Stepper(
            label = stringResource(R.string.editor_steps_done),
            value = progress.clampedDone.toString().localizeDigits(persian),
            onMinus = { onChange(progress.copy(done = (progress.clampedDone - 1).coerceAtLeast(0))) },
            onPlus = { onChange(progress.copy(done = (progress.clampedDone + 1).coerceAtMost(progress.total))) },
            index = 0,
        )
        Stepper(
            label = stringResource(R.string.editor_progress_total),
            value = progress.total.toString().localizeDigits(persian),
            onMinus = { if (progress.total > 1) onChange(StepProgress(progress.clampedDone.coerceAtMost(progress.total - 1), progress.total - 1)) },
            onPlus = { onChange(progress.copy(total = progress.total + 1)) },
            index = 1,
        )
        TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.editor_remove_progress))
        }
    }
}

@Composable
private fun TimerSheet(minutes: Int, onChange: (Int) -> Unit, onStart: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    PickerSheet(stringResource(R.string.editor_timer), onDismiss) {
        Stepper(
            label = stringResource(R.string.editor_timer_length),
            value = stringResource(R.string.editor_timer_minutes, minutes).localizeDigits(persian),
            onMinus = { onChange((minutes - 5).coerceAtLeast(5)) },
            onPlus = { onChange(minutes + 5) },
            index = 0,
            count = 1,
        )
        Button(onClick = onStart, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Text(stringResource(R.string.editor_start_focus), Modifier.padding(start = 6.dp))
        }
        TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.editor_remove_timer))
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, index: Int, count: Int = 2) {
    Surface(shape = GroupedShapes.forIndex(index, count), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onMinus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Remove, null) }
            Text(value, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onPlus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, null) }
        }
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
