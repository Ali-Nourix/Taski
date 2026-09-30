package io.github.alinourix.taski.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.repository.TimerRepository
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUiState(
    val loading: Boolean = true,
    val timer: FocusTimerState? = null,
    val task: TaskItem? = null,
    val candidates: List<TaskItem> = emptyList(),
    val defaultMinutes: Int = FocusTimerState.DEFAULT_MINUTES,
    val focusedSeconds: Long = 0,
)

@HiltViewModel
class FocusTimerViewModel @Inject constructor(
    tasks: TaskRepository,
    timers: TimerRepository,
    preferences: PreferencesRepository,
    private val controller: FocusTimerController,
) : ViewModel() {
    val state: StateFlow<TimerUiState> = combine(
        controller.active,
        tasks.observeItems(),
        preferences.preferences,
        timers.observeTotals(),
    ) { timer, items, prefs, totals ->
        TimerUiState(
            loading = false,
            timer = timer,
            task = timer?.let { t -> items.firstOrNull { it.id == t.taskId } },
            candidates = items.filter { it.task.status != TaskStatus.Done }
                .sortedWith(compareBy({ it.task.status != TaskStatus.InProgress }, { it.task.dueDate == null }, { it.task.dueDate }, { it.task.sortKey })),
            defaultMinutes = prefs.defaultTimerMinutes,
            focusedSeconds = timer?.let { totals[it.taskId] } ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun start(item: TaskItem, minutes: Int) = viewModelScope.launch { controller.start(item.id, item.task.timerMinutes ?: minutes) }
    fun startWith(minutes: Int) = viewModelScope.launch { state.value.timer?.let { controller.start(it.taskId, minutes) } }
    fun pause() = viewModelScope.launch { controller.pause() }
    fun resume() = viewModelScope.launch { controller.resume() }
    fun add(minutes: Int) = viewModelScope.launch { controller.addMinutes(minutes) }
    fun reset() = viewModelScope.launch { controller.reset() }
    fun stop() = viewModelScope.launch { controller.stop() }
}
