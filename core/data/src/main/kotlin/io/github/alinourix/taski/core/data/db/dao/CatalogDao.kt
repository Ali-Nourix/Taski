package io.github.alinourix.taski.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.alinourix.taski.core.data.db.entity.ProjectEntity
import io.github.alinourix.taski.core.data.db.entity.SavedViewEntity
import io.github.alinourix.taski.core.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deleted_at IS NULL ORDER BY sort_key, id")
    fun observeLive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE deleted_at IS NULL ORDER BY sort_key, id")
    suspend fun live(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claim(userId: String): Int

    @Query("SELECT id FROM projects WHERE user_id = :userId")
    suspend fun idsOwnedBy(userId: String): List<String>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY sort_key, id")
    fun observeLive(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY sort_key, id")
    suspend fun live(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    @Query(
        """SELECT task_tags.tag_id AS tag_id, COUNT(*) AS count FROM task_tags
           JOIN tasks ON tasks.id = task_tags.task_id
           WHERE task_tags.deleted_at IS NULL AND tasks.deleted_at IS NULL
           GROUP BY task_tags.tag_id""",
    )
    fun observeUsage(): Flow<List<TagUsage>>

    @Query("UPDATE tags SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claim(userId: String): Int

    @Query("SELECT id FROM tags WHERE user_id = :userId")
    suspend fun idsOwnedBy(userId: String): List<String>
}

data class TagUsage(
    @androidx.room.ColumnInfo(name = "tag_id") val tagId: String,
    val count: Int,
)

@Dao
interface SavedViewDao {
    @Query("SELECT * FROM saved_views WHERE deleted_at IS NULL ORDER BY sort_key, id")
    fun observeLive(): Flow<List<SavedViewEntity>>

    @Query("SELECT * FROM saved_views WHERE deleted_at IS NULL ORDER BY sort_key, id")
    suspend fun live(): List<SavedViewEntity>

    @Query("SELECT * FROM saved_views WHERE id = :id")
    suspend fun get(id: String): SavedViewEntity?

    @Insert
    suspend fun insert(view: SavedViewEntity)

    @Update
    suspend fun update(view: SavedViewEntity)

    @Query("UPDATE saved_views SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claim(userId: String): Int

    @Query("SELECT id FROM saved_views WHERE user_id = :userId")
    suspend fun idsOwnedBy(userId: String): List<String>
}
