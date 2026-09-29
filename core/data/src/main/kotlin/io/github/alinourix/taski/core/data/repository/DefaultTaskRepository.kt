package io.github.alinourix.taski.core.data.repository

import io.github.alinourix.taski.core.data.db.Tables
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.data.db.entity.TaskCompletionEntity
import io.github.alinourix.taski.core.data.db.entity.TaskEntity
import io.github.alinourix.taski.core.data.sync.ChangeWriter
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.model.Completion
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.order.OrderPlanner
import io.github.alinourix.taski.core.domain.recurrence.Recurrence
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.sync.CycleRules
import io.github.alinourix.taski.core.domain.sync.Edge
import io.github.alinourix.taski.core.domain.sync.FieldRevs
import io.github.alinourix.taski.core.domain.sync.Hlc
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.time.DateCodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTaskRepository @Inject constructor(
    db: TaskiDatabase,
    private val writer: ChangeWriter,
    private val clock: Clock,
) : TaskRepository {
    private val tasks = db.taskDao()
    private val links = db.linkDao()
    private val tags = db.tagDao()
    private val projects = db.projectDao()
    private val history = db.historyDao()
    private val linkWriter = LinkWriter(links)

    /** Not shared: a read right after a write must see that write, never a replayed snapshot. */
    private val items: Flow<List<TaskItem>> = combine(
        tasks.observeLive(),
        links.observeLiveTaskTags(),
        tags.observeLive(),
        projects.observeLive(),
        links.observeLiveDependencies(),
    ) { taskRows, taskTags, tagRows, projectRows, dependencies ->
        val tagById = tagRows.associate { it.id to it.toDomain() }
        val projectById = projectRows.associate { it.id to it.toDomain() }
        val statusById = taskRows.associate { it.id to it.status }
        val tagIdsByTask = taskTags.groupBy({ it.taskId }, { it.tagId })
        val children = taskRows.filter { it.parentId != null }.groupBy { it.parentId }
        val openBlockers = dependencies
            .filter { dep -> statusById[dep.dependsOnId].let { it != null && it != TaskStatus.Done.code } }
            .groupingBy { it.taskId }
            .eachCount()

        taskRows.map { row ->
            val subtasks = children[row.id].orEmpty()
            TaskItem(
                task = row.toDomain(),
                tags = tagIdsByTask[row.id].orEmpty().mapNotNull(tagById::get).sortedWith(compareBy({ it.sortKey }, { it.id })),
                project = row.projectId?.let(projectById::get),
                subtaskCount = subtasks.size,
                subtasksDone = subtasks.count { it.status == TaskStatus.Done.code },
                blockedByCount = openBlockers[row.id] ?: 0,
            )
        }
    }

    override fun observeItems(): Flow<List<TaskItem>> = items

    override fun observeItem(id: String): Flow<TaskItem?> = items.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun observeSubtasks(parentId: String): Flow<List<TaskItem>> =
        items.map { list -> list.filter { it.task.parentId == parentId }.sortedWith(compareBy({ it.task.sortKey }, { it.id })) }
            .distinctUntilChanged()

    override fun observeBlockers(taskId: String): Flow<List<Task>> =
        combine(links.observeLiveDependencies(), tasks.observeLive()) { deps, rows ->
            val wanted = deps.filter { it.taskId == taskId }.map { it.dependsOnId }.toSet()
            rows.filter { it.id in wanted }.map { it.toDomain() }
        }.distinctUntilChanged()

    override fun observeCompletions(taskId: String): Flow<List<Completion>> =
        history.observeCompletions(taskId).map { rows -> rows.map { it.toDomain() } }

    override fun observeTrash(): Flow<List<Task>> = tasks.observeTrash().map { rows ->
        val deletedWith = rows.associate { it.id to it.sync.deletedAt }
        // A subtask deleted together with its parent is restored with it, so only the parent is listed.
        rows.filter { row -> row.parentId == null || deletedWith[row.parentId] != row.sync.deletedAt }.map { it.toDomain() }
    }

    override fun search(query: String): Flow<Set<String>> {
        val match = ftsMatch(query) ?: return flowOf(emptySet())
        return tasks.search(match).map { it.toSet() }
    }

    override suspend fun create(task: NewTask): String = writer.write {
        val parent = task.parentId?.let { tasks.get(it) }?.takeIf { it.sync.deletedAt == null }
        val projectId = parent?.projectId ?: task.projectId?.takeIf { id -> projects.get(id)?.sync?.deletedAt == null }
        val siblings = tasks.siblings(projectId, parent?.id)
        val placement = OrderPlanner.insertAt(siblings.map { it.sortKey }, siblings.size)
        rekey(siblings, placement.rekeyed)

        val rev = rev()
        val id = newId()
        val entity = TaskEntity(
            id = id,
            projectId = projectId,
            parentId = parent?.id,
            title = task.title.trim(),
            notes = task.notes,
            status = task.status.code,
            priority = task.priority?.code,
            dueDate = task.dueDate?.let(DateCodes::date),
            dueTime = task.dueTime?.let(DateCodes::time),
            repeatRule = task.repeat?.encode(),
            progressDone = task.progress?.done,
            progressTotal = task.progress?.total,
            timerMinutes = task.timerMinutes,
            reminderOffsetMin = task.reminderOffsetMinutes,
            sortKey = placement.key,
            completedAt = if (task.status == TaskStatus.Done) now else null,
            fieldRevs = FieldRevs().stamp(TaskColumns.names, rev).encode(),
            sync = newSync(rev),
        )
        tasks.insert(entity)
        upserted(Tables.TASKS, id, TaskColumns.names, rev)

        val liveTags = tags.live().map { it.id }.toSet()
        with(linkWriter) {
            task.tagIds.distinct().filter { it in liveTags }.forEach { setTaskTag(null, id, it, present = true) }
        }
        log(Tables.TASKS, id, ActivityKind.Created)
        id
    }

    override suspend fun edit(id: String, edits: List<TaskEdit>) = writer.write {
        val old = tasks.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        var row = old
        for (edit in edits) {
            row = when (edit) {
                is TaskEdit.Title -> row.copy(title = edit.value.trim().ifEmpty { row.title })
                is TaskEdit.Notes -> row.copy(notes = edit.value)
                is TaskEdit.SetPriority -> row.copy(priority = edit.value?.code)
                is TaskEdit.Due -> row.copy(
                    dueDate = edit.date?.let(DateCodes::date),
                    dueTime = edit.date?.let { edit.time?.let(DateCodes::time) },
                )
                is TaskEdit.Repeat -> row.copy(repeatRule = edit.value?.encode())
                is TaskEdit.Progress -> row.copy(
                    progressDone = edit.value?.clampedDone,
                    progressTotal = edit.value?.total,
                )
                is TaskEdit.Timer -> row.copy(timerMinutes = edit.minutes?.coerceIn(1, 24 * 60))
                is TaskEdit.Reminder -> row.copy(reminderOffsetMin = edit.offsetMinutes?.coerceAtLeast(0))
                is TaskEdit.MoveToProject -> {
                    val target = edit.projectId?.takeIf { pid -> projects.get(pid)?.sync?.deletedAt == null }
                    if (target == row.projectId && row.parentId == null) row else {
                        moveSubtree(row, target)
                        val siblings = tasks.siblings(target, null).filter { it.id != row.id }
                        val placement = OrderPlanner.insertAt(siblings.map { it.sortKey }, siblings.size)
                        rekey(siblings, placement.rekeyed)
                        row.copy(projectId = target, parentId = null, sortKey = placement.key)
                    }
                }
                is TaskEdit.SetParent -> reparent(row, edit.parentId)
            }
        }
        saveTask(old, row)
    }

    private suspend fun ChangeWriter.Change.reparent(row: TaskEntity, parentId: String?): TaskEntity {
        if (parentId == row.parentId) return row
        val parent = parentId?.let { tasks.get(it) }?.takeIf { it.sync.deletedAt == null }
        if (parentId != null) {
            if (parent == null) return row
            val edges = tasks.liveParentLinks().filter { it.id != row.id }.map { Edge(it.id, it.id, it.parentId, Hlc.ZERO) }
            if (CycleRules.wouldCreateCycle(edges, Edge(row.id, row.id, parentId, Hlc.ZERO))) return row
        }
        val projectId = parent?.projectId ?: row.projectId
        if (projectId != row.projectId) moveSubtree(row, projectId)
        val siblings = tasks.siblings(projectId, parent?.id).filter { it.id != row.id }
        val placement = OrderPlanner.insertAt(siblings.map { it.sortKey }, siblings.size)
        rekey(siblings, placement.rekeyed)
        return row.copy(parentId = parent?.id, projectId = projectId, sortKey = placement.key)
    }

    /** Subtasks follow their parent into another project. */
    private suspend fun ChangeWriter.Change.moveSubtree(root: TaskEntity, projectId: String?) {
        for (child in tasks.liveChildren(root.id)) {
            moveSubtree(child, projectId)
            saveTask(child, child.copy(projectId = projectId))
        }
    }

    override suspend fun setStatus(id: String, status: TaskStatus) = writer.write {
        val old = tasks.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        val task = old.toDomain()
        if (status == task.status) return@write
        val repeat = task.repeat
        if (status == TaskStatus.Done && repeat != null) {
            val next = Recurrence.nextDueDate(repeat, task.dueDate, clock.today())
            recordCompletion(id, old.dueDate)
            saveTask(
                old,
                old.copy(
                    status = TaskStatus.NotStarted.code,
                    dueDate = DateCodes.date(next),
                    progressDone = old.progressTotal?.let { 0 },
                    completedAt = null,
                ),
            )
            for (child in tasks.liveChildren(id)) {
                if (child.status != TaskStatus.NotStarted.code) {
                    saveTask(child, child.copy(status = TaskStatus.NotStarted.code, completedAt = null))
                }
            }
            log(Tables.TASKS, id, ActivityKind.RolledForward, mapOf("from" to old.dueDate, "to" to DateCodes.date(next)))
            return@write
        }
        saveTask(old, old.copy(status = status.code, completedAt = if (status == TaskStatus.Done) now else null))
        if (status == TaskStatus.Done) {
            recordCompletion(id, old.dueDate)
            log(Tables.TASKS, id, ActivityKind.Completed)
        } else {
            log(Tables.TASKS, id, ActivityKind.StatusChanged, mapOf("from" to old.status, "to" to status.code))
        }
    }

    override suspend fun toggleDone(id: String) {
        val current = tasks.get(id)?.status ?: return
        setStatus(id, if (current == TaskStatus.Done.code) TaskStatus.NotStarted else TaskStatus.Done)
    }

    override suspend fun move(id: String, afterId: String?, beforeId: String?) = writer.write {
        val row = tasks.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        val siblings = tasks.siblings(row.projectId, row.parentId).filter { it.id != id }
        val index = when {
            afterId != null -> siblings.indexOfFirst { it.id == afterId } + 1
            beforeId != null -> siblings.indexOfFirst { it.id == beforeId }.coerceAtLeast(0)
            else -> siblings.size
        }.coerceIn(0, siblings.size)
        val placement = OrderPlanner.insertAt(siblings.map { it.sortKey }, index)
        rekey(siblings, placement.rekeyed)
        saveTask(row, row.copy(sortKey = placement.key))
    }

    override suspend fun setTags(id: String, tagIds: Set<String>) = writer.write {
        if (tasks.get(id)?.sync?.deletedAt != null) return@write
        val wanted = tagIds intersect tags.live().map { it.id }.toSet()
        val existing = links.taskTagsFor(id).associateBy { it.tagId }
        with(linkWriter) {
            for ((tagId, link) in existing) setTaskTag(link, id, tagId, present = tagId in wanted)
            for (tagId in wanted - existing.keys) setTaskTag(null, id, tagId, present = true)
        }
    }

    override suspend fun addDependency(taskId: String, dependsOnId: String): Boolean = writer.write {
        val both = tasks.getAll(listOf(taskId, dependsOnId)).filter { it.sync.deletedAt == null }
        if (both.size != 2) return@write false
        val edges = links.liveDependencies().map { Edge(it.id, it.taskId, it.dependsOnId, Hlc.ZERO) }
        if (CycleRules.wouldCreateCycle(edges, Edge("new", taskId, dependsOnId, Hlc.ZERO))) return@write false
        with(linkWriter) { setDependency(links.dependency(taskId, dependsOnId), taskId, dependsOnId, present = true) }
        true
    }

    override suspend fun removeDependency(taskId: String, dependsOnId: String) = writer.write {
        val existing = links.dependency(taskId, dependsOnId) ?: return@write
        with(linkWriter) { setDependency(existing, taskId, dependsOnId, present = false) }
    }

    override suspend fun delete(id: String) = writer.write {
        val row = tasks.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        tombstoneSubtree(row)
        log(Tables.TASKS, id, ActivityKind.Deleted)
    }

    private suspend fun ChangeWriter.Change.tombstoneSubtree(row: TaskEntity) {
        for (child in tasks.liveChildren(row.id)) tombstoneSubtree(child)
        saveTask(row, row.copy(sync = row.sync.copy(deletedAt = now)))
    }

    override suspend fun restore(id: String) = writer.write {
        val row = tasks.get(id) ?: return@write
        val deletedAt = row.sync.deletedAt ?: return@write
        val parentAlive = row.parentId?.let { tasks.get(it)?.sync?.deletedAt == null } ?: true
        val projectAlive = row.projectId?.let { projects.get(it)?.sync?.deletedAt == null } ?: true
        restoreSubtree(
            row.copy(
                parentId = if (parentAlive) row.parentId else null,
                projectId = if (projectAlive) row.projectId else null,
            ),
            original = row,
            deletedAt = deletedAt,
        )
        log(Tables.TASKS, id, ActivityKind.Restored)
    }

    private suspend fun ChangeWriter.Change.restoreSubtree(row: TaskEntity, original: TaskEntity, deletedAt: Long) {
        saveTask(original, row.copy(sync = row.sync.copy(deletedAt = null)))
        for (child in tasks.childrenDeletedAt(original.id, deletedAt)) {
            restoreSubtree(child.copy(projectId = row.projectId), child, deletedAt)
        }
    }

    override suspend fun purgeTrash() = writer.write {
        for (row in tasks.unpurgedTrash()) {
            saveTask(row, row.copy(title = "", notes = "", purgedAt = now))
            with(linkWriter) {
                links.taskTagsFor(row.id).forEach { setTaskTag(it, row.id, it.tagId, present = false) }
            }
        }
    }

    override suspend fun markOverdueAsNotDone(): Int = writer.write {
        val overdue = tasks.overdueOpen(DateCodes.date(clock.today()))
        for (row in overdue) {
            saveTask(row, row.copy(status = TaskStatus.NotDone.code))
            log(Tables.TASKS, row.id, ActivityKind.StatusChanged, mapOf("from" to row.status, "to" to TaskStatus.NotDone.code, "reason" to "overdue"))
        }
        overdue.size
    }

    private suspend fun ChangeWriter.Change.recordCompletion(taskId: String, occurrence: String?) {
        val rev = rev()
        val completion = TaskCompletionEntity(newId(), taskId, occurrence, now, newSync(rev))
        history.insertCompletion(completion)
        upserted(Tables.TASK_COMPLETIONS, completion.id, listOf("task_id", "occurrence_date", "completed_at"), rev)
    }

    private suspend fun ChangeWriter.Change.rekey(siblings: List<TaskEntity>, rekeyed: Map<Int, String>) {
        rekeyed.forEach { (index, key) -> siblings[index].let { saveTask(it, it.copy(sortKey = key)) } }
    }

    /** Writes [new] over [old], stamping exactly the columns that differ. */
    private suspend fun ChangeWriter.Change.saveTask(old: TaskEntity, new: TaskEntity) {
        val fields = TaskColumns.changed(old, new)
        if (fields.isEmpty()) return
        val rev = rev()
        val saved = new.copy(
            fieldRevs = FieldRevs.decode(old.fieldRevs).stamp(fields, rev).encode(),
            sync = new.sync.touched(now, rev.encode()),
        )
        tasks.update(saved)
        if (saved.sync.deletedAt != null && "deleted_at" in fields) {
            deleted(Tables.TASKS, saved.id, rev)
        } else {
            upserted(Tables.TASKS, saved.id, fields, rev)
        }
    }

    companion object {
        /** Each word as a quoted prefix term, so user input can never be read as FTS syntax. */
        internal fun ftsMatch(query: String): String? {
            val terms = query.split(Regex("\\s+"))
                .map { it.replace("\"", "").replace("*", "") }
                .filter { it.isNotBlank() }
            return if (terms.isEmpty()) null else terms.joinToString(" ") { "\"$it*\"" }
        }
    }
}
