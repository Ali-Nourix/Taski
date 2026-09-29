package io.github.alinourix.taski.feature.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.NewTask
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectSummary(val project: Project?, val open: Int, val done: Int) {
    val fraction: Float get() = if (open + done == 0) 0f else done.toFloat() / (open + done)
}

data class ProjectsState(
    val loading: Boolean = true,
    val inbox: ProjectSummary = ProjectSummary(null, 0, 0),
    val projects: List<ProjectSummary> = emptyList(),
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    tasks: TaskRepository,
    private val projects: ProjectRepository,
) : ViewModel() {
    val state: StateFlow<ProjectsState> = combine(tasks.observeItems(), projects.observeProjects()) { items, list ->
        fun summary(project: Project?): ProjectSummary {
            val mine = items.filter { it.task.projectId == project?.id && it.task.parentId == null }
            return ProjectSummary(project, mine.count { it.task.status != TaskStatus.Done }, mine.count { it.task.status == TaskStatus.Done })
        }
        ProjectsState(loading = false, inbox = summary(null), projects = list.map(::summary))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsState())

    fun create(name: String, color: ColorToken) = viewModelScope.launch { projects.create(name, color) }
    fun update(id: String, name: String, color: ColorToken) = viewModelScope.launch { projects.update(id, name, color) }
    fun delete(id: String) = viewModelScope.launch { projects.delete(id) }
    fun move(id: String, afterId: String?, beforeId: String?) = viewModelScope.launch { projects.move(id, afterId, beforeId) }
    fun nextColor(): ColorToken = ColorToken.next(state.value.projects.mapNotNull { it.project?.color })
}

data class ProjectDetailState(
    val loading: Boolean = true,
    val project: Project? = null,
    val missing: Boolean = false,
    /** Root tasks in manual order, each followed by its subtasks. */
    val open: List<TaskItem> = emptyList(),
    val subtasks: Map<String, List<TaskItem>> = emptyMap(),
    val done: List<TaskItem> = emptyList(),
    val timer: FocusTimerState? = null,
    val tags: List<Tag> = emptyList(),
    val projects: List<Project> = emptyList(),
) {
    fun find(id: String): TaskItem? = (open + done).firstOrNull { it.id == id } ?: subtasks.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
}

@HiltViewModel
class ProjectViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    tags: TagRepository,
    preferences: PreferencesRepository,
    clock: Clock,
    timer: FocusTimerController,
) : ViewModel() {
    val projectId: String? = savedState.toRoute<ProjectRoute>().id
    private val defaultMinutes = preferences.preferences.map { it.defaultTimerMinutes }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusTimerState.DEFAULT_MINUTES)
    val actions = TaskActions(tasks, tags, clock, viewModelScope, timer) { defaultMinutes.value }

    private val catalog = combine(tags.observeTags(), projects.observeProjects(), timer.active) { t, p, a -> Triple(t, p, a) }

    val state: StateFlow<ProjectDetailState> = combine(
        tasks.observeItems(),
        projectId?.let(projects::observeProject) ?: flowOf(null),
        catalog,
    ) { items, project, (allTags, allProjects, active) ->
        val order = compareBy<TaskItem>({ it.task.sortKey }, { it.id })
        val mine = items.filter { it.task.projectId == projectId }
        val roots = mine.filter { it.task.parentId == null || mine.none { p -> p.id == it.task.parentId } }
        ProjectDetailState(
            loading = false,
            project = project,
            missing = projectId != null && project == null,
            open = roots.filter { it.task.status != TaskStatus.Done }.sortedWith(order),
            subtasks = mine.filter { it.task.parentId != null }.groupBy { it.task.parentId!! }.mapValues { it.value.sortedWith(order) },
            done = roots.filter { it.task.status == TaskStatus.Done }.sortedWith(order),
            timer = active,
            tags = allTags,
            projects = allProjects,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailState())

    fun add(title: String) = viewModelScope.launch { if (title.isNotBlank()) tasks.create(NewTask(title = title, projectId = projectId)) }
    fun move(id: String, afterId: String?, beforeId: String?) = viewModelScope.launch { tasks.move(id, afterId, beforeId) }
    fun update(name: String, color: ColorToken) = viewModelScope.launch { projectId?.let { projects.update(it, name, color) } }
    fun delete() = viewModelScope.launch { projectId?.let { projects.delete(it) } }
}
