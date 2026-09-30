package io.github.alinourix.taski.feature.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.theme.Hue
import io.github.alinourix.taski.core.designsystem.theme.TaskiTheme
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.query.TaskGroup
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.MetaChip
import io.github.alinourix.taski.core.ui.component.StatusChip
import io.github.alinourix.taski.core.ui.component.TagChip
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.theme.roles

internal const val DRAG_LABEL = "taski-task"

/**
 * A group's name the way its rows show the value: a status or tag as its own
 * coloured option, a date bucket as plain text. The header reads like the
 * property it groups by.
 */
@Composable
internal fun GroupLabel(group: TaskGroup, state: BoardState) {
    val none = @Composable { text: String -> MetaChip(text, Hue.Gray.roles(TaskiTheme.isDark)) }
    when (group.groupBy) {
        GroupBy.Status -> StatusChip(TaskStatus.fromCode(group.value))
        GroupBy.Priority -> Priority.fromCode(group.value)?.let { MetaChip(groupTitle(group, state.tags, state.projects), it.roles(), icon = TaskIcons.priority(it)) }
            ?: none(groupTitle(group, state.tags, state.projects))
        GroupBy.Tag -> state.tags.firstOrNull { it.id == group.value }?.let { TagChip(it) } ?: none(groupTitle(group, state.tags, state.projects))
        GroupBy.Project -> state.projects.firstOrNull { it.id == group.value }?.let { MetaChip(it.name, it.color.roles(), icon = Icons.Rounded.Folder) }
            ?: none(groupTitle(group, state.tags, state.projects))
        GroupBy.Deadline, GroupBy.None -> Text(
            groupTitle(group, state.tags, state.projects),
            style = MaterialTheme.typography.titleSmall,
            color = when (group.value) {
                "overdue" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** A group's header in the list and the table: fold chevron, the value, a count, and "+" to add inside it. */
@Composable
internal fun GroupHeader(
    group: TaskGroup,
    state: BoardState,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val persian = LocalUiConfig.current.persian
    val turn by animateFloatAsState(if (collapsed) 0f else 90f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 2.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).rotate(turn),
        )
        GroupLabel(group, state)
        Text(
            group.items.size.toString().localizeDigits(persian),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f))
        IconButton(onClick = onAdd, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Add, stringResource(R.string.board_new), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
