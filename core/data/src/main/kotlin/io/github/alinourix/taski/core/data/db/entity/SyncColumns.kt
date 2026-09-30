package io.github.alinourix.taski.core.data.db.entity

import androidx.room.ColumnInfo

/**
 * The columns every synced row carries, embedded without a prefix so the
 * names match Postgres exactly. `user_id` stays null until the user signs in;
 * `server_updated_at` stays null until a server sets it, and is the pull cursor.
 */
data class SyncColumns(
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "rev") val rev: String,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: Long? = null,
) {
    fun touched(now: Long, rev: String) = copy(updatedAt = now, rev = rev)
}
