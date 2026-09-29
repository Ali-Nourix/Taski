package io.github.alinourix.taski.core.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.component.TagChip

/**
 * The plugin's Notion-like multi-select: type to filter, tap to toggle, and a
 * name that does not exist yet offers to create it on the spot, with a colour.
 */
@Composable
fun TagPickerSheet(
    tags: List<Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onCreate: (String, ColorToken) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val filtered = remember(tags, trimmed) { tags.filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) } }
    val exact = tags.any { it.name.equals(trimmed, ignoreCase = true) }
    var newColor by remember(tags) { mutableStateOf(ColorToken.next(tags.map { it.color })) }

    PickerSheet(stringResource(R.string.pick_tags), onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.tag_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                when {
                    trimmed.isNotEmpty() && !exact -> { onCreate(trimmed, newColor); query = "" }
                    filtered.size == 1 -> onToggle(filtered.single().id)
                }
            }),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        if (trimmed.isNotEmpty() && !exact) {
            Surface(shape = GroupedShapes.forIndex(0, 1), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = { onCreate(trimmed, newColor); query = "" },
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.tag_create, trimmed)) },
                            leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            trailingContent = { TagChip(Tag("preview", trimmed, newColor, "")) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    Text(stringResource(R.string.tag_color), style = MaterialTheme.typography.labelLarge)
                    ColorSwatches(selected = newColor, onPick = { newColor = it })
                }
            }
            if (filtered.isNotEmpty()) androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        }

        filtered.forEachIndexed { index, tag ->
            val checked = tag.id in selected
            Surface(
                onClick = { onToggle(tag.id) },
                shape = GroupedShapes.forIndex(index, filtered.size),
                color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { TagChip(tag) },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

