package io.github.alinourix.taski.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Room's exported schema, read as the migration test harness would. */
data class RoomTable(val name: String, val columns: Map<String, String>, val primaryKey: List<String>, val autoGenerate: Boolean, val isFts: Boolean)

object SchemaFiles {
    val root: File = File(System.getProperty("taski.rootDir") ?: "../..")

    fun roomTables(): List<RoomTable> {
        val dir = File(root, "core/data/schemas/io.github.alinourix.taski.core.data.db.TaskiDatabase")
        val latest = dir.listFiles { f -> f.extension == "json" }!!.maxBy { it.nameWithoutExtension.toInt() }
        val database = Json.parseToJsonElement(latest.readText()).jsonObject["database"]!!.jsonObject
        return database["entities"]!!.jsonArray.map { it.jsonObject }.map { entity ->
            val pk = entity["primaryKey"]?.jsonObject
            RoomTable(
                name = entity.string("tableName"),
                columns = entity["fields"]!!.jsonArray.associate { f -> f.jsonObject.string("columnName") to f.jsonObject.string("affinity") },
                primaryKey = pk?.get("columnNames")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                autoGenerate = pk?.get("autoGenerate")?.jsonPrimitive?.boolean ?: false,
                isFts = entity["ftsVersion"] != null,
            )
        }
    }

    fun migration(): String = File(root, "supabase/migrations/0001_init.sql").readText()

    private fun JsonObject.string(key: String) = get(key)!!.jsonPrimitive.content
}
