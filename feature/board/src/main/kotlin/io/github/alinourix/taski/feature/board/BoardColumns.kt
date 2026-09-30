package io.github.alinourix.taski.feature.board

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.query.TaskGroup
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.NewTaskRow
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.RowStyle
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.component.TaskRow
import io.github.alinourix.taski.core.ui.format.localizeDigits

/**
 * The board view: one column per group, cards that are picked up with a
 * long-press and dropped on another column to take its value, and "+ New" at
 * the foot of each column.
 */
@Composable
internal fun BoardColumns(
    state: BoardState,
    viewModel: BoardViewModel,
    onOpenTask: (String) -> Unit,
    onEdit: (String) -> (TaskProperty) -> Unit,
    contentPadding: PaddingValues,
) {
    val groupsById = state.groups.associateBy { it.id }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val extras = RowExtras(showProject = state.view.groupBy != GroupBy.Project, timer = state.timer)
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.groups, key = { it.id }) { group ->
            BoardColumn(
                group = group,
                state = state,
                adding = adding == group.id,
                onAddingChange = { adding = if (it) group.id else null },
                onAdd = { viewModel.addTask(it, group) },
                onDrop = { taskId, fromId -> viewModel.moveTo(taskId, fromId?.let(groupsById::get), group) },
            ) {
                items(group.items, key = { it.id }) { item ->
                    TaskRow(
                        item = item,
                        onToggle = { viewModel.actions.toggle(item) },
                        onClick = { onOpenTask(item.id) },
                        onEdit = onEdit(item.id),
                        extras = extras,
                        style = RowStyle.Card,
                        longPressMenu = false,
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
    group: TaskGroup,
    state: BoardState,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onDrop: (taskId: String, fromGroupId: String?) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val persian = LocalUiConfig.current.persian
    var hovering by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (hovering) MaterialTheme.colorScheme.primary else Color.Transparent,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "column",
    )
    val target = remember(group.id) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val text = event.toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return false
                onDrop(text.substringBefore('|'), text.substringAfter('|', "").ifEmpty { null })
                return true
            }

            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.5.dp, border),
        modifier = Modifier.width(280.dp).fillMaxHeight().dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
            target = target,
        ),
    ) {
        Column {
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupLabel(group, state)
                Text(
                    group.items.size.toString().localizeDigits(persian),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f))
                IconButton(onClick = { onAddingChange(true) }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.board_new), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            if (hovering) {
                Text(
                    stringResource(R.string.board_drop_here),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                content()
                item(key = "new") {
                    NewTaskRow(
                        open = adding,
                        onOpenChange = onAddingChange,
                        onAdd = onAdd,
                        padding = PaddingValues(start = 6.dp, end = 0.dp),
                        iconGap = 8.dp,
                    )
                }
            }
        }
    }
}
