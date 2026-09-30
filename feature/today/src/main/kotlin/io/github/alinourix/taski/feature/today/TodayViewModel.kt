package io.github.alinourix.taski.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.component.TaskActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class TodayState(
    val loading: Boolean = true,
    val overdue: List<TaskItem> = emptyList(),
    val dueToday: List<TaskItem> = emptyList(),
    /** Tasks whose start-to-due window includes today and that are due later: on the plan, not yet due. */
    val underway: List<TaskItem> = emptyList(),
    val inProgress: List<TaskItem> = emptyList(),
    val upcoming: List<TaskItem> = emptyList(),
    val doneToday: List<TaskItem> = emptyList(),
    val timer: FocusTimerState? = null,
    val timerTask: TaskItem? = null,
    val tags: List<Tag> = emptyList(),
    val projects: List<Project> = emptyList(),
) {
    fun find(id: String): TaskItem? = (overdue + dueToday + underway + inProgress + upcoming + doneToday).firstOrNull { it.id == id }

    val openCount: Int get() = overdue.size + dueToday.size + underway.size + inProgress.size
    val isEmpty: Boolean get() = openCount == 0 && upcoming.isEmpty() && doneToday.isEmpty()
    val progress: Float get() = (openCount + doneToday.size).let { if (it == 0) 0f else doneToday.size.toFloat() / it }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    tasks: TaskRepository,
    tags: TagRepository,
    projects: ProjectRepository,
    preferences: PreferencesRepository,
    private val clock: Clock,
    timer: FocusTimerController,
) : ViewModel() {
    private val defaultMinutes = preferences.preferences.map { it.defaultTimerMinutes }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusTimerState.DEFAULT_MINUTES)

    val actions = TaskActions(tasks, tags, clock, viewModelScope, timer) { defaultMinutes.value }

    val state: StateFlow<TodayState> = combine(
        tasks.observeItems(),
        timer.active,
        tags.observeTags(),
        projects.observeProjects(),
    ) { items, active, tagList, projectList ->
        val today = clock.today()
        val order = compareBy<TaskItem>({ -(it.task.priority?.rank ?: 0) }, { it.task.dueTime }, { it.task.sortKey })
        val open = items.filter { it.task.status != TaskStatus.Done }
        val overdue = open.filter { it.task.dueDate?.isBefore(today) == true }.sortedWith(compareBy<TaskItem> { it.task.dueDate }.then(order))
        val dueToday = open.filter { it.task.dueDate == today }.sortedWith(order)
        val dueShown = (overdue + dueToday).map { it.id }.toSet()
        val underway = open.filter { item ->
            val start = item.task.startDate
            val due = item.task.dueDate
            item.id !in dueShown && start != null && !start.isAfter(today) && (due == null || due.isAfter(today))
        }.sortedWith(compareBy<TaskItem> { it.task.startDate }.then(order))
        val shown = dueShown + underway.map { it.id }
        TodayState(
            loading = false,
            overdue = overdue,
            dueToday = dueToday,
            underway = underway,
            inProgress = open.filter { it.task.status == TaskStatus.InProgress && it.id !in shown }.sortedWith(order),
            upcoming = open.filter { item ->
                val due = item.task.dueDate
                due != null && due.isAfter(today) && !due.isAfter(today.plusDays(7)) &&
                    item.task.status != TaskStatus.InProgress && item.id !in shown
            }.sortedWith(compareBy<TaskItem> { it.task.dueDate }.then(order)),
            doneToday = items.filter { item ->
                item.task.status == TaskStatus.Done &&
                    item.task.completedAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone()).toLocalDate() } == today
            },
            timer = active,
            timerTask = active?.let { a -> items.firstOrNull { it.id == a.taskId } },
            tags = tagList,
            projects = projectList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())
}
