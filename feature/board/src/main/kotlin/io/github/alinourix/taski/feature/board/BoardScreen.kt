package io.github.alinourix.taski.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.model.ViewLayout
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.TaskEventsEffect
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.component.TaskSheetHost
import io.github.alinourix.taski.core.ui.component.rememberTaskSheetState
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.picker.OptionItem
import io.github.alinourix.taski.core.ui.picker.PickerSheet
import io.github.alinourix.taski.core.ui.theme.roles
import io.github.alinourix.taski.core.ui.R as UiR

internal enum class FilterSheet { Status, Priority, Tags, Project }

/**
 * Every task as a database, the plugin board's way: three views of the same
 * rows (list, table, board), filters, grouping and sorting, saved views, and
 * editing in place — a tapped property opens its picker, "+ New" in a group
 * adds a task that already belongs there.
 */
@Composable
fun BoardScreen(
    onOpenTask: (String) -> Unit,
    onManageTags: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTimer: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions, onFocusStarted = onOpenTimer)
    val sheet = rememberTaskSheetState()
    var filterSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val showSearch = searching || query.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.board_title), style = MaterialTheme.typography.titleLargeEmphasized) },
                actions = {
                    IconButton(onClick = { searching = !showSearch; if (!searching) viewModel.search.value = "" }, shapes = IconButtonDefaults.shapes()) {
                        Icon(if (showSearch) Icons.Rounded.SearchOff else Icons.Rounded.Search, stringResource(R.string.board_search_open))
                    }
                    IconButton(onClick = onManageTags, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Sell, stringResource(R.string.board_manage_tags))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                ViewTabs(state.view.layout, viewModel::setLayout)
                AnimatedVisibility(showSearch) { SearchField(query) { viewModel.search.value = it } }
                ViewsRow(state, onApply = viewModel::apply, onDelete = viewModel::deleteView)
                FiltersRow(state.view, onOpen = { filterSheet = it }, onClear = viewModel::clearFilters)
                Summary(state)
                val onEdit = { id: String -> { property: TaskProperty -> sheet.open(id, property) } }
                when {
                    state.loading -> LoadingState()
                    state.totalTasks == 0 -> EmptyState(Icons.Rounded.SpaceDashboard, stringResource(R.string.board_empty), body = stringResource(R.string.board_empty_body))
                    state.groups.all { it.items.isEmpty() } && state.view.groupBy != GroupBy.Status ->
                        EmptyState(Icons.Rounded.FilterAltOff, stringResource(R.string.board_empty_filtered))
                    state.view.layout == ViewLayout.List -> BoardList(state, viewModel, onOpenTask, onEdit, contentPadding)
                    state.view.layout == ViewLayout.Table -> BoardTable(state, viewModel, onOpenTask, onEdit, contentPadding)
                    else -> BoardColumns(state, viewModel, onOpenTask, onEdit, contentPadding)
                }
            }
            Toolbar(
                state.view,
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

    TaskSheetHost(sheet, state::find, state.tags, state.projects, viewModel.actions, onOpenTask)
    if (saving) SaveViewDialog(onDismiss = { saving = false }) { viewModel.saveView(it); saving = false }
}

/** The three views of the same rows, as a connected button group. */
@Composable
private fun ViewTabs(layout: ViewLayout, onSelect: (ViewLayout) -> Unit) {
    val labels = mapOf(
        ViewLayout.List to stringResource(R.string.board_layout_list),
        ViewLayout.Table to stringResource(R.string.board_layout_table),
        ViewLayout.Board to stringResource(R.string.board_layout_board),
    )
    val icons = mapOf(
        ViewLayout.List to Icons.AutoMirrored.Rounded.ViewList,
        ViewLayout.Table to Icons.Rounded.TableChart,
        ViewLayout.Board to Icons.Rounded.ViewColumn,
    )
    ConnectedToggleGroup(
        options = ViewLayout.entries,
        selected = layout,
        onSelect = onSelect,
        label = labels::getValue,
        icon = icons::getValue,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        height = 36.dp,
    )
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
        shape = MaterialTheme.shapes.medium,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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
                        modifier = Modifier.weight(1f).height(34.dp).animateWidth(interaction),
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
                        modifier = Modifier.size(34.dp).animateWidth(interaction),
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
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * The board's controls and its add button as one Material 3 Expressive floating
 * toolbar, the FAB docked to it, centred over the content.
 */
@Composable
private fun Toolbar(
    view: ViewDefinition,
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
