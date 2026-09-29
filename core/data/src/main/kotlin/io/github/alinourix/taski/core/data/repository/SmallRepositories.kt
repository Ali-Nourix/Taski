package io.github.alinourix.taski.core.data.repository

import io.github.alinourix.taski.core.data.db.Tables
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.data.db.entity.TimerSessionEntity
import io.github.alinourix.taski.core.data.sync.ChangeWriter
import io.github.alinourix.taski.core.domain.model.ActivityEntry
import io.github.alinourix.taski.core.domain.model.TimerSession
import io.github.alinourix.taski.core.domain.repository.ActivityRepository
import io.github.alinourix.taski.core.domain.repository.TimerRepository
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTimerRepository @Inject constructor(
    db: TaskiDatabase,
    private val writer: ChangeWriter,
) : TimerRepository {
    private val state = db.timerStateDao()
    private val history = db.historyDao()

    override fun observeActive(): Flow<FocusTimerState?> = state.observe().map { it?.toDomain() }

    override suspend fun active(): FocusTimerState? = state.get()?.toDomain()

    /** Local only: no outbox entry, no revision. */
    override suspend fun setActive(state: FocusTimerState?) = this.state.replace(state?.toEntity())

    override fun observeSessions(taskId: String): Flow<List<TimerSession>> =
        history.observeSessions(taskId).map { rows -> rows.map { it.toDomain() } }

    override fun observeTotals(): Flow<Map<String, Long>> =
        history.observeSessionTotals().map { rows -> rows.associate { it.taskId to it.seconds } }

    override suspend fun recordSession(taskId: String, startedAt: Long, endedAt: Long, plannedSeconds: Int, finished: Boolean) {
        if (endedAt - startedAt < MIN_SESSION_MILLIS) return
        writer.write {
            val rev = rev()
            val session = TimerSessionEntity(newId(), taskId, startedAt, endedAt, plannedSeconds, finished, newSync(rev))
            history.insertSession(session)
            upserted(Tables.TIMER_SESSIONS, session.id, listOf("task_id", "started_at", "ended_at", "planned_seconds", "finished"), rev)
        }
    }

    private companion object {
        /** A start-and-stop by accident is not a session worth keeping. */
        const val MIN_SESSION_MILLIS = 10_000L
    }
}

@Singleton
class DefaultActivityRepository @Inject constructor(db: TaskiDatabase) : ActivityRepository {
    private val history = db.historyDao()

    override fun observeForRow(rowId: String): Flow<List<ActivityEntry>> =
        history.observeActivity(rowId).map { rows -> rows.mapNotNull { it.toDomain() } }
}

@Singleton
class DefaultReminderStateRepository @Inject constructor(db: TaskiDatabase) : io.github.alinourix.taski.core.domain.repository.ReminderStateRepository {
    private val dao = db.reminderStateDao()

    override suspend fun all() = dao.all().map { it.toRecord() }

    override suspend fun get(taskId: String) = dao.get(taskId)?.toRecord()

    override suspend fun put(record: io.github.alinourix.taski.core.domain.repository.ReminderRecord) =
        dao.upsert(io.github.alinourix.taski.core.data.db.entity.ReminderStateEntity(record.taskId, record.scheduledAt, record.deliveredKey, record.snoozedUntil))

    override suspend fun remove(taskIds: List<String>) = if (taskIds.isEmpty()) Unit else dao.delete(taskIds)

    private fun io.github.alinourix.taski.core.data.db.entity.ReminderStateEntity.toRecord() =
        io.github.alinourix.taski.core.domain.repository.ReminderRecord(taskId, scheduledAt, deliveredKey, snoozedUntil)
}
