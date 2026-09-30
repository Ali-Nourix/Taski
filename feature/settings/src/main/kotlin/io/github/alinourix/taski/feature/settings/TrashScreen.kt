package io.github.alinourix.taski.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.format.CalendarText
import java.time.Instant
import io.github.alinourix.taski.core.ui.R as UiR

@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val config = LocalUiConfig.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    if (trash.isNotEmpty()) {
                        FilledTonalButton(onClick = { confirm = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Rounded.DeleteForever, null)
                            Text(stringResource(R.string.trash_empty_action), Modifier.padding(start = 6.dp))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedShapes.Gap),
        ) {
            if (trash.isEmpty()) item { EmptyState(Icons.Rounded.DeleteOutline, stringResource(R.string.trash_empty)) }
            itemsIndexed(trash, key = { _, task -> task.id }) { index, task ->
                val deleted = task.deletedAt?.let { Instant.ofEpochMilli(it).atZone(config.zone).toLocalDate() }
                Surface(shape = GroupedShapes.forIndex(index, trash.size), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().animateItem()) {
                    ListItem(
                        headlineContent = { Text(task.title) },
                        supportingContent = deleted?.let { { Text(stringResource(R.string.trash_deleted_on, CalendarText.date(it, config.calendar, config.persian, config.today))) } },
                        trailingContent = {
                            IconButton(onClick = { viewModel.restore(task.id) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.RestoreFromTrash, stringResource(R.string.trash_restore))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.trash_empty_action)) },
            text = { Text(stringResource(R.string.trash_empty_body)) },
            confirmButton = { TextButton(onClick = { viewModel.emptyTrash(); confirm = false }) { Text(stringResource(UiR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(UiR.string.action_cancel)) } },
        )
    }
}
