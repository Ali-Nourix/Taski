package io.github.alinourix.taski.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.alinourix.taski.core.data.db.entity.TaskDependencyEntity
import io.github.alinourix.taski.core.data.db.entity.TaskTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM task_tags WHERE deleted_at IS NULL")
    fun observeLiveTaskTags(): Flow<List<TaskTagEntity>>

    @Query("SELECT * FROM task_tags WHERE task_id = :taskId")
    suspend fun taskTagsFor(taskId: String): List<TaskTagEntity>

    @Query("SELECT * FROM task_tags WHERE tag_id = :tagId AND deleted_at IS NULL")
    suspend fun liveLinksToTag(tagId: String): List<TaskTagEntity>

    @Insert
    suspend fun insertTaskTag(link: TaskTagEntity)

    @Update
    suspend fun updateTaskTag(link: TaskTagEntity)

    @Query("SELECT * FROM task_dependencies WHERE deleted_at IS NULL")
    fun observeLiveDependencies(): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies WHERE deleted_at IS NULL")
    suspend fun liveDependencies(): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies WHERE task_id = :taskId AND depends_on_id = :dependsOnId")
    suspend fun dependency(taskId: String, dependsOnId: String): TaskDependencyEntity?

    @Insert
    suspend fun insertDependency(link: TaskDependencyEntity)

    @Update
    suspend fun updateDependency(link: TaskDependencyEntity)

    @Query("UPDATE task_tags SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimTaskTags(userId: String): Int

    @Query("UPDATE task_dependencies SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimDependencies(userId: String): Int

    @Query("SELECT id FROM task_tags WHERE user_id = :userId")
    suspend fun taskTagIdsOwnedBy(userId: String): List<String>

    @Query("SELECT id FROM task_dependencies WHERE user_id = :userId")
    suspend fun dependencyIdsOwnedBy(userId: String): List<String>
}
