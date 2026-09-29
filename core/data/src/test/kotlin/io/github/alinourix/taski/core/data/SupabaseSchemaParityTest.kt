package io.github.alinourix.taski.core.data

import io.github.alinourix.taski.core.data.db.SyncRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The draft Supabase migration must describe exactly the synced Room tables,
 * with the same column names and compatible types, so a later sync phase can
 * move rows across unchanged.
 */
class SupabaseSchemaParityTest {
    private val sql = SchemaFiles.migration()
    private val pgTables: Map<String, Map<String, String>> = parseTables(sql)
    private val roomTables = SchemaFiles.roomTables().associateBy { it.name }

    @Test
    fun declaresExactlyTheSyncedTables() {
        assertEquals(SyncRegistry.synced.map { it.name }.toSet(), pgTables.keys)
    }

    @Test
    fun columnsMatchRoomOneForOne() {
        for ((table, pgColumns) in pgTables) {
            val room = roomTables.getValue(table).columns
            assertEquals(room.keys, pgColumns.keys, "columns of $table")
            for ((column, pgType) in pgColumns) {
                val affinity = room.getValue(column)
                val allowed = COMPATIBLE.getValue(affinity)
                assertTrue(allowed.any { pgType.startsWith(it) }, "$table.$column: Room $affinity vs Postgres $pgType")
            }
            assertTrue(pgColumns.getValue("id").startsWith("uuid"), "$table.id must be uuid")
            assertTrue(pgColumns.getValue("user_id").contains("references auth.users"), "$table.user_id must reference auth.users")
        }
    }

    @Test
    fun everyTableHasRlsTheCursorIndexAndTheTrigger() {
        for (table in pgTables.keys) {
            assertTrue("alter table public.$table enable row level security;" in sql, "RLS on $table")
            assertTrue(Regex("""create policy ${table}_\w+ on public\.$table for select using \(user_id = \(select auth\.uid\(\)\)\)""").containsMatchIn(sql), "select policy on $table")
            assertTrue("on public.$table (user_id, server_updated_at);" in sql, "cursor index on $table")
            assertTrue(Regex("""before insert or update on public\.$table\s+for each row execute function public\.set_server_updated_at\(\)""").containsMatchIn(sql), "trigger on $table")
        }
        assertTrue("new.server_updated_at := now();" in sql)
    }

    companion object {
        /** Room affinity → Postgres types that carry the same values. */
        private val COMPATIBLE = mapOf(
            "TEXT" to listOf("uuid", "text", "jsonb"),
            "INTEGER" to listOf("bigint", "integer", "boolean", "timestamptz"),
        )

        private val TABLE = Regex("""create table public\.(\w+) \((.*?)\n\);""", RegexOption.DOT_MATCHES_ALL)
        private val CONSTRAINT = Regex("^(unique|check|primary key|constraint|foreign key)\\b")

        fun parseTables(sql: String): Map<String, Map<String, String>> = TABLE.findAll(sql).associate { match ->
            val columns = match.groupValues[2].lines()
                .map { it.trim().removeSuffix(",") }
                .filter { it.isNotEmpty() && !it.startsWith("--") && !CONSTRAINT.containsMatchIn(it) }
                .associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }
            match.groupValues[1] to columns
        }
    }
}
