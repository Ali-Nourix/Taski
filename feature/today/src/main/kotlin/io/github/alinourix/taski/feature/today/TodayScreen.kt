package io.github.alinourix.taski.feature.today

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.designsystem.component.SectionHeader
import io.github.alinourix.taski.core.designsystem.component.sectionRow
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.LiveTimerChip
import io.github.alinourix.taski.core.ui.component.RowExtras
import io.github.alinourix.taski.core.ui.component.SwipeableTaskRow
import io.github.alinourix.taski.core.ui.component.TaskActions
import io.github.alinourix.taski.core.ui.component.TaskEventsEffect
import io.github.alinourix.taski.core.ui.component.TaskProperty
import io.github.alinourix.taski.core.ui.component.TaskSheetHost
import io.github.alinourix.taski.core.ui.component.rememberTaskSheetState
import io.github.alinourix.taski.core.ui.component.rememberTimerLabel
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.R as UiR

@Composable
fun TodayScreen(
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TaskEventsEffect(viewModel.actions, onFocusStarted = onOpenTimer)
    TodayContent(state, viewModel.actions, onOpenTask, onOpenTimer, contentPadding)
}

@Composable
fun TodayContent(
    state: TodayState,
    actions: TaskActions,
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sheet = rememberTaskSheetState()
    var showDone by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                subtitle = { Text(todaySubtitle()) },
                // Shorter than the default: the day's first card should start in the top fifth.
                expandedHeight = 112.dp,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
            "underway" to stringResource(R.string.today_underway),
            "progress" to stringResource(R.string.today_in_progress),
            "upcoming" to stringResource(R.string.today_upcoming),
        )
        val emptyTitle = stringResource(R.string.today_all_clear)
        val emptyBody = stringResource(R.string.today_all_clear_body)
        val doneTitle = stringResource(R.string.today_done)
        val onEdit = { item: TaskItem -> { property: TaskProperty -> sheet.open(item.id, property) } }
        // The card at the top is the next task; the list below does not say it a second time.
        val hero = heroTask(state)?.id
        fun List<TaskItem>.rest() = filterNot { it.id == hero }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp),
        ) {
            item(key = "hero") { Hero(state, actions, onOpenTask, onOpenTimer) }
            if (state.isEmpty) {
                item(key = "empty") { EmptyState(Icons.Rounded.WbSunny, emptyTitle, body = emptyBody) }
            }
            section("overdue", titles.getValue("overdue"), state.overdue.rest(), state, actions, onOpenTask, onEdit)
            section("today", titles.getValue("today"), state.dueToday.rest(), state, actions, onOpenTask, onEdit)
            section("underway", titles.getValue("underway"), state.underway.rest(), state, actions, onOpenTask, onEdit, showProject = true)
            section("progress", titles.getValue("progress"), state.inProgress.rest(), state, actions, onOpenTask, onEdit)
            section("upcoming", titles.getValue("upcoming"), state.upcoming.rest(), state, actions, onOpenTask, onEdit, showProject = true)
            if (state.doneToday.isNotEmpty()) {
                item(key = "done-header") {
                    SectionHeader(doneTitle, count = state.doneToday.size, trailing = {
                        IconButton(onClick = { showDone = !showDone }) {
                            Icon(if (showDone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }
                    })
                }
                if (showDone) rows("done", state.doneToday, state, actions, onOpenTask, onEdit, false)
            }
        }
    }

    TaskSheetHost(sheet, state::find, state.tags, state.projects, actions, onOpenTask)
}

@Composable
private fun todaySubtitle(): String {
    val config = LocalUiConfig.current
    val day = CalendarText.weekdayLong(config.today.dayOfWeek, config.persian)
    val main = CalendarText.date(config.today, config.calendar, config.persian)
    // The other calendar alongside, the way a Persian wall calendar prints both.
    val other = if (config.calendar == CalendarSystem.Jalali) CalendarSystem.Gregorian else CalendarSystem.Jalali
    val second = if (config.persian || config.calendar == CalendarSystem.Jalali) " · " + CalendarText.date(config.today, other, config.persian) else ""
    return "$day${if (config.persian) "،" else ","} $main$second"
}

/** The task the card at the top is about: the one being timed, else the most pressing. */
private fun heroTask(state: TodayState): TaskItem? {
    if (state.isEmpty) return null
    val running = state.timerTask?.takeIf { state.timer != null }
    return running ?: state.inProgress.firstOrNull() ?: state.overdue.firstOrNull() ?: state.dueToday.firstOrNull() ?: state.underway.firstOrNull()
}

/**
 * The screen's one bold moment, and it does work: the task to do next with a
 * button to start focusing on it — or, while a timer runs, the countdown. Its
 * wavy line is the day's progress. Nothing else on the page is filled with colour.
 */
@Composable
private fun Hero(state: TodayState, actions: TaskActions, onOpenTask: (String) -> Unit, onOpenTimer: () -> Unit) {
    if (state.isEmpty) return
    val persian = LocalUiConfig.current.persian
    val total = state.openCount + state.doneToday.size
    val timer = state.timer
    val next = heroTask(state)
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { if (timer != null) onOpenTimer() else next?.let { onOpenTask(it.id) } },
        enabled = next != null,
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    when {
                        timer != null -> R.string.today_focusing
                        next != null -> R.string.today_up_next
                        else -> R.string.today_done_all
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
            if (timer != null && next != null) {
                Text(rememberTimerLabel(timer), style = MaterialTheme.typography.displayMedium, maxLines = 1)
            }
            if (next != null) {
                Text(next.task.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(stringResource(R.string.today_all_clear), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearWavyProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.onPrimaryContainer.copy(alpha = 0.16f),
                        amplitude = { if (it > 0f && it < 1f) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f },
                    )
                    if (total > 0) {
                        Text(
                            stringResource(R.string.today_progress, state.doneToday.size, total).localizeDigits(persian),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                    }
                }
                if (next != null) {
                    FilledTonalIconButton(onClick = { actions.toggle(next) }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Check, stringResource(UiR.string.action_done))
                    }
                }
                if (timer == null && next != null && actions.canFocus) {
                    FilledIconButton(onClick = { actions.startFocus(next) }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(UiR.string.task_focus))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    key: String,
    title: String,
    items: List<TaskItem>,
    state: TodayState,
    actions: TaskActions,
    onOpenTask: (String) -> Unit,
    onEdit: (TaskItem) -> (TaskProperty) -> Unit,
    showProject: Boolean = false,
) {
    if (items.isEmpty()) return
    item(key = "$key-header") { SectionHeader(title, count = items.size) }
    rows(key, items, state, actions, onOpenTask, onEdit, showProject)
}

private fun LazyListScope.rows(
    key: String,
    items: List<TaskItem>,
    state: TodayState,
    actions: TaskActions,
    onOpenTask: (String) -> Unit,
    onEdit: (TaskItem) -> (TaskProperty) -> Unit,
    showProject: Boolean,
) {
    itemsIndexed(items, key = { _, item -> "$key-${item.id}" }) { index, item ->
        SwipeableTaskRow(
            item = item,
            onToggle = { actions.toggle(item) },
            onDelete = { actions.delete(item) },
            onClick = { onOpenTask(item.id) },
            onEdit = onEdit(item),
            extras = RowExtras(showProject = showProject || item.task.status == TaskStatus.InProgress, timer = state.timer),
            showDivider = index < items.lastIndex,
            modifier = Modifier.animateItem().sectionRow(index, items.size),
        )
    }
}
