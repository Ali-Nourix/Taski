package io.github.alinourix.taski.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class RepeatUnit(override val code: String) : Coded {
    @SerialName("day") Day("day"),
    @SerialName("week") Week("week"),
    @SerialName("month") Month("month"),
}

/** Every [every] [unit]s, counted from the task's own due date: the plugin's `@repeat(2w)`. */
@Serializable
data class RepeatRule(val every: Int, val unit: RepeatUnit) {
    init {
        require(every >= 1) { "every must be at least 1" }
    }

    /** Versioned JSON, the storage form: `{"v":1,"every":2,"unit":"week"}`. */
    fun encode(): String = json.encodeToString(Stored.serializer(), Stored(VERSION, every, unit))

    @Serializable
    private data class Stored(val v: Int, val every: Int, val unit: RepeatUnit)

    companion object {
        const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true }

        val PRESETS = listOf(
            RepeatRule(1, RepeatUnit.Day),
            RepeatRule(1, RepeatUnit.Week),
            RepeatRule(2, RepeatUnit.Week),
            RepeatRule(1, RepeatUnit.Month),
        )

        /**
         * Null for anything this version cannot read, including a newer `v`
         * written by a later app. The stored text is left untouched in that
         * case, because only an edit to the repeat field ever rewrites it.
         */
        fun decode(value: String?): RepeatRule? {
            if (value.isNullOrBlank()) return null
            return runCatching {
                val obj = json.parseToJsonElement(value).jsonObject
                if (obj["v"]?.jsonPrimitive?.intOrNull != VERSION) return null
                val stored = json.decodeFromJsonElement(Stored.serializer(), obj)
                if (stored.every < 1) null else RepeatRule(stored.every, stored.unit)
            }.getOrNull()
        }

        fun isSupported(value: String?): Boolean =
            value.isNullOrBlank() || runCatching {
                json.parseToJsonElement(value).jsonObject["v"]!!.jsonPrimitive.int <= VERSION
            }.getOrDefault(false)
    }
}
