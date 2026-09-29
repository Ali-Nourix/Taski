package io.github.alinourix.taski.core.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.recurrence.Recurrence
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.CalendarText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The one snackbar host, above the navigation bar and the FAB. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

sealed interface TaskEvent {
    data class Deleted(val id: String) : TaskEvent
    /** A repeating task was completed and moved on to [next]. */
    data class RolledForward(val next: LocalDate) : TaskEvent
}

/**
 * What every task list does to a task — tick it, change its status, delete
 * it — and the feedback it gives, shared so each list behaves the same.
 */
class TaskActions(
    private val tasks: TaskRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val mutableEvents = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TaskEvent> = mutableEvents

    fun toggle(item: TaskItem) = setStatus(item, if (item.task.status == TaskStatus.Done) TaskStatus.NotStarted else TaskStatus.Done)

    fun setStatus(item: TaskItem, status: TaskStatus) {
        scope.launch {
            val repeat = item.task.repeat
            tasks.setStatus(item.id, status)
            if (status == TaskStatus.Done && repeat != null) {
                mutableEvents.emit(TaskEvent.RolledForward(Recurrence.nextDueDate(repeat, item.task.dueDate, clock.today())))
            }
        }
    }

    fun delete(item: TaskItem) {
        scope.launch {
            tasks.delete(item.id)
            mutableEvents.emit(TaskEvent.Deleted(item.id))
        }
    }

    fun restore(id: String) {
        scope.launch { tasks.restore(id) }
    }
}

/** Shows the snackbar for each [TaskEvent], with undo for a delete. */
@Composable
fun TaskEventsEffect(actions: TaskActions) {
    val snackbar = LocalSnackbarHostState.current
    val config = LocalUiConfig.current
    val deleted = stringResource(R.string.task_deleted)
    val undo = stringResource(R.string.action_undo)
    val rolled = stringResource(R.string.task_rolled)
    LaunchedEffect(actions) {
        actions.events.collect { event ->
            when (event) {
                is TaskEvent.Deleted -> launch {
                    val result = snackbar.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) actions.restore(event.id)
                }
                is TaskEvent.RolledForward -> launch {
                    snackbar.showSnackbar(rolled.replace("%s", CalendarText.date(event.next, config.calendar, config.persian, config.today)))
                }
            }
        }
    }
}
