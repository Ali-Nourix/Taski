package io.github.alinourix.taski.core.domain

import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import java.time.LocalDate

object Fixtures {
    val today: LocalDate = LocalDate.of(2026, 9, 29)

    fun item(
        id: String,
        title: String = id,
        status: TaskStatus = TaskStatus.NotStarted,
        priority: Priority? = null,
        due: LocalDate? = null,
        tags: List<Tag> = emptyList(),
        project: Project? = null,
        sortKey: String = "a0",
        createdAt: Long = 0,
        parentId: String? = null,
    ) = TaskItem(
        task = Task(
            id = id, title = title, status = status, priority = priority, dueDate = due,
            projectId = project?.id, parentId = parentId, sortKey = sortKey, createdAt = createdAt, updatedAt = createdAt,
        ),
        tags = tags,
        project = project,
    )

    fun tag(id: String, name: String = id, sortKey: String = "a0") = Tag(id, name, ColorToken.Palette.Blue, sortKey)
    fun project(id: String, name: String = id, sortKey: String = "a0") =
        Project(id, name, ColorToken.Palette.Green, sortKey, createdAt = 0)
}
