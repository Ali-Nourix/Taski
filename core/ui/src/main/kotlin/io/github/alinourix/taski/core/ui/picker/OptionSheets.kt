package io.github.alinourix.taski.core.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.REMINDER_OFFSETS
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.reminderLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.theme.roles

/** A bottom sheet with a title, used by every picker. */
@Composable
fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            content()
        }
    }
}

/** One choice in a segmented group of choices. */
@Composable
fun OptionItem(
    label: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supporting: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = GroupedShapes.forIndex(index, count),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = supporting?.let { { Text(it) } },
            leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = iconTint) } },
            trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun PrioritySheet(current: Priority?, onDismiss: () -> Unit, onPick: (Priority?) -> Unit) {
    PickerSheet(stringResource(R.string.pick_priority), onDismiss) {
        val options: List<Priority?> = Priority.entries + null
        options.forEachIndexed { index, priority ->
            OptionItem(
                label = priorityLabel(priority),
                selected = priority == current,
                index = index,
                count = options.size,
                onClick = { onPick(priority) },
                icon = priority?.let(TaskIcons::priority) ?: Icons.Rounded.RemoveCircleOutline,
                iconTint = priority?.roles()?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusSheet(current: TaskStatus, onDismiss: () -> Unit, onPick: (TaskStatus) -> Unit) {
    PickerSheet(stringResource(R.string.pick_status), onDismiss) {
        val options = listOf(TaskStatus.NotStarted, TaskStatus.InProgress, TaskStatus.Done, TaskStatus.NotDone)
        options.forEachIndexed { index, status ->
            OptionItem(
                label = statusLabel(status),
                selected = status == current,
                index = index,
                count = options.size,
                onClick = { onPick(status) },
                icon = TaskIcons.status(status),
                iconTint = status.roles().accent,
            )
        }
    }
}

@Composable
fun ReminderSheet(current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    PickerSheet(stringResource(R.string.pick_reminder), onDismiss) {
        val options: List<Int?> = REMINDER_OFFSETS + null
        options.forEachIndexed { index, offset ->
            OptionItem(
                label = reminderLabel(offset),
                selected = offset == current,
                index = index,
                count = options.size,
                onClick = { onPick(offset) },
                icon = if (offset == null) Icons.Rounded.NotificationsOff else TaskIcons.Reminder,
            )
        }
    }
}

/** The plugin's presets, plus any interval of days, weeks or months. */
@Composable
fun RepeatSheet(current: RepeatRule?, onDismiss: () -> Unit, onPick: (RepeatRule?) -> Unit) {
    var every by rememberSaveable { mutableStateOf((current?.every ?: 3).toString()) }
    var unit by rememberSaveable { mutableStateOf(current?.unit ?: RepeatUnit.Day) }
    PickerSheet(stringResource(R.string.pick_repeat), onDismiss) {
        val options: List<RepeatRule?> = RepeatRule.PRESETS + null
        options.forEachIndexed { index, rule ->
            OptionItem(
                label = repeatLabel(rule),
                selected = rule == current,
                index = index,
                count = options.size,
                onClick = { onPick(rule) },
                icon = if (rule == null) Icons.Rounded.RemoveCircleOutline else TaskIcons.Repeat,
            )
        }
        Text(stringResource(R.string.repeat_custom), style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.repeat_every))
            OutlinedTextField(
                value = every,
                onValueChange = { value -> every = value.filter(Char::isDigit).take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.size(width = 88.dp, height = 56.dp),
            )
        }
        val unitNames = mapOf(
            RepeatUnit.Day to stringResource(R.string.unit_days),
            RepeatUnit.Week to stringResource(R.string.unit_weeks),
            RepeatUnit.Month to stringResource(R.string.unit_months),
        )
        ConnectedToggleGroup(
            options = RepeatUnit.entries,
            selected = unit,
            onSelect = { unit = it },
            label = unitNames::getValue,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(stringResource(R.string.repeat_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        val parsed = every.toIntOrNull()?.takeIf { it >= 1 }
        Button(
            onClick = { parsed?.let { onPick(RepeatRule(it, unit)) } },
            enabled = parsed != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(TaskIcons.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(parsed?.let { repeatLabel(RepeatRule(it, unit)) } ?: stringResource(R.string.repeat_custom), Modifier.padding(start = 8.dp))
        }
    }
}
