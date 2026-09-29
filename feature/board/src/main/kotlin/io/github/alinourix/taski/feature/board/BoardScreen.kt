package io.github.alinourix.taski.feature.board

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.model.ViewLayout
import io.github.alinourix.taski.core.domain.query.TaskGroup
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.SwipeableTaskRow
import io.github.alinourix.taski.core.ui.component.TaskEventsEffect
import io.github.alinourix.taski.core.ui.component.TaskRow
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.picker.OptionItem
import io.github.alinourix.taski.core.ui.picker.PickerSheet
import io.github.alinourix.taski.core.ui.picker.StatusSheet
import io.github.alinourix.taski.core.ui.theme.roles
import io.github.alinourix.taski.core.ui.R as UiR

private enum class FilterSheet { Status, Priority, Tags, Project }

@Composable
fun BoardScreen(
    onOpenTask: (String) -> Unit,
    onManageTags: () -> Unit,
    onAddTask: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions)
    var filterSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    var statusFor by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.board_title), style = MaterialTheme.typography.titleLargeEmphasized) },
                actions = {
                    IconButton(onClick = onManageTags, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Sell, stringResource(R.string.board_manage_tags))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                SearchField(query) { viewModel.search.value = it }
                ViewsRow(state, onApply = viewModel::apply, onDelete = viewModel::deleteView)
                FiltersRow(state.view, onOpen = { filterSheet = it }, onClear = viewModel::clearFilters)
                Summary(state)
                when {
                    state.loading -> LoadingState()
                    state.totalTasks == 0 -> EmptyState(Icons.Rounded.SpaceDashboard, stringResource(R.string.board_empty), body = stringResource(R.string.board_empty_body))
                    state.groups.all { it.items.isEmpty() } && state.view.groupBy != GroupBy.Status ->
                        EmptyState(Icons.Rounded.FilterAltOff, stringResource(R.string.board_empty_filtered))
                    state.view.layout == ViewLayout.List -> ListLayout(state, viewModel, onOpenTask, { statusFor = it }, contentPadding)
                    else -> BoardLayout(state, viewModel, onOpenTask, contentPadding)
                }
            }
            Toolbar(
                state.view,
                onLayout = viewModel::setLayout,
                onGroup = viewModel::setGroupBy,
                onSort = viewModel::setSort,
                onToggleSubtasks = viewModel::toggleSubtasks,
                onSave = { saving = true },
                onAddTask = onAddTask,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            )
        }
    }

    when (filterSheet) {
        FilterSheet.Status -> MultiSelect(
            stringResource(R.string.board_filter_status),
            TaskStatus.BOARD_ORDER.map { Choice(it.code, statusLabel(it), TaskIcons.status(it), it.roles().accent) },
            state.view.statuses.toSet(),
            viewModel::toggleStatus,
        ) { filterSheet = null }
        FilterSheet.Priority -> MultiSelect(
            stringResource(R.string.board_filter_priority),
            Priority.entries.map { Choice(it.code, priorityLabel(it), TaskIcons.priority(it), it.roles().accent) } +
                Choice(ViewDefinition.NONE, priorityLabel(null), null, null),
            state.view.priorities.toSet(),
            viewModel::togglePriority,
        ) { filterSheet = null }
        FilterSheet.Tags -> MultiSelect(
            stringResource(R.string.board_filter_tags),
            state.tags.map { Choice(it.id, it.name, TaskIcons.Tag, it.color.roles().accent) } +
                Choice(ViewDefinition.NONE, stringResource(UiR.string.no_tag), null, null),
            state.view.tagIds.toSet(),
            viewModel::toggleTag,
        ) { filterSheet = null }
        FilterSheet.Project -> MultiSelect(
            stringResource(R.string.board_filter_project),
            listOf(Choice(ViewDefinition.NONE, stringResource(UiR.string.inbox), Icons.Rounded.Workspaces, null)) +
                state.projects.map { Choice(it.id, it.name, Icons.Rounded.Workspaces, it.color.roles().accent) },
            state.view.projectIds.toSet(),
            viewModel::toggleProject,
        ) { filterSheet = null }
        null -> Unit
    }

    statusFor?.let { id ->
        val item = state.groups.flatMap { it.items }.firstOrNull { it.id == id }
        if (item == null) statusFor = null else {
            StatusSheet(item.task.status, onDismiss = { statusFor = null }) { viewModel.actions.setStatus(item, it); statusFor = null }
        }
    }

    if (saving) SaveViewDialog(onDismiss = { saving = false }) { viewModel.saveView(it); saving = false }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.board_search)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, stringResource(UiR.string.action_clear)) } }) else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ViewsRow(state: BoardState, onApply: (ViewDefinition) -> Unit, onDelete: (String) -> Unit) {
    if (state.savedViews.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = false, onClick = { onApply(ViewDefinition(layout = state.view.layout)) }, label = { Text(stringResource(R.string.board_all)) })
        state.savedViews.forEach { saved ->
            val active = saved.definition == state.view
            InputChip(
                selected = active,
                onClick = { onApply(saved.definition) },
                label = { Text(saved.name) },
                // The active view carries its own delete button.
                trailingIcon = if (active) ({
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(R.string.board_delete_view, saved.name),
                        Modifier.size(18.dp).clickable { onDelete(saved.id) },
                    )
                }) else null,
            )
        }
    }
}

