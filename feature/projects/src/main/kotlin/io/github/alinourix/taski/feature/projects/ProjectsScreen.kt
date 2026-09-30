package io.github.alinourix.taski.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
import io.github.alinourix.taski.core.designsystem.component.ShapeIcon
import io.github.alinourix.taski.core.designsystem.component.ShapeLetter
import io.github.alinourix.taski.core.designsystem.component.sectionRow
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.theme.roles
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.alinourix.taski.core.ui.R as UiR

/**
 * Projects as a list of pages, the way a workspace sidebar lists them: an
 * icon in the project's colour, the name, what is left, and a small ring for
 * how far along it is. Long-press a project to move it.
 */
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
                    IconButton(onClick = { creating = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.projects_new))
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp),
        ) {
            item(key = "inbox") {
                PageRow(
                    summary = state.inbox,
                    title = stringResource(UiR.string.inbox),
                    icon = { InboxIcon() },
                    onClick = { onOpenProject(null) },
                    showDivider = false,
                    modifier = Modifier.sectionRow(0, 1),
                )
            }
            item(key = "header") { SectionHeader(stringResource(R.string.projects_title), count = order.size, modifier = Modifier.padding(start = 12.dp)) }
            if (order.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.FolderOpen, stringResource(R.string.projects_empty), body = stringResource(R.string.projects_empty_body))
                }
            }
            itemsIndexed(order, key = { _, it -> it.project!!.id }) { index, summary ->
                val project = summary.project!!
                ReorderableItem(reorder, key = project.id) { dragging ->
                    PageRow(
                        summary = summary,
                        title = project.name,
                        icon = { PageIcon(project.id, project.name, project.color.roles()) },
                        onClick = { onOpenProject(project.id) },
                        modifier = (if (dragging) Modifier.shadow(6.dp, MaterialTheme.shapes.large) else Modifier)
                            .sectionRow(index, order.size + 1)
                            .longPressDraggableHandle(onDragStopped = {
                                val i = order.indexOfFirst { it.project?.id == project.id }
                                viewModel.move(project.id, order.getOrNull(i - 1)?.project?.id, order.getOrNull(i + 1)?.project?.id)
                            }),
                    )
                }
            }
            item(key = "new") { NewProjectRow(Modifier.sectionRow(order.size, order.size + 1)) { creating = true } }
        }
    }

    if (creating) {
        ProjectDialog(stringResource(R.string.projects_new), "", viewModel.nextColor(), onDismiss = { creating = false }) { name, color ->
            viewModel.create(name, color); creating = false
        }
    }
}

@Composable
private fun PageRow(
    summary: ProjectSummary,
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val persian = LocalUiConfig.current.persian
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.projects_open_count, summary.open, summary.done).localizeDigits(persian),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.open + summary.done > 0) {
                CircularProgressIndicator(
                    progress = { summary.fraction },
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 76.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NewProjectRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = muted, modifier = Modifier.size(22.dp)) }
        Text(stringResource(R.string.projects_new), style = MaterialTheme.typography.titleMedium, color = muted)
    }
}

private val SHAPES = listOf(
    MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon,
    MaterialShapes.Cookie9Sided, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst,
)

/** A project keeps its shape for good: it comes from the id, not from where the project sits in the list. */
internal fun shapeFor(projectId: String) = SHAPES[(projectId.hashCode() and Int.MAX_VALUE) % SHAPES.size]

/** A project's page icon: its initial in one of the expressive shapes, on its own pastel. */
@Composable
internal fun PageIcon(projectId: String, name: String, roles: AccentRoles, size: Dp = 44.dp) {
    ShapeLetter(name.trim().take(1).uppercase(), polygon = shapeFor(projectId), containerColor = roles.container, contentColor = roles.onContainer, size = size)
}

@Composable
internal fun InboxIcon(size: Dp = 44.dp) {
    ShapeIcon(
        Icons.Rounded.Inbox,
        polygon = MaterialShapes.Square,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        size = size,
    )
}
