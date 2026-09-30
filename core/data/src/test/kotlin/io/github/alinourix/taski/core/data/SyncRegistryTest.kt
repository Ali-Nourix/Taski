package io.github.alinourix.taski.core.data

import io.github.alinourix.taski.core.data.db.RowShape
import io.github.alinourix.taski.core.data.db.SyncMode
import io.github.alinourix.taski.core.data.db.SyncRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class SyncRegistryTest {
    private val tables = SchemaFiles.roomTables()

    @Test
    fun everyTableIsClassifiedAsSyncedOrLocalOnly() {
        val unclassified = tables.map { it.name }.filterNot { SyncRegistry.spec(it) != null || SyncRegistry.isInternal(it) }
        assertTrue(unclassified.isEmpty(), "add these tables to SyncRegistry: $unclassified")
        val missing = SyncRegistry.tables.map { it.name } - tables.map { it.name }.toSet()
        assertTrue(missing.isEmpty(), "registered but not in the schema: $missing")
    }

    @Test
    fun everySyncedTableHasTheRequiredColumns() {
        for (spec in SyncRegistry.synced) {
            val table = tables.single { it.name == spec.name }
            val absent = SyncRegistry.REQUIRED_COLUMNS - table.columns.keys
            if (absent.isNotEmpty()) fail("${spec.name} lacks $absent")
            assertEquals(listOf("id"), table.primaryKey, "${spec.name} must be keyed by id alone")
            assertEquals("TEXT", table.columns["id"], "${spec.name}.id must hold a UUID string")
            assertEquals(
                spec.shape == RowShape.FieldClocked,
                SyncRegistry.FIELD_REVS in table.columns,
                "${spec.name}: field_revs belongs on exactly the field-clocked tables",
            )
        }
    }

    @Test
    fun nothingUsesAutoIncrementKeys() {
        for (table in tables) assertFalse(table.autoGenerate, "${table.name} has an auto-increment key")
    }

    @Test
    fun columnNamesAreSnakeCase() {
        val snake = Regex("[a-z][a-z0-9_]*")
        for (table in tables) {
            assertTrue(snake.matches(table.name), table.name)
            for (column in table.columns.keys) assertTrue(snake.matches(column), "${table.name}.$column")
        }
    }

    @Test
    fun localOnlyTablesAreNeverMarkedSynced() {
        val local = setOf("sync_outbox", "reminder_state", "timer_state", "task_fts")
        assertEquals(local, SyncRegistry.tables.filter { it.mode == SyncMode.LocalOnly }.map { it.name }.toSet())
    }
}
