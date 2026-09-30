package io.github.alinourix.taski.core.domain.model

/**
 * The four states of the plugin's checkbox: `[ ]`, `[/]`, `[x]` and `[!]`.
 * [NotDone] is for something that missed its moment, not something still open.
 */
enum class TaskStatus(override val code: String, val markdown: Char) : Coded {
    NotStarted("not_started", ' '),
    InProgress("in_progress", '/'),
    Done("done", 'x'),
    NotDone("not_done", '!'),
    ;

    val isOpen: Boolean get() = this == NotStarted || this == InProgress

    /** The plugin's four-state toggle: `[ ] → [/] → [x] → [!] → [ ]`. */
    fun next(): TaskStatus = when (this) {
        NotStarted -> InProgress
        InProgress -> Done
        Done -> NotDone
        NotDone -> NotStarted
    }

    companion object {
        /** Board columns and grouping order. */
        val BOARD_ORDER = listOf(NotStarted, InProgress, NotDone, Done)

        fun fromCode(code: String?): TaskStatus = codeOf<TaskStatus>(code) ?: NotStarted

        fun fromMarkdown(char: Char): TaskStatus? = entries.firstOrNull { it.markdown == char.lowercaseChar() }
    }
}
