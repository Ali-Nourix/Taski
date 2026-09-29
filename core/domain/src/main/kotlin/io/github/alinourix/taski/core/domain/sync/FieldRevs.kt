package io.github.alinourix.taski.core.domain.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-field clocks for a row whose fields are edited independently: field
 * (column name) → the revision that last wrote it. Stored as a JSON object in
 * the row's `field_revs` column.
 */
@JvmInline
value class FieldRevs(val revs: Map<String, Hlc> = emptyMap()) {

    operator fun get(field: String): Hlc? = revs[field]

    /** Every field in [fields] stamped with [rev]. */
    fun stamp(fields: Iterable<String>, rev: Hlc): FieldRevs = FieldRevs(revs + fields.associateWith { rev })

    /** The newest revision among [fields] other than those in [except]. */
    fun newest(except: Set<String> = emptySet()): Hlc? =
        revs.filterKeys { it !in except }.values.maxOrNull()

    fun encode(): String = JsonObject(revs.toSortedMap().mapValues { JsonPrimitive(it.value.encode()) }).toString()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String?): FieldRevs {
            if (value.isNullOrBlank()) return FieldRevs()
            val obj = runCatching { json.parseToJsonElement(value) as? JsonObject }.getOrNull() ?: return FieldRevs()
            return FieldRevs(
                obj.mapNotNull { (field, element) ->
                    Hlc.parseOrNull(element.jsonPrimitive.contentOrNull)?.let { field to it }
                }.toMap(),
            )
        }
    }
}
