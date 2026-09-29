package io.github.alinourix.taski.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.ShapeIcon
import io.github.alinourix.taski.core.designsystem.component.ShapeLetter
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.theme.roles
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.alinourix.taski.core.ui.R as UiR

private val SHAPES = listOf(
    MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon,
    MaterialShapes.Cookie9Sided, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst,
)

@Composable
fun ProjectsScreen(
    onOpenProject: (String?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.projects_title)) },
                actions = {
                    FilledTonalButton(onClick = { creating = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Rounded.Add, null)
                        Text(stringResource(R.string.projects_new), Modifier.padding(start = 6.dp))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        var order by remember(state.projects) { mutableStateOf(state.projects) }
        val listState = rememberLazyListState()
        val reorder = rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = order.indexOfFirst { it.project?.id == from.key }
            val toIndex = order.indexOfFirst { it.project?.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap),
        ) {
            item(key = "inbox") {
                ProjectCard(
                    summary = state.inbox,
                    title = stringResource(UiR.string.inbox),
                    index = 0,
                    count = 1,
                    icon = { ShapeIcon(Icons.Rounded.Inbox, polygon = MaterialShapes.Square, size = 48.dp) },
                    onClick = { onOpenProject(null) },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            if (order.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.FolderOpen, stringResource(R.string.projects_empty), body = stringResource(R.string.projects_empty_body))
                }
            }
            itemsIndexed(order, key = { _, summary -> summary.project!!.id }) { index, summary ->
                val project = summary.project!!
                ReorderableItem(reorder, key = project.id) { dragging ->
                    val roles = project.color.roles()
                    ProjectCard(
                        summary = summary,
                        title = project.name,
                        index = index,
                        count = order.size,
                        icon = { ShapeLetter(project.name.take(1).uppercase(), polygon = SHAPES[index % SHAPES.size], containerColor = roles.container, contentColor = roles.onContainer, size = 48.dp) },
                        onClick = { onOpenProject(project.id) },
                        modifier = if (dragging) Modifier.shadow(8.dp, GroupedShapes.forIndex(index, order.size)) else Modifier,
                        trailing = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(onDragStopped = {
                                    val i = order.indexOfFirst { it.project?.id == project.id }
                                    viewModel.move(project.id, order.getOrNull(i - 1)?.project?.id, order.getOrNull(i + 1)?.project?.id)
                                }),
                            ) { Icon(Icons.Rounded.DragIndicator, stringResource(R.string.projects_reorder)) }
                        },
                    )
                }
            }
        }
    }

    if (creating) {
        ProjectDialog(stringResource(R.string.projects_new), "", viewModel.nextColor(), onDismiss = { creating = false }) { name, color ->
            viewModel.create(name, color); creating = false
        }
    }
}

@Composable
private fun ProjectCard(
    summary: ProjectSummary,
    title: String,
    index: Int,
    count: Int,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val persian = LocalUiConfig.current.persian
    Surface(onClick = onClick, shape = GroupedShapes.forIndex(index, count), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(R.string.projects_open_count, summary.open, summary.done).localizeDigits(persian),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.open + summary.done > 0) {
                    LinearWavyProgressIndicator(progress = { summary.fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
            trailing?.invoke()
        }
    }
}
