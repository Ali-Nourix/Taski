package io.github.alinourix.taski.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
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
import androidx.compose.material3.ToggleButtonDefaults
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
    onOpenTimer: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions, onFocusStarted = onOpenTimer)
    val sheet = rememberTaskSheetState()
    val timeline = rememberTimelineState(LocalUiConfig.current.today)
    val unscheduled = remember(state.groups) { unscheduledOf(state).size }
    var filterSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val showSearch = searching || query.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    ViewTitle(
                        state = state,
                        onLayout = viewModel::setLayout,
                        onApply = viewModel::apply,
                        onDeleteView = viewModel::deleteView,
                        onSave = { saving = true },
                    )
                },
                actions = {
                    IconButton(onClick = { searching = !showSearch; if (!searching) viewModel.search.value = "" }, shapes = IconButtonDefaults.shapes()) {
                        Icon(if (showSearch) Icons.Rounded.SearchOff else Icons.Rounded.Search, stringResource(R.string.board_search_open))
                    }
                    IconButton(onClick = { optionsOpen = true }, shapes = IconButtonDefaults.shapes()) {
                        BadgedBox(badge = { if (state.view.hasFilters) Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                            Icon(Icons.Rounded.Tune, stringResource(R.string.board_view_options))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AnimatedVisibility(showSearch) { SearchField(query) { viewModel.search.value = it } }
            val onEdit = { id: String -> { property: TaskProperty -> sheet.open(id, property) } }
            when {
                state.loading -> LoadingState()
                state.totalTasks == 0 -> EmptyState(Icons.Rounded.SpaceDashboard, stringResource(R.string.board_empty), body = stringResource(R.string.board_empty_body))
                state.groups.all { it.items.isEmpty() } && state.view.groupBy != GroupBy.Status ->
                    EmptyState(Icons.Rounded.FilterAltOff, stringResource(R.string.board_empty_filtered))
                state.view.layout == ViewLayout.List -> BoardList(state, viewModel, onOpenTask, onEdit, contentPadding)
                state.view.layout == ViewLayout.Table -> BoardTable(state, viewModel, onOpenTask, onEdit, contentPadding)
                state.view.layout == ViewLayout.Timeline -> BoardTimeline(state, timeline, viewModel, onOpenTask, onEdit, contentPadding)
                else -> BoardColumns(state, viewModel, onOpenTask, onEdit, contentPadding)
            }
        }
    }

    if (optionsOpen) {
        ViewSheet(
            state = state,
            unscheduled = unscheduled,
            onDismiss = { optionsOpen = false },
            onFilter = { optionsOpen = false; filterSheet = it },
            onClearFilters = viewModel::clearFilters,
            onGroup = viewModel::setGroupBy,
            onSort = viewModel::setSort,
            onToggleSubtasks = viewModel::toggleSubtasks,
            onManageTags = { optionsOpen = false; onManageTags() },
            onUnscheduled = { optionsOpen = false; timeline.unscheduledOpen = true },
        )
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
private fun SummaryText(state: BoardState) {
    val persian = LocalUiConfig.current.persian
    val parts = listOfNotNull(
        stringResource(R.string.board_summary_total, state.summary.total),
        stringResource(R.string.board_summary_done, state.summary.done),
        state.summary.overdue.takeIf { it > 0 }?.let { stringResource(R.string.board_summary_overdue, it) },
    )
    Text(parts.joinToString(" · ").localizeDigits(persian), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun layoutLabel(layout: ViewLayout): String = stringResource(
    when (layout) {
        ViewLayout.List -> R.string.board_layout_list
        ViewLayout.Table -> R.string.board_layout_table
        ViewLayout.Board -> R.string.board_layout_board
        ViewLayout.Timeline -> R.string.board_layout_timeline
    },
)

private fun layoutIcon(layout: ViewLayout) = when (layout) {
    ViewLayout.List -> Icons.AutoMirrored.Rounded.ViewList
    ViewLayout.Table -> Icons.Rounded.TableChart
    ViewLayout.Board -> Icons.Rounded.ViewColumn
    ViewLayout.Timeline -> Icons.Rounded.Timeline
}

/**
 * The screen's title is its view switcher: what you are looking at, with a caret. One tap lists the four views
 * and the saved ones, so there is no row of tabs taking a line of the screen.
 */
@Composable
private fun ViewTitle(
    state: BoardState,
    onLayout: (ViewLayout) -> Unit,
    onApply: (ViewDefinition) -> Unit,
    onDeleteView: (String) -> Unit,
    onSave: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val layout = state.view.layout
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.medium).clickable { open = true }.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(layoutLabel(layout), style = MaterialTheme.typography.titleLargeEmphasized)
                    Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.board_switch_view))
                }
                SummaryText(state)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewLayout.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(layoutLabel(entry)) },
                    leadingIcon = { Icon(layoutIcon(entry), null) },
                    trailingIcon = if (entry == layout) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { onLayout(entry); open = false },
                )
            }
            if (state.savedViews.isNotEmpty()) {
                HorizontalDivider()
                state.savedViews.forEach { saved ->
                    val active = saved.definition == state.view
                    DropdownMenuItem(
                        text = { Text(saved.name) },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, null) },
                        trailingIcon = if (active) ({
                            IconButton(onClick = { onDeleteView(saved.id); open = false }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.board_delete_view, saved.name))
                            }
                        }) else null,
                        onClick = { onApply(saved.definition); open = false },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.board_save_view)) },
                leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null) },
                onClick = { open = false; onSave() },
            )
        }
    }
}

