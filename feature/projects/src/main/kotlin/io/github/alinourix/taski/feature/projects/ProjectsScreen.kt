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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
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
                )
            }
            item(key = "header") { SectionHeader(stringResource(R.string.projects_title), count = order.size) }
            if (order.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.FolderOpen, stringResource(R.string.projects_empty), body = stringResource(R.string.projects_empty_body))
                }
            }
            items(order, key = { it.project!!.id }) { summary ->
                val project = summary.project!!
                ReorderableItem(reorder, key = project.id) { dragging ->
                    PageRow(
                        summary = summary,
                        title = project.name,
                        icon = { PageIcon(project.name, project.color.roles()) },
                        onClick = { onOpenProject(project.id) },
                        modifier = (if (dragging) Modifier.shadow(6.dp, MaterialTheme.shapes.small) else Modifier)
                            .longPressDraggableHandle(onDragStopped = {
                                val i = order.indexOfFirst { it.project?.id == project.id }
                                viewModel.move(project.id, order.getOrNull(i - 1)?.project?.id, order.getOrNull(i + 1)?.project?.id)
                            }),
                    )
                }
            }
            item(key = "new") { NewProjectRow { creating = true } }
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
) {
    val persian = LocalUiConfig.current.persian
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.projects_open_count, summary.open, summary.done).localizeDigits(persian),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.open + summary.done > 0) {
                CircularProgressIndicator(
                    progress = { summary.fraction },
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NewProjectRow(onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = muted, modifier = Modifier.size(20.dp)) }
        Text(stringResource(R.string.projects_new), style = MaterialTheme.typography.bodyLarge, color = muted)
    }
}

/** A page icon: the project's first letter on its pastel, with crisp corners. */
@Composable
internal fun PageIcon(name: String, roles: AccentRoles, size: Dp = 28.dp) {
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.small).background(roles.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * 0.5f).sp),
            color = roles.onContainer,
        )
    }
}

@Composable
internal fun InboxIcon(size: Dp = 28.dp) {
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.6f))
    }
}
