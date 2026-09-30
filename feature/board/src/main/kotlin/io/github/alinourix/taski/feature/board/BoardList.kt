package io.github.alinourix.taski.feature.board

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.component.sectionRow
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.ui.component.NewTaskRow
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.SwipeableTaskRow
import io.github.alinourix.taski.core.ui.component.TaskProperty

/**
 * The list view: groups as foldable sections, each task one line on the page
 * with its properties editable in place, and "+ New" at the foot of each group.
 */
@Composable
internal fun BoardList(
    state: BoardState,
    viewModel: BoardViewModel,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    contentPadding: PaddingValues,
) {
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val grouped = state.view.groupBy != GroupBy.None
    val extras = RowExtras(showProject = state.view.groupBy != GroupBy.Project, timer = state.timer)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 104.dp)) {
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
            // The group's tasks and its "+ New" line are one rounded container.
            val slices = group.items.size + 1
            itemsIndexed(group.items, key = { _, it -> group.id + "/" + it.id }, contentType = { _, _ -> "task" }) { index, item ->
                SwipeableTaskRow(
                    item = item,
                    onToggle = { viewModel.actions.toggle(item) },
                    onDelete = { viewModel.actions.delete(item) },
                    onClick = { onOpenTask(item.id) },
                    onEdit = onEdit(item.id),
                    extras = extras,
                    modifier = Modifier.animateItem().sectionRow(index, slices),
                )
            }
            item(key = "n-" + group.id, contentType = "new") {
                NewTaskRow(
                    open = adding == group.id,
                    onOpenChange = { adding = if (it) group.id else null },
                    onAdd = { viewModel.addTask(it, group) },
                    modifier = Modifier.animateItem().sectionRow(slices - 1, slices),
                    // The plus sits on the checkbox column, the text on the title's.
                    padding = PaddingValues(start = 18.dp, end = 4.dp),
                )
            }
        }
    }
}
