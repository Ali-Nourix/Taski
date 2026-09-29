package io.github.alinourix.taski.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.alinourix.taski.core.data.db.entity.ActivityEntity
import io.github.alinourix.taski.core.data.db.entity.TaskCompletionEntity
import io.github.alinourix.taski.core.data.db.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow

/** Append-only records: inserted, never updated. */
@Dao
interface HistoryDao {
    @Insert
    suspend fun insertCompletion(completion: TaskCompletionEntity)

    @Query("SELECT * FROM task_completions WHERE task_id = :taskId AND deleted_at IS NULL ORDER BY completed_at DESC")
    fun observeCompletions(taskId: String): Flow<List<TaskCompletionEntity>>

    @Insert
    suspend fun insertSession(session: TimerSessionEntity)

    @Query("SELECT * FROM timer_sessions WHERE task_id = :taskId AND deleted_at IS NULL ORDER BY started_at DESC")
    fun observeSessions(taskId: String): Flow<List<TimerSessionEntity>>

    @Query(
        """SELECT task_id, SUM((ended_at - started_at) / 1000) AS seconds FROM timer_sessions
           WHERE deleted_at IS NULL GROUP BY task_id""",
    )
    fun observeSessionTotals(): Flow<List<SessionTotal>>

    @Insert
    suspend fun insertActivity(activity: ActivityEntity)

    @Query("SELECT * FROM activity_log WHERE row_id = :rowId AND deleted_at IS NULL ORDER BY occurred_at DESC, id DESC")
    fun observeActivity(rowId: String): Flow<List<ActivityEntity>>

    @Query("UPDATE task_completions SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimCompletions(userId: String): Int

    @Query("UPDATE timer_sessions SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimSessions(userId: String): Int

    @Query("UPDATE activity_log SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimActivity(userId: String): Int

    @Query("SELECT id FROM task_completions WHERE user_id = :userId")
    suspend fun completionIdsOwnedBy(userId: String): List<String>

    @Query("SELECT id FROM timer_sessions WHERE user_id = :userId")
    suspend fun sessionIdsOwnedBy(userId: String): List<String>

    @Query("SELECT id FROM activity_log WHERE user_id = :userId")
    suspend fun activityIdsOwnedBy(userId: String): List<String>
}

data class SessionTotal(
    @androidx.room.ColumnInfo(name = "task_id") val taskId: String,
    val seconds: Long,
)
