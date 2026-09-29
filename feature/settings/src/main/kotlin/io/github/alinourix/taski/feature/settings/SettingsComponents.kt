package io.github.alinourix.taski.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.SectionHeader

/** A titled group of settings rows drawn as one segmented card. */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionHeader(title)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap), content = content)
}

@Composable
internal fun SettingRow(
    index: Int,
    count: Int,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val shape = GroupedShapes.forIndex(index, count)
    val body = @Composable {
        Column {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = supporting?.let { { Text(it) } },
                leadingContent = icon?.let { { Icon(it, null) } },
                trailingContent = trailing,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            below?.let { Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { it() } }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) { body() }
    }
}

@Composable
internal fun SwitchRow(index: Int, count: Int, title: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null, icon: ImageVector? = null) =
    SettingRow(index, count, title, supporting = supporting, icon = icon, onClick = { onChange(!checked) }, trailing = { Switch(checked = checked, onCheckedChange = onChange) })

@Composable
internal fun NavRow(index: Int, count: Int, title: String, supporting: String?, icon: ImageVector, onClick: () -> Unit) =
    SettingRow(index, count, title, supporting = supporting, icon = icon, onClick = onClick, trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) })

/** A number with − and + buttons. */
@Composable
internal fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Icon(Icons.Rounded.Remove, null) }
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Icon(Icons.Rounded.Add, null) }
    }
}
