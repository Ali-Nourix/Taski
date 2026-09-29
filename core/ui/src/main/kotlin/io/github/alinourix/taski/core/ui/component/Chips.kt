package io.github.alinourix.taski.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.Urgency
import io.github.alinourix.taski.core.ui.format.dueText
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.theme.roles

/**
 * The plugin's chips, as Material pills: every chip is a tint of one accent,
 * so a new kind of chip is one colour choice and inherits the theme.
 */
@Composable
fun MetaChip(
    label: String,
    roles: AccentRoles,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.heightIn(min = 24.dp),
        shape = RoundedCornerShape(8.dp),
        color = roles.container,
        contentColor = roles.onContainer,
    ) {
        Row(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun neutralRoles(): AccentRoles = MaterialTheme.colorScheme.let {
    AccentRoles(it.onSurfaceVariant, it.surface, it.surfaceContainerHighest, it.onSurfaceVariant)
}

@Composable
fun DueChip(task: Task, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val due = dueText(task) ?: return
    val scheme = MaterialTheme.colorScheme
    val roles = when (due.urgency) {
        Urgency.Overdue -> AccentRoles(scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer)
        Urgency.Today -> AccentRoles(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Urgency.Soon -> AccentRoles(scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer)
        Urgency.Later, Urgency.Met -> neutralRoles()
    }
    MetaChip(due.label, roles, modifier, TaskIcons.Due, onClick)
}

@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    MetaChip(priorityLabel(priority), priority.roles(), modifier, TaskIcons.priority(priority), onClick)

@Composable
fun RepeatChip(rule: RepeatRule, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    MetaChip(repeatLabel(rule), neutralRoles(), modifier, TaskIcons.Repeat, onClick)

@Composable
fun TagChip(tag: Tag, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    MetaChip(tag.name, tag.color.roles(), modifier, null, onClick)

@Composable
fun ProgressChip(fraction: Float, modifier: Modifier = Modifier) {
    val persian = LocalUiConfig.current.persian
    val scheme = MaterialTheme.colorScheme
    MetaChip(
        stringResource(R.string.percent, (fraction * 100).toInt()).localizeDigits(persian),
        AccentRoles(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer),
        modifier,
        TaskIcons.Progress,
    )
}

@Composable
fun TimerChip(label: String, running: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val roles = if (running) {
        AccentRoles(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    } else {
        neutralRoles()
    }
    MetaChip(label, roles, modifier, TaskIcons.Timer, onClick)
}

@Composable
fun BlockedChip(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    MetaChip(
        stringResource(R.string.blocked),
        AccentRoles(scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer),
        modifier,
        TaskIcons.Blocked,
    )
}