/**
 * Everything that shapes the view, in one place instead of one row each: what to show (filters), how to
 * arrange it (group, sort) and whether subtasks appear. Reached by one icon; closed, it costs nothing.
 */
@Composable
private fun ViewSheet(
    state: BoardState,
    unscheduled: Int,
    onDismiss: () -> Unit,
    onFilter: (FilterSheet) -> Unit,
    onClearFilters: () -> Unit,
    onGroup: (GroupBy) -> Unit,
    onSort: (SortKey) -> Unit,
    onToggleSubtasks: () -> Unit,
    onManageTags: () -> Unit,
    onUnscheduled: () -> Unit,
) {
    val view = state.view
    val persian = LocalUiConfig.current.persian
    val any = stringResource(R.string.board_filter_any)
    val filters = listOf(
        Triple(FilterSheet.Status, R.string.board_filter_status, view.statuses.size),
        Triple(FilterSheet.Priority, R.string.board_filter_priority, view.priorities.size),
        Triple(FilterSheet.Tags, R.string.board_filter_tags, view.tagIds.size),
        Triple(FilterSheet.Project, R.string.board_filter_project, view.projectIds.size),
    )
    PickerSheet(stringResource(R.string.board_view_options), onDismiss) {
        SheetLabel(stringResource(R.string.board_section_filter))
        filters.forEachIndexed { index, (sheet, label, count) ->
            OptionItem(
                label = stringResource(label),
                selected = count > 0,
                index = index,
                count = filters.size,
                onClick = { onFilter(sheet) },
                supporting = if (count == 0) any else stringResource(R.string.board_filter_count, count).localizeDigits(persian),
            )
        }
        if (view.hasFilters) TextButton(onClick = onClearFilters) { Text(stringResource(R.string.board_clear_filters)) }

        if (view.layout != ViewLayout.Timeline) {
            SheetLabel(stringResource(R.string.board_section_group))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupBy.entries.forEach { g -> FilterChip(selected = g == view.groupBy, onClick = { onGroup(g) }, label = { Text(groupByLabel(g)) }) }
            }
            SheetLabel(stringResource(R.string.board_section_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortKey.entries.forEach { key ->
                    val active = key == view.sortKey
                    FilterChip(
                        selected = active,
                        onClick = { onSort(key) },
                        label = { Text(sortLabel(key)) },
                        trailingIcon = if (active && key != SortKey.Manual) ({
                            Icon(
                                if (view.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                stringResource(if (view.descending) R.string.board_descending else R.string.board_ascending),
                                Modifier.size(16.dp),
                            )
                        }) else null,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.board_show_subtasks), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = view.showSubtasks, onCheckedChange = { onToggleSubtasks() })
        }
        if (view.layout == ViewLayout.Timeline) {
            TextButton(onClick = onUnscheduled) { Text(stringResource(R.string.timeline_unscheduled) + if (unscheduled > 0) " · " + unscheduled.toString().localizeDigits(persian) else "") }
        }
        TextButton(onClick = onManageTags) { Text(stringResource(R.string.board_manage_tags)) }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp))
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
