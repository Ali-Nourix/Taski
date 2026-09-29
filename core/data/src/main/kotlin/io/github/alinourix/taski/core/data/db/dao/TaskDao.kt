package io.github.alinourix.taski.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.alinourix.taski.core.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL")
    fun observeLive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted_at IS NOT NULL AND purged_at IS NULL ORDER BY deleted_at DESC")
    fun observeTrash(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getAll(ids: Collection<String>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL")
    suspend fun live(): List<TaskEntity>

    @Query(
        """SELECT * FROM tasks WHERE deleted_at IS NULL
           AND ((:projectId IS NULL AND project_id IS NULL) OR project_id = :projectId)
           AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
           ORDER BY sort_key, id""",
    )
    suspend fun siblings(projectId: String?, parentId: String?): List<TaskEntity>

    @Query("SELECT id, parent_id FROM tasks WHERE deleted_at IS NULL AND parent_id IS NOT NULL")
    suspend fun liveParentLinks(): List<ParentLink>

    @Query("SELECT * FROM tasks WHERE parent_id = :parentId AND deleted_at IS NULL")
    suspend fun liveChildren(parentId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parent_id = :parentId AND deleted_at = :deletedAt")
    suspend fun childrenDeletedAt(parentId: String, deletedAt: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted_at IS NOT NULL AND purged_at IS NULL")
    suspend fun unpurgedTrash(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL AND project_id = :projectId")
    suspend fun liveInProject(projectId: String): List<TaskEntity>

    @Query(
        """SELECT * FROM tasks WHERE deleted_at IS NULL AND due_date IS NOT NULL AND due_date < :today
           AND status IN ('not_started', 'in_progress')""",
    )
    suspend fun overdueOpen(today: String): List<TaskEntity>

    @Query(
        """SELECT tasks.id FROM tasks JOIN task_fts ON tasks.rowid = task_fts.rowid
           WHERE task_fts MATCH :match AND tasks.deleted_at IS NULL""",
    )
    fun search(match: String): Flow<List<String>>

    @Insert
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claim(userId: String): Int

    @Query("SELECT id FROM tasks WHERE user_id = :userId")
    suspend fun idsOwnedBy(userId: String): List<String>
}

data class ParentLink(
    val id: String,
    @androidx.room.ColumnInfo(name = "parent_id") val parentId: String,
)
