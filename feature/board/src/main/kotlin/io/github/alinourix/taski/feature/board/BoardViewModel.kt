package io.github.alinourix.taski.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.SavedView
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.model.ViewLayout
import io.github.alinourix.taski.core.domain.query.Summary
import io.github.alinourix.taski.core.domain.query.TaskGroup
import io.github.alinourix.taski.core.domain.query.TaskQuery
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.SavedViewRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.component.TaskActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoardState(
    val loading: Boolean = true,
    val view: ViewDefinition = ViewDefinition(),
    val groups: List<TaskGroup> = emptyList(),
    val summary: Summary = Summary(0, 0, 0),
    val totalTasks: Int = 0,
    val tags: List<Tag> = emptyList(),
    val projects: List<Project> = emptyList(),
    val savedViews: List<SavedView> = emptyList(),
    val collapsed: Set<String> = emptySet(),
    val timer: FocusTimerState? = null,
)

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val tasks: TaskRepository,
    tags: TagRepository,
    projects: ProjectRepository,
    private val views: SavedViewRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    timer: FocusTimerController,
) : ViewModel() {
    val actions = TaskActions(tasks, clock, viewModelScope)
    val search = MutableStateFlow("")

    private val matches = search.debounce(150).flatMapLatest { tasks.search(it) }

    private val catalog = combine(tags.observeTags(), projects.observeProjects(), views.observeViews(), timer.active) { t, p, v, a ->
        Catalog(t, p, v, a)
    }

    private data class Catalog(val tags: List<Tag>, val projects: List<Project>, val views: List<SavedView>, val timer: FocusTimerState?)

    val state: StateFlow<BoardState> = combine(
        tasks.observeItems(),
        preferences.preferences,
        search,
        matches,
        catalog,
    ) { items, prefs, query, ids, catalog ->
        val view = prefs.board
        val today = clock.today()
        val filtered = TaskQuery.filter(items, view, query, ids)
        val sorted = TaskQuery.sort(filtered, view.sortKey, view.descending, today)
        BoardState(
            loading = false,
            view = view,
            groups = TaskQuery.group(sorted, view.groupBy, catalog.tags, today),
            summary = TaskQuery.summarize(filtered, today),
            totalTasks = items.size,
            tags = catalog.tags,
            projects = catalog.projects,
            savedViews = catalog.views,
            collapsed = prefs.collapsedGroups,
            timer = catalog.timer,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardState())

    private fun updateView(transform: (ViewDefinition) -> ViewDefinition) {
        viewModelScope.launch { preferences.update { it.copy(board = transform(it.board)) } }
    }

    fun setLayout(layout: ViewLayout) = updateView { it.copy(layout = layout) }
    fun setGroupBy(groupBy: GroupBy) = updateView { it.copy(groupBy = groupBy) }

    /** Choosing the current key again flips the direction, like clicking a column header. */
    fun setSort(key: SortKey) = updateView { if (it.sortKey == key) it.copy(descending = !it.descending) else it.copy(sortKey = key, descending = false) }

    fun toggleStatus(code: String) = updateView { it.copy(statuses = it.statuses.toggle(code)) }
    fun togglePriority(code: String) = updateView { it.copy(priorities = it.priorities.toggle(code)) }
    fun toggleTag(id: String) = updateView { it.copy(tagIds = it.tagIds.toggle(id)) }
    fun toggleProject(id: String) = updateView { it.copy(projectIds = it.projectIds.toggle(id)) }
    fun toggleSubtasks() = updateView { it.copy(showSubtasks = !it.showSubtasks) }
    fun clearFilters() = updateView { it.copy(statuses = emptyList(), priorities = emptyList(), tagIds = emptyList(), projectIds = emptyList()) }
    fun apply(view: ViewDefinition) = updateView { view }

    fun toggleCollapsed(groupId: String) {
        viewModelScope.launch {
            preferences.update { it.copy(collapsedGroups = if (groupId in it.collapsedGroups) it.collapsedGroups - groupId else it.collapsedGroups + groupId) }
        }
    }

    fun saveView(name: String) {
        viewModelScope.launch { views.save(name.ifBlank { "View" }, state.value.view) }
    }

    fun deleteView(id: String) {
        viewModelScope.launch { views.delete(id) }
    }

    /**
     * A card dropped on another column takes that column's value, as on the
     * plugin's board: its status, priority, project, or one tag swapped for another.
     */
    fun moveTo(taskId: String, from: TaskGroup?, to: TaskGroup) {
        if (from?.id == to.id) return
        viewModelScope.launch {
            when (to.groupBy) {
                GroupBy.Status -> tasks.setStatus(taskId, TaskStatus.fromCode(to.value))
                GroupBy.Priority -> tasks.edit(taskId, listOf(TaskEdit.SetPriority(Priority.fromCode(to.value))))
                GroupBy.Project -> tasks.edit(taskId, listOf(TaskEdit.MoveToProject(to.value.ifEmpty { null })))
                GroupBy.Tag -> {
                    val item = state.value.groups.flatMap { it.items }.firstOrNull { it.id == taskId } ?: return@launch
                    val current = item.tags.map { it.id }.toSet()
                    val without = from?.value?.takeIf { it.isNotEmpty() }?.let { current - it } ?: current
                    tasks.setTags(taskId, if (to.value.isEmpty()) emptySet() else without + to.value)
                }
                GroupBy.Deadline -> when (to.value) {
                    "today" -> tasks.edit(taskId, listOf(TaskEdit.Due(clock.today(), null)))
                    "none" -> tasks.edit(taskId, listOf(TaskEdit.Due(null, null)))
                    else -> Unit
                }
                GroupBy.None -> Unit
            }
        }
    }

    private fun List<String>.toggle(value: String) = if (value in this) this - value else this + value
}

