package io.github.alinourix.taski.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.markdown.TaskMarkdown
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** What the user picked with the chips, before the typed text is read. */
data class QuickDraft(
    val title: String = "",
    val projectId: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority? = null,
    val repeat: RepeatRule? = null,
    val tagIds: Set<String> = emptySet(),
)

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val tagRepository: TagRepository,
    projects: ProjectRepository,
) : ViewModel() {
    val tags: StateFlow<List<Tag>> = tagRepository.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects: StateFlow<List<Project>> = projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Adds the task. Plugin markers typed into the title (`@due(…)`,
     * `@priority(…)`, `@repeat(…)`, `@tag(…)`) are read like the plugin reads a
     * note; a chip the user picked wins over a marker for the same field.
     */
    fun add(draft: QuickDraft, onAdded: (String) -> Unit) {
        viewModelScope.launch {
            val parsed = TaskMarkdown.parse("- [ ] ${draft.title}").firstOrNull() ?: return@launch
            if (parsed.title.isBlank()) return@launch
            val known = tagRepository.observeTags().first().associateBy { TaskMarkdown.normalizeTagKey(it.name) }
            val typedTags = parsed.tagKeys.map { key -> known[key]?.id ?: tagRepository.create(key.replace('-', ' ')) }
            val id = tasks.create(
                NewTask(
                    title = parsed.title,
                    projectId = draft.projectId,
                    dueDate = draft.dueDate ?: parsed.dueDate,
                    dueTime = draft.dueTime,
                    priority = draft.priority ?: parsed.priority,
                    repeat = draft.repeat ?: parsed.repeat,
                    tagIds = (draft.tagIds + typedTags).toList(),
                ),
            )
            onAdded(id)
        }
    }

    fun createTag(name: String, color: ColorToken, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(tagRepository.create(name, color)) }
    }
}
