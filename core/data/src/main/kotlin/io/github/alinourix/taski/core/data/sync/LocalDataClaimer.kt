package io.github.alinourix.taski.core.data.sync

import androidx.room.withTransaction
import io.github.alinourix.taski.core.data.db.Tables
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.domain.sync.HlcClock
import io.github.alinourix.taski.core.data.db.entity.OutboxEntity
import io.github.alinourix.taski.core.domain.id.IdGenerator
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.data.db.dao.OutboxDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the first sign-in will do: every row this device wrote without an
 * account gets the user's id and a place in the outbox, so the first push
 * uploads all of it. Rows are not otherwise changed: their revisions stay, so
 * a merge with data from another device still compares real edit times.
 */
@Singleton
class LocalDataClaimer @Inject constructor(
    private val db: TaskiDatabase,
    private val hlc: HlcClock,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend fun claim(userId: String): Int = db.withTransaction {
        val tasks = db.taskDao()
        val projects = db.projectDao()
        val tags = db.tagDao()
        val views = db.savedViewDao()
        val links = db.linkDao()
        val history = db.historyDao()

        val claimed = tasks.claim(userId) + projects.claim(userId) + tags.claim(userId) + views.claim(userId) +
            links.claimTaskTags(userId) + links.claimDependencies(userId) +
            history.claimCompletions(userId) + history.claimSessions(userId) + history.claimActivity(userId)

        val owned = listOf(
            Tables.TASKS to tasks.idsOwnedBy(userId),
            Tables.PROJECTS to projects.idsOwnedBy(userId),
            Tables.TAGS to tags.idsOwnedBy(userId),
            Tables.SAVED_VIEWS to views.idsOwnedBy(userId),
            Tables.TASK_TAGS to links.taskTagIdsOwnedBy(userId),
            Tables.TASK_DEPENDENCIES to links.dependencyIdsOwnedBy(userId),
            Tables.TASK_COMPLETIONS to history.completionIdsOwnedBy(userId),
            Tables.TIMER_SESSIONS to history.sessionIdsOwnedBy(userId),
            Tables.ACTIVITY_LOG to history.activityIdsOwnedBy(userId),
        )
        val now = clock.nowMillis()
        val rev = hlc.now().encode()
        val outbox = db.outboxDao()
        for ((table, rowIds) in owned) {
            for (rowId in rowIds) {
                outbox.record(OutboxEntity(ids.newId(), table, rowId, OutboxOp.Upsert.code, OutboxDao.encodeFields(listOf("user_id")), rev, now))
            }
        }
        claimed
    }
}