/**
 * The four filters as one Material 3 Expressive button group: pressing a button
 * widens it and squeezes its neighbours, and a button stays in its checked shape
 * and colour while its filter is on.
 */
@Composable
private fun FiltersRow(view: ViewDefinition, onOpen: (FilterSheet) -> Unit, onClear: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    val filters = listOf(
        Triple(FilterSheet.Status, stringResource(R.string.board_filter_status), view.statuses.size),
        Triple(FilterSheet.Priority, stringResource(R.string.board_filter_priority), view.priorities.size),
        Triple(FilterSheet.Tags, stringResource(R.string.board_filter_tags), view.tagIds.size),
        Triple(FilterSheet.Project, stringResource(R.string.board_filter_project), view.projectIds.size),
    )
    val clearLabel = stringResource(R.string.board_clear_filters)
    ButtonGroup(
        overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menu) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        for ((sheet, label, count) in filters) {
            val text = if (count > 0) "$label ${count.toString().localizeDigits(persian)}" else label
            customItem(
                buttonGroupContent = {
                    val interaction = remember { MutableInteractionSource() }
                    ToggleButton(
                        checked = count > 0,
                        onCheckedChange = { onOpen(sheet) },
                        interactionSource = interaction,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).animateWidth(interaction),
                    ) {
                        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                menuContent = { menu ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { menu.dismiss(); onOpen(sheet) })
                },
            )
        }
        if (view.hasFilters) {
            customItem(
                buttonGroupContent = {
                    val interaction = remember { MutableInteractionSource() }
                    FilledTonalIconButton(
                        onClick = onClear,
                        interactionSource = interaction,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.animateWidth(interaction),
                    ) { Icon(Icons.Rounded.FilterAltOff, clearLabel) }
                },
                menuContent = { menu ->
                    DropdownMenuItem(text = { Text(clearLabel) }, onClick = { menu.dismiss(); onClear() })
                },
            )
        }
    }
}

