package io.github.alinourix.taski.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.component.TaskActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class TodayState(
    val loading: Boolean = true,
    val overdue: List<TaskItem> = emptyList(),
    val dueToday: List<TaskItem> = emptyList(),
    val inProgress: List<TaskItem> = emptyList(),
    val upcoming: List<TaskItem> = emptyList(),
    val doneToday: List<TaskItem> = emptyList(),
    val timer: FocusTimerState? = null,
    val timerTask: TaskItem? = null,
) {
    val openCount: Int get() = overdue.size + dueToday.size + inProgress.size
    val isEmpty: Boolean get() = openCount == 0 && upcoming.isEmpty() && doneToday.isEmpty()
    val progress: Float get() = (openCount + doneToday.size).let { if (it == 0) 0f else doneToday.size.toFloat() / it }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    tasks: TaskRepository,
    private val clock: Clock,
    timer: FocusTimerController,
) : ViewModel() {
    val actions = TaskActions(tasks, clock, viewModelScope)

    val state: StateFlow<TodayState> = combine(tasks.observeItems(), timer.active) { items, active ->
        val today = clock.today()
        val order = compareBy<TaskItem>({ -(it.task.priority?.rank ?: 0) }, { it.task.dueTime }, { it.task.sortKey })
        val open = items.filter { it.task.status != TaskStatus.Done }
        val overdue = open.filter { it.task.dueDate?.isBefore(today) == true }.sortedWith(compareBy<TaskItem> { it.task.dueDate }.then(order))
        val dueToday = open.filter { it.task.dueDate == today }.sortedWith(order)
        val shown = (overdue + dueToday).map { it.id }.toSet()
        TodayState(
            loading = false,
            overdue = overdue,
            dueToday = dueToday,
            inProgress = open.filter { it.task.status == TaskStatus.InProgress && it.id !in shown }.sortedWith(order),
            upcoming = open.filter { item ->
                val due = item.task.dueDate
                due != null && due.isAfter(today) && !due.isAfter(today.plusDays(7)) && item.task.status != TaskStatus.InProgress
            }.sortedWith(compareBy<TaskItem> { it.task.dueDate }.then(order)),
            doneToday = items.filter { item ->
                item.task.status == TaskStatus.Done &&
                    item.task.completedAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone()).toLocalDate() } == today
            },
            timer = active,
            timerTask = active?.let { a -> items.firstOrNull { it.id == a.taskId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())
}
