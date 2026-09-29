package io.github.alinourix.taski.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class ViewLayout { @SerialName("list") List, @SerialName("board") Board }

@Serializable
enum class GroupBy {
    @SerialName("none") None,
    @SerialName("status") Status,
    @SerialName("tag") Tag,
    @SerialName("priority") Priority,
    @SerialName("deadline") Deadline,
    @SerialName("project") Project,
}

@Serializable
enum class SortKey {
    @SerialName("manual") Manual,
    @SerialName("title") Title,
    @SerialName("status") Status,
    @SerialName("priority") Priority,
    @SerialName("deadline") Deadline,
    @SerialName("progress") Progress,
    @SerialName("project") Project,
    @SerialName("created") Created,
}

/**
 * How a list of tasks is filtered, grouped and ordered: the board's state, and
 * what a saved view stores (as versioned JSON). Filters hold stable codes and
 * ids, never display names. An empty filter list means "no filter".
 */
@Serializable
data class ViewDefinition(
    val v: Int = VERSION,
    val layout: ViewLayout = ViewLayout.List,
    val groupBy: GroupBy = GroupBy.Status,
    val sortKey: SortKey = SortKey.Manual,
    val descending: Boolean = false,
    /** Status codes. */
    val statuses: List<String> = emptyList(),
    /** Priority codes, plus [NONE] for tasks without one. */
    val priorities: List<String> = emptyList(),
    /** Tag ids, plus [NONE] for untagged tasks. A task matches if it has any of them. */
    val tagIds: List<String> = emptyList(),
    /** Project ids, plus [NONE] for the Inbox. */
    val projectIds: List<String> = emptyList(),
    val showSubtasks: Boolean = true,
) {
    val hasFilters: Boolean
        get() = statuses.isNotEmpty() || priorities.isNotEmpty() || tagIds.isNotEmpty() || projectIds.isNotEmpty()

    fun encode(): String = json.encodeToString(serializer(), copy(v = VERSION))

    companion object {
        const val VERSION = 1
        const val NONE = ""
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null for text from a newer version or anything unreadable. */
        fun decode(value: String?): ViewDefinition? {
            if (value.isNullOrBlank()) return null
            return runCatching {
                val version = json.parseToJsonElement(value).jsonObject["v"]?.jsonPrimitive?.intOrNull
                if (version != VERSION) null else json.decodeFromString(serializer(), value)
            }.getOrNull()
        }
    }
}