@Composable
private fun Summary(state: BoardState) {
    val persian = LocalUiConfig.current.persian
    val parts = listOfNotNull(
        stringResource(R.string.board_summary_total, state.summary.total),
        stringResource(R.string.board_summary_done, state.summary.done),
        state.summary.overdue.takeIf { it > 0 }?.let { stringResource(R.string.board_summary_overdue, it) },
    )
    Text(
        parts.joinToString(" · ").localizeDigits(persian),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun ListLayout(
    state: BoardState,
    viewModel: BoardViewModel,
    onOpenTask: (String) -> Unit,
    onStatusMenu: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val titles = state.groups.associate { it.id to groupTitle(it, state.tags, state.projects) }
    val persian = LocalUiConfig.current.persian
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 104.dp)) {
        for (group in state.groups) {
            val collapsed = group.id in state.collapsed
            if (state.view.groupBy != GroupBy.None) {
                item(key = "h-" + group.id) {
                    GroupHeader(titles.getValue(group.id), group.items.size.toString().localizeDigits(persian), collapsed) { viewModel.toggleCollapsed(group.id) }
                }
            }
            if (!collapsed) {
                itemsIndexed(group.items, key = { _, item -> group.id + "/" + item.id }) { index, item ->
                    SwipeableTaskRow(
                        item = item,
                        onToggle = { viewModel.actions.toggle(item) },
                        onDelete = { viewModel.actions.delete(item) },
                        onClick = { onOpenTask(item.id) },
                        onStatusMenu = { onStatusMenu(item.id) },
                        extras = RowExtras(showProject = state.view.groupBy != GroupBy.Project, timer = state.timer),
                        shape = GroupedShapes.forIndex(index, group.items.size),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: String, collapsed: Boolean, onToggle: () -> Unit) {
    Surface(onClick = onToggle, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f))
            Icon(if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val DRAG_LABEL = "taski-task"

@Composable
private fun BoardLayout(state: BoardState, viewModel: BoardViewModel, onOpenTask: (String) -> Unit, contentPadding: PaddingValues) {
    val persian = LocalUiConfig.current.persian
    val groupsById = state.groups.associateBy { it.id }
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.groups, key = { it.id }) { group ->
            BoardColumn(
                title = groupTitle(group, state.tags, state.projects),
                count = group.items.size.toString().localizeDigits(persian),
                group = group,
                onDrop = { taskId, fromId -> viewModel.moveTo(taskId, fromId?.let(groupsById::get), group) },
            ) {
                items(group.items, key = { it.id }) { item ->
                    TaskRow(
                        item = item,
                        onToggle = { viewModel.actions.toggle(item) },
                        onClick = { onOpenTask(item.id) },
                        extras = RowExtras(showProject = state.view.groupBy != GroupBy.Project, timer = state.timer),
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .animateItem()
                            .dragAndDropSource { _ ->
                                DragAndDropTransferData(ClipData.newPlainText(DRAG_LABEL, "${item.id}|${group.id}"))
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardColumn(
    title: String,
    count: String,
    group: TaskGroup,
    onDrop: (taskId: String, fromGroupId: String?) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        if (hovering) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "column",
    )
    val target = remember(group.id) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val text = event.toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return false
                val taskId = text.substringBefore('|')
                onDrop(taskId, text.substringAfter('|', "").ifEmpty { null })
                return true
            }

            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
        }
    }
    Surface(
        color = container,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.width(304.dp).fillMaxHeight().dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
            target = target,
        ),
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(count, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            if (hovering) {
                Text(stringResource(R.string.board_drop_here), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = content)
        }
    }
}

/**
 * The board's controls and its add button as one Material 3 Expressive floating
 * toolbar, the FAB docked to it, centred over the content.
 */
@Composable
private fun Toolbar(
    view: ViewDefinition,
    onLayout: (ViewLayout) -> Unit,
    onGroup: (GroupBy) -> Unit,
    onSort: (SortKey) -> Unit,
    onToggleSubtasks: () -> Unit,
    onSave: () -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var groupMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Rounded.Add, stringResource(R.string.board_add_task))
            }
        },
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        // One button that shows the layout it switches to.
        val toBoard = view.layout == ViewLayout.List
        IconButton(onClick = { onLayout(if (toBoard) ViewLayout.Board else ViewLayout.List) }) {
            Icon(
                if (toBoard) Icons.Rounded.ViewColumn else Icons.AutoMirrored.Rounded.ViewList,
                stringResource(if (toBoard) R.string.board_layout_board else R.string.board_layout_list),
            )
        }
        Box {
            IconButton(onClick = { groupMenu = true }) { Icon(Icons.Rounded.TableRows, stringResource(R.string.board_group)) }
            DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                GroupBy.entries.forEach { g ->
                    DropdownMenuItem(
                        text = { Text(groupByLabel(g)) },
                        trailingIcon = if (g == view.groupBy) ({ Icon(Icons.Rounded.Check, null) }) else null,
                        onClick = { onGroup(g); groupMenu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.board_show_subtasks)) },
                    trailingIcon = if (view.showSubtasks) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { onToggleSubtasks(); groupMenu = false },
                )
            }
        }
        Box {
            IconButton(onClick = { sortMenu = true }) { Icon(SortIcon, stringResource(R.string.board_sort)) }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortKey.entries.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(key)) },
                        trailingIcon = if (key == view.sortKey) ({
                            Icon(if (view.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                stringResource(if (view.descending) R.string.board_descending else R.string.board_ascending))
                        }) else null,
                        onClick = { onSort(key); sortMenu = false },
                    )
                }
            }
        }
        IconButton(onClick = onSave) { Icon(Icons.Rounded.BookmarkAdd, stringResource(R.string.board_save_view)) }
    }
}

private data class Choice(val value: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val tint: Color?)

@Composable
private fun MultiSelect(title: String, choices: List<Choice>, selected: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    PickerSheet(title, onDismiss) {
        choices.forEachIndexed { index, choice ->
            OptionItem(
                label = choice.label,
                selected = choice.value in selected,
                index = index,
                count = choices.size,
                onClick = { onToggle(choice.value) },
                icon = choice.icon,
                iconTint = choice.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaveViewDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_save_view)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.board_save_view_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}
