package io.github.alinourix.taski.core.data.repository

import io.github.alinourix.taski.core.data.db.entity.ActivityEntity
import io.github.alinourix.taski.core.data.db.entity.ProjectEntity
import io.github.alinourix.taski.core.data.db.entity.SavedViewEntity
import io.github.alinourix.taski.core.data.db.entity.TagEntity
import io.github.alinourix.taski.core.data.db.entity.TaskCompletionEntity
import io.github.alinourix.taski.core.data.db.entity.TaskEntity
import io.github.alinourix.taski.core.data.db.entity.TimerSessionEntity
import io.github.alinourix.taski.core.data.db.entity.TimerStateEntity
import io.github.alinourix.taski.core.domain.model.ActivityEntry
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Completion
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.SavedView
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.TimerSession
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.time.DateCodes
import io.github.alinourix.taski.core.domain.timer.FocusTimerState

internal fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    notes = notes,
    status = TaskStatus.fromCode(status),
    priority = Priority.fromCode(priority),
    projectId = projectId,
    parentId = parentId,
    startDate = DateCodes.parseDate(startDate),
    startTime = DateCodes.parseTime(startTime),
    dueDate = DateCodes.parseDate(dueDate),
    dueTime = DateCodes.parseTime(dueTime),
    repeat = RepeatRule.decode(repeatRule),
    progress = if (progressTotal != null && progressTotal >= 1) StepProgress(progressDone ?: 0, progressTotal) else null,
    timerMinutes = timerMinutes,
    reminderOffsetMinutes = reminderOffsetMin,
    sortKey = sortKey,
    completedAt = completedAt,
    createdAt = sync.createdAt,
    updatedAt = sync.updatedAt,
    deletedAt = sync.deletedAt,
)

internal fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    color = ColorToken.fromCode(color),
    sortKey = sortKey,
    createdAt = sync.createdAt,
    deletedAt = sync.deletedAt,
)

internal fun TagEntity.toDomain() = Tag(id = id, name = name, color = ColorToken.fromCode(color), sortKey = sortKey)

internal fun SavedViewEntity.toDomain(): SavedView? =
    ViewDefinition.decode(definition)?.let { SavedView(id, name, it, sortKey) }

internal fun TaskCompletionEntity.toDomain() =
    Completion(id, taskId, DateCodes.parseDate(occurrenceDate), completedAt)

internal fun TimerSessionEntity.toDomain() =
    TimerSession(id, taskId, startedAt, endedAt, plannedSeconds, finished)

internal fun ActivityEntity.toDomain(): ActivityEntry? =
    ActivityKind.fromCode(kind)?.let { ActivityEntry(id, entity, rowId, it, payload, occurredAt) }

internal fun TimerStateEntity.toDomain() =
    FocusTimerState(taskId, plannedSeconds, bankedSeconds, runningSince, startedAt)

internal fun FocusTimerState.toEntity() =
    TimerStateEntity(taskId, plannedSeconds, bankedSeconds, runningSince, startedAt)
