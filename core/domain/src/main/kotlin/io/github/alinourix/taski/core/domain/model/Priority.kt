package io.github.alinourix.taski.core.domain.model

/** Five ordered levels, the Obsidian Tasks vocabulary the plugin also uses. */
enum class Priority(override val code: String, val rank: Int) : Coded {
    Highest("highest", 5),
    High("high", 4),
    Medium("medium", 3),
    Low("low", 2),
    Lowest("lowest", 1),
    ;

    companion object {
        fun fromCode(code: String?): Priority? = codeOf<Priority>(code)
    }
}
