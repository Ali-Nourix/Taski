package io.github.alinourix.taski.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.SwipeableTaskRow
import io.github.alinourix.taski.core.ui.component.TaskEventsEffect
import io.github.alinourix.taski.core.ui.picker.StatusSheet
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.alinourix.taski.core.ui.R as UiR

@Composable
fun ProjectScreen(
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var statusFor by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(state.project?.name ?: stringResource(UiR.string.inbox)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    if (state.project != null) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.projects_edit)) }
                        IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.projects_delete)) }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.missing -> EmptyState(Icons.Rounded.FolderOff, stringResource(R.string.project_empty), Modifier.padding(padding))
            else -> {
                var order by remember(state.open) { mutableStateOf(state.open) }
                val listState = rememberLazyListState()
                val reorder = rememberReorderableLazyListState(listState) { from, to ->
                    val a = order.indexOfFirst { it.id == from.key }
                    val b = order.indexOfFirst { it.id == to.key }
                    if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
                ) {
                    item(key = "add") { AddTaskField(viewModel::add) }
                    if (order.isEmpty() && state.done.isEmpty()) {
                        item(key = "empty") { EmptyState(Icons.Rounded.Inventory2, stringResource(R.string.project_empty)) }
                    }
                    itemsIndexed(order, key = { _, item -> item.id }) { index, item ->
                        ReorderableItem(reorder, key = item.id) { dragging ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        SwipeableTaskRow(
                                            item = item,
                                            onToggle = { viewModel.actions.toggle(item) },
                                            onDelete = { viewModel.actions.delete(item) },
                                            onClick = { onOpenTask(item.id) },
                                            onStatusMenu = { statusFor = item.id },
                                            extras = RowExtras(timer = state.timer),
                                            shape = GroupedShapes.forIndex(index, order.size),
                                            modifier = if (dragging) Modifier.shadow(8.dp, GroupedShapes.forIndex(index, order.size)) else Modifier,
                                        )
                                    }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(onDragStopped = {
                                            val i = order.indexOfFirst { it.id == item.id }
                                            viewModel.move(item.id, order.getOrNull(i - 1)?.id, order.getOrNull(i + 1)?.id)
                                        }),
                                    ) { Icon(Icons.Rounded.DragIndicator, stringResource(R.string.projects_reorder)) }
                                }
                                Subtasks(state.subtasks[item.id].orEmpty(), viewModel, onOpenTask)
                            }
                        }
                    }
                    if (state.done.isNotEmpty()) {
                        item(key = "done-header") {
                            SectionHeader(stringResource(R.string.project_show_done), count = state.done.size, trailing = {
                                IconButton(onClick = { showDone = !showDone }) {
                                    Icon(if (showDone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                                }
                            })
                        }
                        if (showDone) {
                            itemsIndexed(state.done, key = { _, item -> "done-" + item.id }) { index, item ->
                                SwipeableTaskRow(
                                    item = item,
                                    onToggle = { viewModel.actions.toggle(item) },
                                    onDelete = { viewModel.actions.delete(item) },
                                    onClick = { onOpenTask(item.id) },
                                    shape = GroupedShapes.forIndex(index, state.done.size),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp).animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val project = state.project
    if (editing && project != null) {
        ProjectDialog(stringResource(R.string.projects_edit), project.name, project.color, onDismiss = { editing = false }) { name, color ->
            viewModel.update(name, color); editing = false
        }
    }
    if (deleting) {
        ConfirmDeleteDialog(stringResource(R.string.projects_delete), stringResource(R.string.projects_delete_body), onDismiss = { deleting = false }) {
            deleting = false; viewModel.delete(); onBack()
        }
    }
    statusFor?.let { id ->
        val item = (state.open + state.done + state.subtasks.values.flatten()).firstOrNull { it.id == id }
        if (item == null) statusFor = null else {
            StatusSheet(item.task.status, onDismiss = { statusFor = null }) { viewModel.actions.setStatus(item, it); statusFor = null }
        }
    }
}

@Composable
private fun Subtasks(subtasks: List<TaskItem>, viewModel: ProjectViewModel, onOpenTask: (String) -> Unit) {
    if (subtasks.isEmpty()) return
    Column(Modifier.padding(start = 28.dp, end = 48.dp, top = 2.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap)) {
        subtasks.forEachIndexed { index, sub ->
            SwipeableTaskRow(
                item = sub,
                onToggle = { viewModel.actions.toggle(sub) },
                onDelete = { viewModel.actions.delete(sub) },
                onClick = { onOpenTask(sub.id) },
                shape = GroupedShapes.forIndex(index, subtasks.size, outer = 16.dp, inner = 4.dp),
            )
        }
    }
}

@Composable
private fun AddTaskField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.project_add_task)) },
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
