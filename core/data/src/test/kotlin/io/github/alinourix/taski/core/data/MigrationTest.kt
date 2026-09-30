package io.github.alinourix.taski.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Upgrades a real version-1 database file, as an installed app would meet it:
 * every table is created from Room's exported v1 schema, a task is written the way
 * v1 wrote it, and the current database is opened over it.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun schema(version: Int) = File(SchemaFiles.root, "core/data/schemas/io.github.alinourix.taski.core.data.db.TaskiDatabase/$version.json")
        .readText().let { Json.parseToJsonElement(it).jsonObject["database"]!!.jsonObject }

    @Test
    fun aVersionOneDatabaseUpgradesAndKeepsItsTasks() = runTest {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val v1 = schema(1)

        val raw = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        v1["entities"]!!.jsonArray.map { it.jsonObject }.forEach { entity ->
            val table = entity["tableName"]!!.jsonPrimitive.content
            raw.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { index ->
                raw.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        v1["setupQueries"]!!.jsonArray.forEach { raw.execSQL(it.jsonPrimitive.content) }
        raw.execSQL(
            """INSERT INTO tasks (id, project_id, parent_id, title, notes, status, priority, due_date, due_time, repeat_rule, progress_done,
               progress_total, timer_minutes, reminder_offset_min, sort_key, completed_at, purged_at, field_revs, user_id, created_at, updated_at,
               deleted_at, rev, server_updated_at)
               VALUES ('t1', NULL, NULL, 'Kept across the upgrade', '', 'not_started', NULL, '2026-10-01', '17:00', NULL, NULL, NULL, NULL, NULL,
               'a0', NULL, NULL, '{}', NULL, 1, 1, NULL, '0000000000001-0000-0123456789abcdef', NULL)""",
        )
        raw.version = 1
        raw.close()

        val db = Room.databaseBuilder(context, TaskiDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val row = assertNotNull(db.taskDao().get("t1"))
            assertEquals("Kept across the upgrade", row.title)
            assertEquals("2026-10-01", row.dueDate)
            assertNull(row.startDate, "an existing task simply has no start")
            assertNull(row.startTime)
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
