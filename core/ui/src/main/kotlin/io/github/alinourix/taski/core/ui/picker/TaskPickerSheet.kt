package io.github.alinourix.taski.core.ui.picker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.ui.format.TaskIcons

/** Pick one task from a searchable list, for a dependency or a focus timer. */
@Composable
fun TaskPickerSheet(title: String, searchHint: String, tasks: List<TaskItem>, onDismiss: () -> Unit, onPick: (TaskItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(tasks, query) {
        val q = query.trim()
        tasks.filter { q.isEmpty() || it.task.title.contains(q, ignoreCase = true) }.take(60)
    }
    PickerSheet(title, onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(searchHint) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        shown.forEachIndexed { index, item ->
            OptionItem(
                label = item.task.title,
                selected = false,
                index = index,
                count = shown.size,
                onClick = { onPick(item) },
                icon = TaskIcons.status(item.task.status),
                supporting = item.project?.name,
            )
        }
    }
}
