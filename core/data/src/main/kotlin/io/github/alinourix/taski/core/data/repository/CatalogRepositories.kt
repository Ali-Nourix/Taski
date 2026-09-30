package io.github.alinourix.taski.core.data.repository

import io.github.alinourix.taski.core.data.db.Tables
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.data.db.entity.ProjectEntity
import io.github.alinourix.taski.core.data.db.entity.SavedViewEntity
import io.github.alinourix.taski.core.data.db.entity.TagEntity
import io.github.alinourix.taski.core.data.sync.ChangeWriter
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.SavedView
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.order.OrderPlanner
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.SavedViewRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.sync.FieldRevs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Where [id] lands when moved between [afterId] and [beforeId] in an ordered list. */
internal fun <T> planMove(ordered: List<T>, idOf: (T) -> String, keyOf: (T) -> String, id: String, afterId: String?, beforeId: String?) =
    ordered.filter { idOf(it) != id }.let { rest ->
        val index = when {
            afterId != null -> rest.indexOfFirst { idOf(it) == afterId } + 1
            beforeId != null -> rest.indexOfFirst { idOf(it) == beforeId }.coerceAtLeast(0)
            else -> rest.size
        }.coerceIn(0, rest.size)
        rest to OrderPlanner.insertAt(rest.map(keyOf), index)
    }

@Singleton
class DefaultProjectRepository @Inject constructor(
    db: TaskiDatabase,
    private val writer: ChangeWriter,
) : ProjectRepository {
    private val projects = db.projectDao()
    private val tasks = db.taskDao()

    override fun observeProjects(): Flow<List<Project>> = projects.observeLive().map { rows -> rows.map { it.toDomain() } }

    override fun observeProject(id: String): Flow<Project?> =
        projects.observe(id).map { row -> row?.takeIf { it.sync.deletedAt == null }?.toDomain() }

    override suspend fun create(name: String, color: ColorToken): String = writer.write {
        val existing = projects.live()
        val placement = OrderPlanner.insertAt(existing.map { it.sortKey }, existing.size)
        rekey(existing, placement.rekeyed)
        val rev = rev()
        val row = ProjectEntity(newId(), name.trim(), color.code, placement.key, FieldRevs().stamp(ProjectColumns.names, rev).encode(), newSync(rev))
        projects.insert(row)
        upserted(Tables.PROJECTS, row.id, ProjectColumns.names, rev)
        row.id
    }

    override suspend fun update(id: String, name: String, color: ColorToken) = writer.write {
        val old = projects.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        save(old, old.copy(name = name.trim().ifEmpty { old.name }, color = color.code))
    }

    override suspend fun move(id: String, afterId: String?, beforeId: String?) = writer.write {
        val row = projects.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        val (rest, placement) = planMove(projects.live(), { it.id }, { it.sortKey }, id, afterId, beforeId)
        rekey(rest, placement.rekeyed)
        save(row, row.copy(sortKey = placement.key))
    }

    override suspend fun delete(id: String) = writer.write {
        val old = projects.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        save(old, old.copy(sync = old.sync.copy(deletedAt = now)))
        // The same rule the sync engine applies to a project deleted elsewhere.
        for (task in tasks.liveInProject(id)) {
            val rev = rev()
            val moved = task.copy(
                projectId = null,
                fieldRevs = FieldRevs.decode(task.fieldRevs).stamp(listOf("project_id"), rev).encode(),
                sync = task.sync.touched(now, rev.encode()),
            )
            tasks.update(moved)
            upserted(Tables.TASKS, task.id, listOf("project_id"), rev)
            log(Tables.TASKS, task.id, ActivityKind.MovedToInbox, mapOf("project_id" to id))
        }
    }

    private suspend fun ChangeWriter.Change.rekey(rows: List<ProjectEntity>, rekeyed: Map<Int, String>) =
        rekeyed.forEach { (i, key) -> save(rows[i], rows[i].copy(sortKey = key)) }

    private suspend fun ChangeWriter.Change.save(old: ProjectEntity, new: ProjectEntity) {
        val fields = ProjectColumns.changed(old, new)
        if (fields.isEmpty()) return
        val rev = rev()
        projects.update(new.copy(fieldRevs = FieldRevs.decode(old.fieldRevs).stamp(fields, rev).encode(), sync = new.sync.touched(now, rev.encode())))
        if (new.sync.deletedAt != null && "deleted_at" in fields) deleted(Tables.PROJECTS, old.id, rev) else upserted(Tables.PROJECTS, old.id, fields, rev)
    }
}

