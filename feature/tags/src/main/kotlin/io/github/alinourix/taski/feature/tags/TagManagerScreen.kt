package io.github.alinourix.taski.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.TagChip
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.picker.ColorSwatches
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject
import io.github.alinourix.taski.core.ui.R as UiR

data class TagsState(val tags: List<Tag> = emptyList(), val usage: Map<String, Int> = emptyMap())

@HiltViewModel
class TagManagerViewModel @Inject constructor(private val tags: TagRepository) : ViewModel() {
    val state: StateFlow<TagsState> = combine(tags.observeTags(), tags.observeUsage(), ::TagsState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsState())

    fun create(name: String, color: ColorToken) = viewModelScope.launch { tags.create(name, color) }
    fun update(id: String, name: String, color: ColorToken) = viewModelScope.launch { tags.update(id, name, color) }
    fun delete(id: String) = viewModelScope.launch { tags.delete(id) }
    fun move(id: String, afterId: String?, beforeId: String?) = viewModelScope.launch { tags.move(id, afterId, beforeId) }
}

private sealed interface Editing {
    data object New : Editing
    data class Existing(val tag: Tag) : Editing
}

@Composable
fun TagManagerScreen(onBack: () -> Unit, viewModel: TagManagerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val persian = LocalUiConfig.current.persian
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                subtitle = { Text(stringResource(R.string.tags_hint)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Editing.New },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.tags_new)) },
            )
        },
    ) { padding ->
        var order by remember(state.tags) { mutableStateOf(state.tags) }
        val listState = rememberLazyListState()
        val reorder = rememberReorderableLazyListState(listState) { from, to ->
            val a = order.indexOfFirst { it.id == from.key }
            val b = order.indexOfFirst { it.id == to.key }
            if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap),
        ) {
            if (order.isEmpty()) {
                item(key = "empty") { EmptyState(Icons.Rounded.Sell, stringResource(R.string.tags_empty), body = stringResource(R.string.tags_empty_body)) }
            }
            itemsIndexed(order, key = { _, tag -> tag.id }) { index, tag ->
                ReorderableItem(reorder, key = tag.id) { dragging ->
                    val shape = GroupedShapes.forIndex(index, order.size)
                    Surface(
                        onClick = { editing = Editing.Existing(tag) },
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().then(if (dragging) Modifier.shadow(8.dp, shape) else Modifier),
                    ) {
                        val count = state.usage[tag.id] ?: 0
                        ListItem(
                            headlineContent = { TagChip(tag) },
                            supportingContent = { Text(pluralStringResource(R.plurals.tags_usage, count, count).localizeDigits(persian)) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { deleting = tag }) { Icon(Icons.Rounded.Delete, stringResource(R.string.tags_delete)) }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(onDragStopped = {
                                            val i = order.indexOfFirst { it.id == tag.id }
                                            viewModel.move(tag.id, order.getOrNull(i - 1)?.id, order.getOrNull(i + 1)?.id)
                                        }),
                                    ) { Icon(Icons.Rounded.DragIndicator, stringResource(R.string.tags_reorder)) }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }

    when (val current = editing) {
        Editing.New -> TagDialog(
            stringResource(R.string.tags_new), "", ColorToken.next(state.tags.map { it.color }),
            onDismiss = { editing = null },
        ) { name, color -> viewModel.create(name, color); editing = null }
        is Editing.Existing -> TagDialog(
            stringResource(R.string.tags_edit), current.tag.name, current.tag.color,
            onDismiss = { editing = null },
        ) { name, color -> viewModel.update(current.tag.id, name, color); editing = null }
        null -> Unit
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tags_delete)) },
            text = { Text(stringResource(R.string.tags_delete_body, tag.name)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(tag.id); deleting = null }) { Text(stringResource(UiR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(UiR.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TagDialog(title: String, initialName: String, initialColor: ColorToken, onDismiss: () -> Unit, onSave: (String, ColorToken) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.tags_name)) }, singleLine = true)
                if (name.isNotBlank()) TagChip(Tag("preview", name.trim(), color, ""), Modifier.padding(start = 4.dp))
                ColorSwatches(selected = color, onPick = { color = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}
