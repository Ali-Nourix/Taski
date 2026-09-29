package io.github.alinourix.taski.feature.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.DueChip
import io.github.alinourix.taski.core.ui.component.NewTaskRow
import io.github.alinourix.taski.core.ui.component.PriorityChip
import io.github.alinourix.taski.core.ui.component.PropertyToken
import io.github.alinourix.taski.core.ui.component.StatusChip
import io.github.alinourix.taski.core.ui.component.TagChip
import io.github.alinourix.taski.core.ui.component.TaskCheckbox
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.R as UiR

private val TitleWidth = 196.dp
private val RowHeight = 48.dp

/** The table's property columns, in the plugin's order. Tags have no sort; everything else sorts by its header. */
private enum class TableColumn(val width: Dp, val sort: SortKey?, val icon: ImageVector) {
    Status(136.dp, SortKey.Status, Icons.Rounded.Timelapse),
    Due(132.dp, SortKey.Deadline, TaskIcons.Due),
    Priority(120.dp, SortKey.Priority, Icons.Rounded.KeyboardDoubleArrowUp),
    Tags(176.dp, null, TaskIcons.Tag),
    Progress(124.dp, SortKey.Progress, TaskIcons.Progress),
    Project(148.dp, SortKey.Project, Icons.Rounded.Folder),
}

@Composable
private fun TableColumn.label(): String = stringResource(
    when (this) {
        TableColumn.Status -> R.string.col_status
        TableColumn.Due -> R.string.col_due
        TableColumn.Priority -> R.string.col_priority
        TableColumn.Tags -> R.string.col_tags
        TableColumn.Progress -> R.string.col_progress
        TableColumn.Project -> R.string.col_project
    },
)

/**
 * The table view, as on the plugin's desktop board: the task name frozen at the
 * start, every property in its own column scrolling sideways together, a header
 * that sorts when tapped, and each cell opening its own picker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoardTable(
    state: BoardState,
    viewModel: BoardViewModel,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    contentPadding: PaddingValues,
) {
    // One scroll position for every row, so the columns move as one sheet.
    val scroll = rememberScrollState()
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val grouped = state.view.groupBy != GroupBy.None
    // The grouped property is already each group's header; its column would only repeat it.
    val grouping = when (state.view.groupBy) {
        GroupBy.Status -> TableColumn.Status
        GroupBy.Priority -> TableColumn.Priority
        GroupBy.Tag -> TableColumn.Tags
        GroupBy.Project -> TableColumn.Project
        GroupBy.Deadline, GroupBy.None -> null
    }
    val columns = TableColumn.entries.filter { it != grouping }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 104.dp)) {
        stickyHeader(key = "columns", contentType = "columns") {
            HeaderRow(state.view, columns, scroll, onSort = viewModel::setSort)
        }
        for (group in state.groups) {
            val collapsed = grouped && group.id in state.collapsed
            if (grouped) {
                item(key = "h-" + group.id, contentType = "header") {
                    GroupHeader(
                        group = group,
                        state = state,
                        collapsed = collapsed,
                        onToggle = { viewModel.toggleCollapsed(group.id) },
                        onAdd = {
                            if (collapsed) viewModel.toggleCollapsed(group.id)
                            adding = group.id
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (collapsed) continue
            items(group.items, key = { group.id + "/" + it.id }, contentType = { "row" }) { item ->
                TableRow(
                    item = item,
                    columns = columns,
                    scroll = scroll,
                    onToggle = { viewModel.actions.toggle(item) },
                    onOpen = { onOpenTask(item.id) },
                    onEdit = onEdit(item.id),
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "n-" + group.id, contentType = "new") {
                Column(Modifier.animateItem()) {
                    NewTaskRow(
                        open = adding == group.id,
                        onOpenChange = { adding = if (it) group.id else null },
                        onAdd = { viewModel.addTask(it, group) },
                        padding = PaddingValues(start = 14.dp, end = 4.dp),
                        iconGap = 10.dp,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(view: ViewDefinition, columns: List<TableColumn>, scroll: ScrollState, onSort: (SortKey) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.height(36.dp)) {
            HeaderCell(stringResource(R.string.col_task), Icons.Rounded.Title, view, SortKey.Title, onSort, Modifier.width(TitleWidth))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                columns.forEach { column ->
                    HeaderCell(column.label(), column.icon, view, column.sort, onSort, Modifier.width(column.width))
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.width(24.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun HeaderCell(label: String, icon: ImageVector, view: ViewDefinition, key: SortKey?, onSort: (SortKey) -> Unit, modifier: Modifier) {
    val active = key != null && view.sortKey == key
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxHeight()
            .then(if (key != null) Modifier.clickable { onSort(key) } else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (active) {
            Icon(
                if (view.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(if (view.descending) R.string.board_descending else R.string.board_ascending),
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TableRow(
    item: TaskItem,
    columns: List<TableColumn>,
    scroll: ScrollState,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEdit: (TaskProperty) -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = item.task
    val done = task.status == TaskStatus.Done
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.height(RowHeight)) {
            Row(
                modifier = Modifier
                    .width(TitleWidth)
                    .fillMaxHeight()
                    .combinedClickable(onClick = onOpen, onLongClick = { onEdit(TaskProperty.Menu) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCheckbox(task.status, onToggle, priority = task.priority, onLongPress = { onEdit(TaskProperty.Status) }, size = 18.dp)
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
            VerticalDivider(color = outline)
            Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                columns.forEach { column ->
                    Cell(column.width, onClick = { onCellClick(column, onEdit, onOpen) }) { CellContent(column, item) }
                    VerticalDivider(color = outline)
                }
                Spacer(Modifier.width(24.dp))
            }
        }
        HorizontalDivider(color = outline)
    }
}

private fun onCellClick(column: TableColumn, onEdit: (TaskProperty) -> Unit, onOpen: () -> Unit) = when (column) {
    TableColumn.Status -> onEdit(TaskProperty.Status)
    TableColumn.Due -> onEdit(TaskProperty.Due)
    TableColumn.Priority -> onEdit(TaskProperty.Priority)
    TableColumn.Tags -> onEdit(TaskProperty.Tags)
    // Steps are ticked on the task's page.
    TableColumn.Progress -> onOpen()
    TableColumn.Project -> onEdit(TaskProperty.Project)
}

@Composable
private fun Cell(width: Dp, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.width(width).fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 8.dp).clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun RowScope.CellContent(column: TableColumn, item: TaskItem) {
    val task = item.task
    val persian = LocalUiConfig.current.persian
    when (column) {
        TableColumn.Status -> StatusChip(task.status)
        TableColumn.Due -> DueChip(task)
        TableColumn.Priority -> task.priority?.let { PriorityChip(it) }
        TableColumn.Tags -> item.tags.forEach { TagChip(it) }
        TableColumn.Progress -> item.progressFraction?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.width(52.dp),
            )
            Text(
                stringResource(UiR.string.percent, (fraction * 100).toInt()).localizeDigits(persian),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TableColumn.Project -> item.project?.let { PropertyToken(it.name, icon = Icons.Rounded.Folder) }
    }
    Box(Modifier.weight(1f))
}
