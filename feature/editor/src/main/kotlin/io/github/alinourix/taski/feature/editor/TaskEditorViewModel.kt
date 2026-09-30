package io.github.alinourix.taski.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.markdown.TaskMarkdown
import io.github.alinourix.taski.core.domain.model.ActivityEntry
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Completion
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.TimerSession
import io.github.alinourix.taski.core.domain.repository.ActivityRepository
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.repository.TimerRepository
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorState(
    val loading: Boolean = true,
    val item: TaskItem? = null,
    val parent: TaskItem? = null,
    val subtasks: List<TaskItem> = emptyList(),
    val blockers: List<Task> = emptyList(),
    val completions: List<Completion> = emptyList(),
    val sessions: List<TimerSession> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val projects: List<Project> = emptyList(),
    val candidates: List<TaskItem> = emptyList(),
    val defaultTimerMinutes: Int = FocusTimerState.DEFAULT_MINUTES,
    val activeTimer: FocusTimerState? = null,
)

sealed interface EditorEvent {
    data object DependencyRefused : EditorEvent
    data object Deleted : EditorEvent
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val tagRepository: TagRepository,
    projects: ProjectRepository,
    timers: TimerRepository,
    activity: ActivityRepository,
    preferences: PreferencesRepository,
    private val timer: FocusTimerController,
) : ViewModel() {
    val id: String = savedState.toRoute<TaskEditorRoute>().id

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val titleDraft = MutableStateFlow<String?>(null)
    private val notesDraft = MutableStateFlow<String?>(null)

    private val history = combine(
        tasks.observeCompletions(id),
        timers.observeSessions(id),
        activity.observeForRow(id),
    ) { completions, sessions, log -> Triple(completions, sessions, log) }

    private val catalog = combine(
        tagRepository.observeTags(),
        projects.observeProjects(),
        preferences.preferences.map { it.defaultTimerMinutes },
        timer.active,
    ) { tagList, projectList, minutes, active -> Catalog(tagList, projectList, minutes, active) }

    private data class Catalog(val tags: List<Tag>, val projects: List<Project>, val minutes: Int, val active: FocusTimerState?)

    val state: StateFlow<EditorState> = combine(
        tasks.observeItems(),
        tasks.observeBlockers(id),
        history,
        catalog,
    ) { items, blockers, (completions, sessions, log), catalog ->
        val item = items.firstOrNull { it.id == id }
        EditorState(
            loading = false,
            item = item,
            parent = item?.task?.parentId?.let { pid -> items.firstOrNull { it.id == pid } },
            subtasks = items.filter { it.task.parentId == id }.sortedWith(compareBy({ it.task.sortKey }, { it.id })),
            blockers = blockers,
            completions = completions,
            sessions = sessions,
            activity = log,
            tags = catalog.tags,
            projects = catalog.projects,
            candidates = items.filter { it.id != id && it.task.status != TaskStatus.Done },
            defaultTimerMinutes = catalog.minutes,
            activeTimer = catalog.active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorState())

    init {
        viewModelScope.launch { titleDraft.filterNotNull().debounce(400).collect { tasks.edit(id, listOf(TaskEdit.Title(it))) } }
        viewModelScope.launch { notesDraft.filterNotNull().debounce(400).collect { tasks.edit(id, listOf(TaskEdit.Notes(it))) } }
    }

    fun setTitle(value: String) { titleDraft.value = value }
    fun setNotes(value: String) { notesDraft.value = value }

    fun edit(vararg edits: TaskEdit) = launch { tasks.edit(id, edits.toList()) }
    fun setStatus(status: TaskStatus) = launch { tasks.setStatus(id, status) }
    fun toggle(taskId: String) = launch { tasks.toggleDone(taskId) }

    fun toggleTag(tagId: String) = launch {
        val current = state.value.item?.tags?.map { it.id }?.toSet() ?: return@launch
        tasks.setTags(id, if (tagId in current) current - tagId else current + tagId)
    }

    fun createTag(name: String, color: ColorToken) = launch {
        val tagId = tagRepository.create(name, color)
        val current = state.value.item?.tags?.map { it.id }?.toSet().orEmpty()
        tasks.setTags(id, current + tagId)
    }

    fun addSubtask(title: String) = launch {
        if (title.isNotBlank()) tasks.create(NewTask(title = title, parentId = id))
    }

    fun addDependency(onId: String) = launch {
        if (!tasks.addDependency(id, onId)) events.send(EditorEvent.DependencyRefused)
    }

    fun removeDependency(onId: String) = launch { tasks.removeDependency(id, onId) }

    fun startFocus() = launch {
        val minutes = state.value.item?.task?.timerMinutes ?: state.value.defaultTimerMinutes
        timer.start(id, minutes)
    }

    fun delete() = launch {
        tasks.delete(id)
        events.send(EditorEvent.Deleted)
    }

    fun markdownLine(): String? = state.value.item?.let { item ->
        val t = item.task
        TaskMarkdown.format(t.title, t.status, t.priority, t.dueDate, t.repeat, item.tags.map { it.name })
    }

    /** Saves any pending title or notes right away, when the screen closes. */
    fun flush() = launch {
        val edits = listOfNotNull(titleDraft.value?.let(TaskEdit::Title), notesDraft.value?.let(TaskEdit::Notes))
        if (edits.isNotEmpty() && tasks.observeItem(id).first() != null) tasks.edit(id, edits)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
