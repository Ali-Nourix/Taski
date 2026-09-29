package io.github.alinourix.taski.feature.projects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.NewTaskRow
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.SwipeableTaskRow
import io.github.alinourix.taski.core.ui.component.TaskEventsEffect
import io.github.alinourix.taski.core.ui.component.TaskSheetHost
import io.github.alinourix.taski.core.ui.component.TaskSheetState
import io.github.alinourix.taski.core.ui.component.rememberTaskSheetState
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.theme.roles
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.alinourix.taski.core.ui.R as UiR

/**
 * A project as a page: its icon and name, how far along it is, then its tasks
 * one line each with subtasks indented beneath, "+ New" at the foot, and the
 * completed ones folded away. Order is changed in a reorder mode, so the page
 * carries no drag handles the rest of the time.
 */
@Composable
fun ProjectScreen(
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit = {},
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions, onFocusStarted = onOpenTimer)
    val sheet = rememberTaskSheetState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val name = state.project?.name ?: stringResource(UiR.string.inbox)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                // The page shows its own title; the bar picks it up once the page scrolls under it.
                title = {
                    if (scrollBehavior.state.overlappedFraction > 0.5f) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    IconToggleButton(checked = reordering, onCheckedChange = { reordering = it }, shapes = IconButtonDefaults.toggleableShapes()) {
                        Icon(if (reordering) Icons.Rounded.Check else Icons.Rounded.SwapVert, stringResource(R.string.projects_reorder))
                    }
                    if (state.project != null) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(UiR.string.action_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_edit)) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menu = false; editing = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_delete)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menu = false; deleting = true },
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
            state.missing -> EmptyState(Icons.Rounded.FolderOff, stringResource(R.string.project_empty), Modifier.padding(padding))
            else -> ProjectPage(
                state = state,
                name = name,
                viewModel = viewModel,
                sheet = sheet,
                onOpenTask = onOpenTask,
                reordering = reordering,
                adding = adding,
                onAddingChange = { adding = it },
                showDone = showDone,
                onToggleDone = { showDone = !showDone },
                padding = padding,
            )
        }
    }

    val project = state.project
    if (editing && project != null) {
        ProjectDialog(stringResource(R.string.projects_edit), project.name, project.color, onDismiss = { editing = false }) { newName, color ->
            viewModel.update(newName, color); editing = false
        }
    }
    if (deleting) {
        ConfirmDeleteDialog(stringResource(R.string.projects_delete), stringResource(R.string.projects_delete_body), onDismiss = { deleting = false }) {
            deleting = false; viewModel.delete(); onBack()
        }
    }
    TaskSheetHost(sheet, state::find, state.tags, state.projects, viewModel.actions, onOpenTask)
}

@Composable
private fun ProjectPage(
    state: ProjectDetailState,
    name: String,
    viewModel: ProjectViewModel,
    sheet: TaskSheetState,
    onOpenTask: (String) -> Unit,
    reordering: Boolean,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    showDone: Boolean,
    onToggleDone: () -> Unit,
    padding: PaddingValues,
) {
    var order by remember(state.open) { mutableStateOf(state.open) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = order.indexOfFirst { it.id == from.key }
        val b = order.indexOfFirst { it.id == to.key }
        if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
    }
    val extras = RowExtras(timer = state.timer)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
    ) {
        item(key = "title") { PageTitle(state, name) }
        items(order, key = { it.id }) { item ->
            ReorderableItem(reorder, key = item.id) { dragging ->
                Column(if (dragging) Modifier.shadow(6.dp, MaterialTheme.shapes.small) else Modifier) {
                    Row(Modifier.background(MaterialTheme.colorScheme.surface), verticalAlignment = Alignment.CenterVertically) {
                        SwipeableTaskRow(
                            item = item,
                            onToggle = { viewModel.actions.toggle(item) },
                            onDelete = { viewModel.actions.delete(item) },
                            onClick = { onOpenTask(item.id) },
                            onEdit = { sheet.open(item.id, it) },
                            extras = extras,
                            modifier = Modifier.weight(1f),
                        )
                        if (reordering) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(onDragStopped = {
                                    val i = order.indexOfFirst { it.id == item.id }
                                    viewModel.move(item.id, order.getOrNull(i - 1)?.id, order.getOrNull(i + 1)?.id)
                                }),
                            ) { Icon(Icons.Rounded.DragIndicator, stringResource(R.string.projects_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    state.subtasks[item.id].orEmpty().forEach { sub -> Subtask(sub, viewModel, sheet, onOpenTask, extras) }
                }
            }
        }
        item(key = "new") {
            NewTaskRow(open = adding, onOpenChange = onAddingChange, onAdd = viewModel::add, padding = PaddingValues(start = 18.dp, end = 4.dp))
        }
        if (state.done.isNotEmpty()) {
            item(key = "done-header") { DoneHeader(state.done.size, showDone, onToggleDone) }
            if (showDone) {
                items(state.done, key = { "done-" + it.id }) { item ->
                    SwipeableTaskRow(
                        item = item,
                        onToggle = { viewModel.actions.toggle(item) },
                        onDelete = { viewModel.actions.delete(item) },
                        onClick = { onOpenTask(item.id) },
                        onEdit = { sheet.open(item.id, it) },
                        extras = extras,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** The page header: an icon in the project's colour, the name large, and progress as one quiet line. */
@Composable
private fun PageTitle(state: ProjectDetailState, name: String) {
    val persian = LocalUiConfig.current.persian
    val open = state.open.size
    val done = state.done.size
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val project = state.project
        if (project != null) {
            PageIcon(name, project.color.roles(), size = 52.dp)
        } else {
            InboxIcon(size = 52.dp)
        }
        Text(name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        if (open + done > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / (open + done) },
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.projects_open_count, open, done).localizeDigits(persian),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Subtask(sub: TaskItem, viewModel: ProjectViewModel, sheet: TaskSheetState, onOpenTask: (String) -> Unit, extras: RowExtras) {
    SwipeableTaskRow(
        item = sub,
        onToggle = { viewModel.actions.toggle(sub) },
        onDelete = { viewModel.actions.delete(sub) },
        onClick = { onOpenTask(sub.id) },
        onEdit = { sheet.open(sub.id, it) },
        extras = extras,
        modifier = Modifier.padding(start = 32.dp),
    )
}

@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    val turn by animateFloatAsState(if (expanded) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 16.dp, top = 16.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).rotate(turn),
        )
        Text(stringResource(R.string.project_show_done), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString().localizeDigits(persian), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
    }
}
