package io.github.alinourix.taski.core.domain.repository

import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.model.ActivityEntry
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Completion
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.SavedView
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.TimerSession
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import kotlinx.coroutines.flow.Flow

/**
 * Every repository exposes Flows read from the local database only. A screen
 * cannot tell whether a row was written on this device or arrived by sync.
 */
interface TaskRepository {
    /** Every live task with its tags, project and derived values. */
    fun observeItems(): Flow<List<TaskItem>>
    fun observeItem(id: String): Flow<TaskItem?>
    fun observeSubtasks(parentId: String): Flow<List<TaskItem>>
    fun observeBlockers(taskId: String): Flow<List<Task>>
    fun observeCompletions(taskId: String): Flow<List<Completion>>
    fun observeTrash(): Flow<List<Task>>
    /** Ids of tasks whose title or notes match, from the local full-text index. */
    fun search(query: String): Flow<Set<String>>

    suspend fun create(task: NewTask): String
    suspend fun edit(id: String, edits: List<TaskEdit>)
    /**
     * Sets the status. Completing a repeating task rolls it forward instead:
     * the due date moves to the next occurrence and the task opens again.
     */
    suspend fun setStatus(id: String, status: TaskStatus)
    /** Done ⇄ not started, the checkbox. */
    suspend fun toggleDone(id: String)
    /** Moves a task between two neighbours in manual order (either may be null at an end). */
    suspend fun move(id: String, afterId: String?, beforeId: String?)
    suspend fun setTags(id: String, tagIds: Set<String>)
    /** False, and nothing written, if the dependency would close a loop. */
    suspend fun addDependency(taskId: String, dependsOnId: String): Boolean
    suspend fun removeDependency(taskId: String, dependsOnId: String)
    /** Tombstones the task and its subtasks. */
    suspend fun delete(id: String)
    suspend fun restore(id: String)
    /** Clears what the user sees of deleted tasks; the tombstones stay for sync. */
    suspend fun purgeTrash()
    /** Moves open tasks whose deadline has passed to "Not done". Returns how many changed. */
    suspend fun markOverdueAsNotDone(): Int
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeProject(id: String): Flow<Project?>
    suspend fun create(name: String, color: ColorToken): String
    suspend fun update(id: String, name: String, color: ColorToken)
    suspend fun move(id: String, afterId: String?, beforeId: String?)
    /** Tombstones the project; its tasks move to the Inbox. */
    suspend fun delete(id: String)
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    /** Tag id → number of live tasks carrying it. */
    fun observeUsage(): Flow<Map<String, Int>>
    suspend fun create(name: String, color: ColorToken? = null): String
    suspend fun update(id: String, name: String, color: ColorToken)
    suspend fun move(id: String, afterId: String?, beforeId: String?)
    /** Tombstones the tag and every link to it. */
    suspend fun delete(id: String)
}

interface SavedViewRepository {
    fun observeViews(): Flow<List<SavedView>>
    suspend fun save(name: String, definition: ViewDefinition): String
    suspend fun update(id: String, name: String, definition: ViewDefinition)
    suspend fun delete(id: String)
}

interface TimerRepository {
    /** The countdown on this device, if any. Local only: never synced. */
    fun observeActive(): Flow<FocusTimerState?>
    suspend fun active(): FocusTimerState?
    suspend fun setActive(state: FocusTimerState?)
    fun observeSessions(taskId: String): Flow<List<TimerSession>>
    /** Total focused seconds per task, from the session history. */
    fun observeTotals(): Flow<Map<String, Long>>
    suspend fun recordSession(taskId: String, startedAt: Long, endedAt: Long, plannedSeconds: Int, finished: Boolean)
}

interface ActivityRepository {
    fun observeForRow(rowId: String): Flow<List<ActivityEntry>>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

data class ImportResult(val tasks: Int, val tagsCreated: Int, val projectId: String?)

/** Moving tasks between the app and an Obsidian vault in the TaskPro plugin's syntax. */
interface TransferRepository {
    /** Reads a note's tasks into a new project named after the note (the Inbox when [noteName] is blank). */
    suspend fun importMarkdown(noteName: String, content: String): ImportResult
    /** Every live task as a note, one section per project. */
    suspend fun exportMarkdown(): String
}
