package io.github.alinourix.taski.core.data.repository

import io.github.alinourix.taski.core.data.db.Tables
import io.github.alinourix.taski.core.data.db.dao.LinkDao
import io.github.alinourix.taski.core.data.db.entity.TaskDependencyEntity
import io.github.alinourix.taski.core.data.db.entity.TaskTagEntity
import io.github.alinourix.taski.core.data.sync.ChangeWriter

/**
 * Adds and removes link rows. A pair has one row for life: removing it
 * tombstones the row and adding it again revives the same row with a newer
 * revision, so add/remove on two devices resolves by time.
 */
internal class LinkWriter(private val links: LinkDao) {

    suspend fun ChangeWriter.Change.setTaskTag(existing: TaskTagEntity?, taskId: String, tagId: String, present: Boolean) {
        val live = existing != null && existing.sync.deletedAt == null
        if (live == present) return
        val rev = rev()
        if (existing == null) {
            val link = TaskTagEntity(newId(), taskId, tagId, newSync(rev))
            links.insertTaskTag(link)
            upserted(Tables.TASK_TAGS, link.id, listOf("task_id", "tag_id", "deleted_at"), rev)
            return
        }
        val updated = existing.copy(sync = existing.sync.touched(now, rev.encode()).copy(deletedAt = if (present) null else now))
        links.updateTaskTag(updated)
        if (present) upserted(Tables.TASK_TAGS, updated.id, LinkFields, rev) else deleted(Tables.TASK_TAGS, updated.id, rev)
    }

    suspend fun ChangeWriter.Change.setDependency(existing: TaskDependencyEntity?, taskId: String, dependsOnId: String, present: Boolean) {
        val live = existing != null && existing.sync.deletedAt == null
        if (live == present) return
        val rev = rev()
        if (existing == null) {
            val link = TaskDependencyEntity(newId(), taskId, dependsOnId, newSync(rev))
            links.insertDependency(link)
            upserted(Tables.TASK_DEPENDENCIES, link.id, listOf("task_id", "depends_on_id", "deleted_at"), rev)
            return
        }
        val updated = existing.copy(sync = existing.sync.touched(now, rev.encode()).copy(deletedAt = if (present) null else now))
        links.updateDependency(updated)
        if (present) upserted(Tables.TASK_DEPENDENCIES, updated.id, LinkFields, rev) else deleted(Tables.TASK_DEPENDENCIES, updated.id, rev)
    }
}
