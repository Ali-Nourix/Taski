package io.github.alinourix.taski.core.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.alinourix.taski.core.domain.markdown.TaskMarkdown
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.recurrence.Recurrence
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
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
    data object FocusStarted : TaskEvent
}

/**
 * Everything a task list can do to a task in place — tick it, set its status
 * or any property, tag it, start a focus timer, delete it — and the feedback
 * each gives. Shared, so every list edits the way the plugin's chip menus do.
 */
class TaskActions(
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val timer: FocusTimerController? = null,
    private val defaultTimerMinutes: () -> Int = { FocusTimerState.DEFAULT_MINUTES },
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

    fun edit(id: String, vararg edits: TaskEdit) {
        scope.launch { tasks.edit(id, edits.toList()) }
    }

    fun toggleTag(item: TaskItem, tagId: String) {
        val current = item.tags.map { it.id }.toSet()
        scope.launch { tasks.setTags(item.id, if (tagId in current) current - tagId else current + tagId) }
    }

    fun createTag(item: TaskItem, name: String, color: ColorToken) {
        scope.launch {
            val id = tags.create(name, color)
            tasks.setTags(item.id, item.tags.map { it.id }.toSet() + id)
        }
    }

    /** A new task that takes on the values of the group it was added to, as on the plugin's board. */
    fun create(task: NewTask) {
        scope.launch { tasks.create(task) }
    }

    fun startFocus(item: TaskItem) {
        val controller = timer ?: return
        scope.launch {
            controller.start(item.id, item.task.timerMinutes ?: defaultTimerMinutes())
            mutableEvents.emit(TaskEvent.FocusStarted)
        }
    }

    val canFocus: Boolean get() = timer != null

    fun delete(item: TaskItem) {
        scope.launch {
            tasks.delete(item.id)
            mutableEvents.emit(TaskEvent.Deleted(item.id))
        }
    }

    fun restore(id: String) {
        scope.launch { tasks.restore(id) }
    }

    fun markdownLine(item: TaskItem): String = item.task.let { t ->
        TaskMarkdown.format(t.title, t.status, t.priority, t.dueDate, t.repeat, item.tags.map { it.name })
    }
}

/** Shows the snackbar for each [TaskEvent], with undo for a delete. */
@Composable
fun TaskEventsEffect(actions: TaskActions, onFocusStarted: () -> Unit = {}) {
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
                TaskEvent.FocusStarted -> onFocusStarted()
            }
        }
    }
}
