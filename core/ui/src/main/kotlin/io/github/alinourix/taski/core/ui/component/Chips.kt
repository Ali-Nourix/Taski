package io.github.alinourix.taski.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.Urgency
import io.github.alinourix.taski.core.ui.format.dueText
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.theme.roles

/**
 * A property shown the quiet way: a small icon and grey text, no container.
 * Colour appears only when it means something (an overdue date, a priority's
 * icon). Tappable when [onClick] is given, so the property is edited where it
 * is read.
 */
@Composable
fun PropertyToken(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A filled pill for values that are themselves coloured: tags, statuses. Crisp corners, pastel fill. */
@Composable
fun MetaChip(
    label: String,
    roles: AccentRoles,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.heightIn(min = 22.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = roles.container,
        contentColor = roles.onContainer,
    ) {
        Row(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(13.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun dueColor(urgency: Urgency): Color = when (urgency) {
    Urgency.Overdue -> MaterialTheme.colorScheme.error
    Urgency.Today -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun DueChip(task: Task, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val due = dueText(task) ?: return
    val color = dueColor(due.urgency)
    PropertyToken(due.label, modifier, TaskIcons.Due, iconTint = color, textColor = color, onClick = onClick)
}

@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    PropertyToken(priorityLabel(priority), modifier, TaskIcons.priority(priority), iconTint = priority.roles().accent, onClick = onClick)

@Composable
fun RepeatChip(rule: RepeatRule, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    PropertyToken(repeatLabel(rule), modifier, TaskIcons.Repeat, onClick = onClick)

@Composable
fun TagChip(tag: Tag, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    MetaChip(tag.name, tag.color.roles(), modifier, null, onClick)

@Composable
fun StatusChip(status: TaskStatus, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val roles = status.roles()
    MetaChip(statusLabel(status), roles, modifier, TaskIcons.status(status), onClick)
}

@Composable
fun ProgressChip(fraction: Float, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val persian = LocalUiConfig.current.persian
    PropertyToken(
        stringResource(R.string.percent, (fraction * 100).toInt()).localizeDigits(persian),
        modifier,
        TaskIcons.Progress,
        iconTint = MaterialTheme.colorScheme.primary,
        onClick = onClick,
    )
}

@Composable
fun TimerChip(label: String, running: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    PropertyToken(label, modifier, TaskIcons.Timer, iconTint = color, textColor = color, onClick = onClick)
}

@Composable
fun BlockedChip(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    PropertyToken(stringResource(R.string.blocked), modifier, TaskIcons.Blocked, iconTint = color, textColor = color)
}
