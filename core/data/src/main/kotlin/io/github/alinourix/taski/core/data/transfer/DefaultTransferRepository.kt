package io.github.alinourix.taski.core.data.transfer

import io.github.alinourix.taski.core.domain.markdown.MarkdownTask
import io.github.alinourix.taski.core.domain.markdown.TaskMarkdown
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.repository.ImportResult
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.repository.TransferRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTransferRepository @Inject constructor(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val tags: TagRepository,
) : TransferRepository {

    override suspend fun importMarkdown(noteName: String, content: String): ImportResult {
        val parsed = TaskMarkdown.parse(content)
        if (parsed.isEmpty()) return ImportResult(0, 0, null)

        // A tag key from the note matches an existing tag whose name reduces to the same key.
        val tagIdsByKey = tags.observeTags().first().associate { TaskMarkdown.normalizeTagKey(it.name) to it.id }.toMutableMap()
        var tagsCreated = 0
        suspend fun tagIdFor(key: String): String = tagIdsByKey.getOrPut(key) {
            tagsCreated++
            tags.create(key.replace('-', ' ').replaceFirstChar { it.uppercase() })
        }

        val projectId = noteName.trim().removeSuffix(".md").takeIf { it.isNotEmpty() }?.let { name ->
            val existing = projects.observeProjects().first()
            existing.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: projects.create(name, ColorToken.next(existing.map { it.color }))
        }

        var count = 0
        suspend fun insert(task: MarkdownTask, parentId: String?) {
            val total = task.progressTotal
            val id = tasks.create(
                NewTask(
                    title = task.title.ifBlank { "—" },
                    projectId = projectId,
                    parentId = parentId,
                    status = task.status,
                    priority = task.priority,
                    dueDate = task.dueDate,
                    repeat = task.repeat,
                    tagIds = task.tagKeys.map { tagIdFor(it) },
                    progress = if (total != null && total >= 1) StepProgress(task.progressDone ?: 0, total) else null,
                ),
            )
            count++
            task.children.forEach { insert(it, id) }
        }
        parsed.forEach { insert(it, null) }
        return ImportResult(count, tagsCreated, projectId)
    }

    override suspend fun exportMarkdown(): String {
        val items = tasks.observeItems().first()
        val projectList = projects.observeProjects().first()
        val children = items.filter { it.task.parentId != null }.groupBy { it.task.parentId }
        val order = compareBy<TaskItem>({ it.task.sortKey }, { it.id })

        fun StringBuilder.write(item: TaskItem, depth: Int) {
            val task = item.task
            appendLine(TaskMarkdown.format(task.title, task.status, task.priority, task.dueDate, task.repeat, item.tags.map { it.name }, depth))
            children[task.id].orEmpty().sortedWith(order).forEach { write(it, depth + 1) }
        }

        return buildString {
            val roots = items.filter { it.task.parentId == null }
            val inbox = roots.filter { it.task.projectId == null }.sortedWith(order)
            if (inbox.isNotEmpty()) {
                appendLine("## Inbox")
                appendLine()
                inbox.forEach { write(it, 0) }
                appendLine()
            }
            for (project in projectList) {
                val inProject = roots.filter { it.task.projectId == project.id }.sortedWith(order)
                if (inProject.isEmpty()) continue
                appendLine("## ${project.name}")
                appendLine()
                inProject.forEach { write(it, 0) }
                appendLine()
            }
        }.trimEnd() + "\n"
    }
}