@Singleton
class DefaultTagRepository @Inject constructor(
    db: TaskiDatabase,
    private val writer: ChangeWriter,
) : TagRepository {
    private val tags = db.tagDao()
    private val links = db.linkDao()
    private val linkWriter = LinkWriter(links)

    override fun observeTags(): Flow<List<Tag>> = tags.observeLive().map { rows -> rows.map { it.toDomain() } }

    override fun observeUsage(): Flow<Map<String, Int>> = tags.observeUsage().map { rows -> rows.associate { it.tagId to it.count } }

    override suspend fun create(name: String, color: ColorToken?): String = writer.write {
        val existing = tags.live()
        val placement = OrderPlanner.insertAt(existing.map { it.sortKey }, existing.size)
        rekey(existing, placement.rekeyed)
        val rev = rev()
        val pick = color ?: ColorToken.next(existing.map { ColorToken.fromCode(it.color) })
        val row = TagEntity(newId(), name.trim().ifEmpty { "Tag" }, pick.code, placement.key, FieldRevs().stamp(TagColumns.names, rev).encode(), newSync(rev))
        tags.insert(row)
        upserted(Tables.TAGS, row.id, TagColumns.names, rev)
        row.id
    }

    override suspend fun update(id: String, name: String, color: ColorToken) = writer.write {
        val old = tags.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        save(old, old.copy(name = name.trim().ifEmpty { old.name }, color = color.code))
    }

    override suspend fun move(id: String, afterId: String?, beforeId: String?) = writer.write {
        val row = tags.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        val (rest, placement) = planMove(tags.live(), { it.id }, { it.sortKey }, id, afterId, beforeId)
        rekey(rest, placement.rekeyed)
        save(row, row.copy(sortKey = placement.key))
    }

    override suspend fun delete(id: String) = writer.write {
        val old = tags.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        with(linkWriter) {
            links.liveLinksToTag(id).forEach { setTaskTag(it, it.taskId, id, present = false) }
        }
        save(old, old.copy(sync = old.sync.copy(deletedAt = now)))
    }

    private suspend fun ChangeWriter.Change.rekey(rows: List<TagEntity>, rekeyed: Map<Int, String>) =
        rekeyed.forEach { (i, key) -> save(rows[i], rows[i].copy(sortKey = key)) }

    private suspend fun ChangeWriter.Change.save(old: TagEntity, new: TagEntity) {
        val fields = TagColumns.changed(old, new)
        if (fields.isEmpty()) return
        val rev = rev()
        tags.update(new.copy(fieldRevs = FieldRevs.decode(old.fieldRevs).stamp(fields, rev).encode(), sync = new.sync.touched(now, rev.encode())))
        if (new.sync.deletedAt != null && "deleted_at" in fields) deleted(Tables.TAGS, old.id, rev) else upserted(Tables.TAGS, old.id, fields, rev)
    }
}

@Singleton
class DefaultSavedViewRepository @Inject constructor(
    db: TaskiDatabase,
    private val writer: ChangeWriter,
) : SavedViewRepository {
    private val views = db.savedViewDao()

    override fun observeViews(): Flow<List<SavedView>> = views.observeLive().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun save(name: String, definition: ViewDefinition): String = writer.write {
        val existing = views.live()
        val placement = OrderPlanner.insertAt(existing.map { it.sortKey }, existing.size)
        val rev = rev()
        val row = SavedViewEntity(newId(), name.trim(), definition.encode(), placement.key, FieldRevs().stamp(SavedViewColumns.names, rev).encode(), newSync(rev))
        views.insert(row)
        upserted(Tables.SAVED_VIEWS, row.id, SavedViewColumns.names, rev)
        row.id
    }

    override suspend fun update(id: String, name: String, definition: ViewDefinition) = writer.write {
        val old = views.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        save(old, old.copy(name = name.trim().ifEmpty { old.name }, definition = definition.encode()))
    }

    override suspend fun delete(id: String) = writer.write {
        val old = views.get(id)?.takeIf { it.sync.deletedAt == null } ?: return@write
        save(old, old.copy(sync = old.sync.copy(deletedAt = now)))
    }

    private suspend fun ChangeWriter.Change.save(old: SavedViewEntity, new: SavedViewEntity) {
        val fields = SavedViewColumns.changed(old, new)
        if (fields.isEmpty()) return
        val rev = rev()
        views.update(new.copy(fieldRevs = FieldRevs.decode(old.fieldRevs).stamp(fields, rev).encode(), sync = new.sync.touched(now, rev.encode())))
        if (new.sync.deletedAt != null && "deleted_at" in fields) deleted(Tables.SAVED_VIEWS, old.id, rev) else upserted(Tables.SAVED_VIEWS, old.id, fields, rev)
    }
}
