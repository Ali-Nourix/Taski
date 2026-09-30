package io.github.alinourix.taski.core.ui.picker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.theme.roles
import androidx.compose.material.icons.rounded.Folder

@Composable
fun ProjectPickerSheet(projects: List<Project>, current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    PickerSheet(stringResource(R.string.pick_project), onDismiss) {
        val count = projects.size + 1
        OptionItem(stringResource(R.string.inbox), current == null, 0, count, { onPick(null) }, icon = Icons.Rounded.Inbox)
        projects.forEachIndexed { index, project ->
            OptionItem(
                label = project.name,
                selected = project.id == current,
                index = index + 1,
                count = count,
                onClick = { onPick(project.id) },
                icon = Icons.Rounded.Folder,
                iconTint = project.color.roles().accent,
            )
        }
    }
}
