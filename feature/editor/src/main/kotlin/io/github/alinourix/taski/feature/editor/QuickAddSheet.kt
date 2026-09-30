package io.github.alinourix.taski.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.dueText
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.repeatLabel
import io.github.alinourix.taski.core.ui.picker.DatePickerSheet
import io.github.alinourix.taski.core.ui.picker.PrioritySheet
import io.github.alinourix.taski.core.ui.picker.ProjectPickerSheet
import io.github.alinourix.taski.core.ui.picker.RepeatSheet
import io.github.alinourix.taski.core.ui.picker.TagPickerSheet
import java.time.LocalDate
import io.github.alinourix.taski.core.ui.R as UiR

private enum class QuickSheet { Due, Priority, Repeat, Tags, Project }

/**
 * Add a task in one line from anywhere: type, pick what matters with the
 * chips, send. The keyboard's action adds it and keeps the sheet open for the next one.
 */
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit = {},
    projectId: String? = null,
    dueDate: LocalDate? = null,
    initialTitle: String = "",
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(QuickDraft(title = initialTitle, projectId = projectId, dueDate = dueDate)) }
    var sheet by rememberSaveable { mutableStateOf<QuickSheet?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (draft.title.isBlank()) return
        viewModel.add(draft, onAdded)
        draft = draft.copy(title = "")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                FilledIconButton(
                    onClick = { submit() },
                    enabled = draft.title.isNotBlank(),
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.quick_add_submit))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val preview = Task(id = "", title = "", dueDate = draft.dueDate, dueTime = draft.dueTime, sortKey = "", createdAt = 0, updatedAt = 0)
                Chip(dueText(preview)?.label ?: stringResource(UiR.string.pick_date), TaskIcons.Due, draft.dueDate != null) { sheet = QuickSheet.Due }
                Chip(
                    if (draft.priority != null) priorityLabel(draft.priority) else stringResource(UiR.string.pick_priority),
                    draft.priority?.let(TaskIcons::priority) ?: TaskIcons.priority(io.github.alinourix.taski.core.domain.model.Priority.Medium),
                    draft.priority != null,
                ) { sheet = QuickSheet.Priority }
                Chip(
                    draft.tagIds.takeIf { it.isNotEmpty() }?.let { ids -> tags.filter { it.id in ids }.joinToString { it.name } } ?: stringResource(UiR.string.pick_tags),
                    TaskIcons.Tag,
                    draft.tagIds.isNotEmpty(),
                ) { sheet = QuickSheet.Tags }
                val project = projects.firstOrNull { it.id == draft.projectId }
                Chip(project?.name ?: stringResource(UiR.string.inbox), if (project == null) Icons.Rounded.Inbox else Icons.Rounded.Folder, project != null) {
                    sheet = QuickSheet.Project
                }
                Chip(if (draft.repeat != null) repeatLabel(draft.repeat) else stringResource(UiR.string.pick_repeat), TaskIcons.Repeat, draft.repeat != null) {
                    sheet = QuickSheet.Repeat
                }
            }
            Text(stringResource(R.string.quick_add_syntax), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    when (sheet) {
        QuickSheet.Due -> DatePickerSheet(draft.dueDate, draft.dueTime, { sheet = null }) { d, t -> draft = draft.copy(dueDate = d, dueTime = t); sheet = null }
        QuickSheet.Priority -> PrioritySheet(draft.priority, { sheet = null }) { draft = draft.copy(priority = it); sheet = null }
        QuickSheet.Repeat -> RepeatSheet(draft.repeat, { sheet = null }) { draft = draft.copy(repeat = it); sheet = null }
        QuickSheet.Project -> ProjectPickerSheet(projects, draft.projectId, { sheet = null }) { draft = draft.copy(projectId = it); sheet = null }
        QuickSheet.Tags -> TagPickerSheet(
            tags = tags,
            selected = draft.tagIds,
            onToggle = { id -> draft = draft.copy(tagIds = if (id in draft.tagIds) draft.tagIds - id else draft.tagIds + id) },
            onCreate = { name, color -> viewModel.createTag(name, color) { id -> draft = draft.copy(tagIds = draft.tagIds + id) } },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
    )
}
