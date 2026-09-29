package io.github.alinourix.taski.feature.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.GroupedShapes
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
import io.github.alinourix.taski.core.designsystem.component.ShapeIcon
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.LiveTimerChip
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.SwipeableTaskRow
import io.github.alinourix.taski.core.ui.component.TaskActions
import io.github.alinourix.taski.core.ui.component.TaskEventsEffect
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.picker.StatusSheet
import io.github.alinourix.taski.core.domain.CalendarSystem

@Composable
fun TodayScreen(
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions)
    TodayContent(state, viewModel.actions, onOpenTask, onOpenTimer, contentPadding)
}

@Composable
fun TodayContent(
    state: TodayState,
    actions: TaskActions?,
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var statusFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val all = state.overdue + state.dueToday + state.inProgress + state.upcoming + state.doneToday

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                subtitle = { Text(todaySubtitle()) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val titles = mapOf(
            "overdue" to stringResource(R.string.today_overdue),
            "today" to stringResource(R.string.today_due),
            "progress" to stringResource(R.string.today_in_progress),
            "upcoming" to stringResource(R.string.today_upcoming),
        )
        val emptyTitle = stringResource(R.string.today_all_clear)
        val emptyBody = stringResource(R.string.today_all_clear_body)
        val doneTitle = stringResource(R.string.today_done)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item(key = "hero") { Hero(state, onOpenTimer) }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.WbSunny, emptyTitle, body = emptyBody)
                }
            }
            section("overdue", titles.getValue("overdue"), state.overdue, state, actions, onOpenTask) { statusFor = it }
            section("today", titles.getValue("today"), state.dueToday, state, actions, onOpenTask) { statusFor = it }
            section("progress", titles.getValue("progress"), state.inProgress, state, actions, onOpenTask) { statusFor = it }
            section("upcoming", titles.getValue("upcoming"), state.upcoming, state, actions, onOpenTask, showProject = true) { statusFor = it }
            if (state.doneToday.isNotEmpty()) {
                item(key = "done-header") {
                    SectionHeader(
                        doneTitle,
                        count = state.doneToday.size,
                        trailing = {
                            IconButton(onClick = { showDone = !showDone }) {
                                Icon(if (showDone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                            }
                        },
                    )
                }
                if (showDone) rows("done", state.doneToday, state, actions, onOpenTask, false) { statusFor = it }
            }
        }
    }

    statusFor?.let { id ->
        val item = all.firstOrNull { it.id == id }
        if (item == null) {
            statusFor = null
        } else {
            StatusSheet(item.task.status, onDismiss = { statusFor = null }) { actions?.setStatus(item, it); statusFor = null }
        }
    }
}

@Composable
private fun todaySubtitle(): String {
    val config = LocalUiConfig.current
    val day = CalendarText.weekdayLong(config.today.dayOfWeek, config.persian)
    val main = CalendarText.date(config.today, config.calendar, config.persian)
    // The other calendar alongside, the way a Persian wall calendar prints both.
    val other = if (config.calendar == CalendarSystem.Jalali) CalendarSystem.Gregorian else CalendarSystem.Jalali
    val second = if (config.persian || config.calendar == CalendarSystem.Jalali) " · " + CalendarText.date(config.today, other, config.persian) else ""
    return "$day، $main$second".replace("،", if (config.persian) "،" else ",")
}

@Composable
private fun Hero(state: TodayState, onOpenTimer: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CircularWavyProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val total = state.openCount + state.doneToday.size
                    Text(
                        stringResource(R.string.today_progress, state.doneToday.size, total).localizeDigits(persian),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat(stringResource(R.string.today_stat_open), state.openCount)
                        Stat(stringResource(R.string.today_stat_overdue), state.overdue.size)
                    }
                }
            }
            val timer = state.timer
            val task = state.timerTask
            if (timer != null && task != null) {
                Surface(onClick = onOpenTimer, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShapeIcon(TaskIcons.Timer, polygon = MaterialShapes.Sunny, size = 36.dp)
                        Text(task.task.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        LiveTimerChip(timer, onClick = onOpenTimer)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    val persian = LocalUiConfig.current.persian
    Column {
        Text(value.toString().localizeDigits(persian), style = MaterialTheme.typography.titleMediumEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun LazyListScope.section(
    key: String,
    title: String,
    items: List<TaskItem>,
    state: TodayState,
    actions: TaskActions?,
    onOpenTask: (String) -> Unit,
    showProject: Boolean = false,
    onStatusMenu: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "$key-header") { SectionHeader(title, count = items.size) }
    rows(key, items, state, actions, onOpenTask, showProject, onStatusMenu)
}

private fun LazyListScope.rows(
    key: String,
    items: List<TaskItem>,
    state: TodayState,
    actions: TaskActions?,
    onOpenTask: (String) -> Unit,
    showProject: Boolean,
    onStatusMenu: (String) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> "$key-${item.id}" }) { index, item ->
        SwipeableTaskRow(
            item = item,
            onToggle = { actions?.toggle(item) },
            onDelete = { actions?.delete(item) },
            onClick = { onOpenTask(item.id) },
            onStatusMenu = { onStatusMenu(item.id) },
            extras = RowExtras(showProject = showProject || item.task.status == TaskStatus.InProgress, timer = state.timer),
            shape = GroupedShapes.forIndex(index, items.size),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp).animateItem(),
        )
    }
}
