package io.github.alinourix.taski.core.domain.markdown

import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.time.DateCodes
import java.text.Normalizer
import java.time.LocalDate

/** A task line read from an Obsidian note written with the TaskPro plugin. */
data class MarkdownTask(
    val title: String,
    val status: TaskStatus,
    val priority: Priority? = null,
    val dueDate: LocalDate? = null,
    val repeat: RepeatRule? = null,
    val tagKeys: List<String> = emptyList(),
    val progressDone: Int? = null,
    val progressTotal: Int? = null,
    val children: List<MarkdownTask> = emptyList(),
)

/**
 * The plugin's task syntax, both ways, so a vault's tasks can come to the
 * phone and go back:
 *
 * ```
 * - [/] Draft the brief @priority(high) @due(2026-12-31) @repeat(2w) @tag(work, client)
 * ```
 *
 * Reading accepts the legacy forms the plugin still reads (`⏳ 2026-12-31`,
 * `📅`, `[due:: …]`, `[priority:: …]`, the Obsidian Tasks priority emoji);
 * writing produces only the canonical markers.
 */
object TaskMarkdown {
    private val TASK_LINE = Regex("""^(\s*)[-*+] \[([ /xX!])\]\s*(.*)$""")
    private val DUE = Regex("""@due\(\s*(\d{4}-\d{2}-\d{2})\s*\)|(?:⏳|📅)\s*(\d{4}-\d{2}-\d{2})|\[(?:due|deadline)::\s*(\d{4}-\d{2}-\d{2})\]""")
    private val PRIORITY_KEYWORD = Regex("""@priority\(\s*(highest|high|medium|low|lowest)\s*\)""", RegexOption.IGNORE_CASE)
    private val PRIORITY_FIELD = Regex("""\[priority::\s*(highest|high|medium|low|lowest)\s*\]""", RegexOption.IGNORE_CASE)
    private val PRIORITY_EMOJI = Regex("🔺|⏫|🔼|🔽|⏬")
    private val REPEAT = Regex(
        """@repeat\(\s*(?:(\d+)\s*)?(d|w|m|day|days|daily|week|weeks|weekly|month|months|monthly)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val TAG = Regex("""@tag\(\s*([^()\n]*?)\s*\)""")
    private val PROGRESS = Regex("""\[progress::\s*(\d+)/(\d+)\]""")
    private val BLOCK_ID = Regex("""\s*\^[a-zA-Z0-9-]+\s*$""")
    private val TAG_KEY_DISALLOWED = Regex("""[^\p{L}\p{N}\p{M}_\-/‌]""")

    private val EMOJI_PRIORITY = mapOf(
        "🔺" to Priority.Highest, "⏫" to Priority.High, "🔼" to Priority.Medium,
        "🔽" to Priority.Low, "⏬" to Priority.Lowest,
    )

    /** Every task in a note, nested by indentation. Lines inside fenced code blocks are skipped. */
    fun parse(content: String): List<MarkdownTask> {
        data class Node(val indent: Int, val task: MarkdownTask, val children: MutableList<Node> = mutableListOf())

        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        var inFence = false
        for (line in content.lines()) {
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue
            val match = TASK_LINE.matchEntire(line) ?: continue
            val indent = indentWidth(match.groupValues[1])
            val task = parseBody(match.groupValues[3], TaskStatus.fromMarkdown(match.groupValues[2][0]) ?: TaskStatus.NotStarted)
            val node = Node(indent, task)
            while (stack.isNotEmpty() && stack.last().indent >= indent) stack.removeLast()
            if (stack.isEmpty()) roots += node else stack.last().children += node
            stack.addLast(node)
        }

        fun Node.build(): MarkdownTask = task.copy(children = children.map { it.build() })
        return roots.map { it.build() }
    }

    private fun indentWidth(whitespace: String): Int = whitespace.sumOf { if (it == '\t') 4 else 1 }

    private fun parseBody(body: String, status: TaskStatus): MarkdownTask {
        var text = body
        val due = DUE.find(text)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }?.let(DateCodes::parseDate)
        text = text.replace(DUE, " ")

        val priority = PRIORITY_KEYWORD.find(text)?.groupValues?.get(1)?.lowercase()?.let(Priority::fromCode)
            ?: PRIORITY_FIELD.find(text)?.groupValues?.get(1)?.lowercase()?.let(Priority::fromCode)
            ?: PRIORITY_EMOJI.find(text)?.value?.let(EMOJI_PRIORITY::get)
        text = text.replace(PRIORITY_KEYWORD, " ").replace(PRIORITY_FIELD, " ").replace(PRIORITY_EMOJI, " ")

        val repeat = REPEAT.find(text)?.let { m ->
            val every = m.groupValues[1].toIntOrNull() ?: 1
            val unit = when (m.groupValues[2].lowercase().first()) {
                'd' -> RepeatUnit.Day
                'w' -> RepeatUnit.Week
                else -> RepeatUnit.Month
            }
            if (every >= 1) RepeatRule(every, unit) else null
        }
        text = text.replace(REPEAT, " ")

        val tags = mutableListOf<String>()
        TAG.findAll(text).forEach { m ->
            m.groupValues[1].split(',').map(::normalizeTagKey).filter { it.isNotEmpty() && it !in tags }.forEach(tags::add)
        }
        text = text.replace(TAG, " ")

        val progress = PROGRESS.find(text)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            ?.takeIf { it.second > 0 }
        text = text.replace(PROGRESS, " ").replace(BLOCK_ID, "")

        return MarkdownTask(
            title = text.replace(Regex("""[ \t]{2,}"""), " ").trim(),
            status = status,
            priority = priority,
            dueDate = due,
            repeat = repeat,
            tagKeys = tags,
            progressDone = progress?.first,
            progressTotal = progress?.second,
        )
    }

    /** The plugin's key for a tag name: lowercase, spaces to hyphens, any script kept. */
    fun normalizeTagKey(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFC)
        .trim()
        .lowercase()
        .replace(Regex("""\s+"""), "-")
        .replace(TAG_KEY_DISALLOWED, "")
        .replace(Regex("-{2,}"), "-")
        .trim('-', '/')

    /** One task line in the plugin's canonical form, markers in the order the plugin writes them. */
    fun format(
        title: String,
        status: TaskStatus,
        priority: Priority? = null,
        dueDate: LocalDate? = null,
        repeat: RepeatRule? = null,
        tagNames: List<String> = emptyList(),
        depth: Int = 0,
    ): String = buildString {
        append("  ".repeat(depth)).append("- [").append(status.markdown).append("] ")
        append(title.replace(Regex("""\s+"""), " ").trim())
        priority?.let { append(" @priority(").append(it.code).append(')') }
        dueDate?.let { append(" @due(").append(DateCodes.date(it)).append(')') }
        repeat?.let {
            val unit = when (it.unit) {
                RepeatUnit.Day -> 'd'
                RepeatUnit.Week -> 'w'
                RepeatUnit.Month -> 'm'
            }
            append(" @repeat(").append(it.every).append(unit).append(')')
        }
        val keys = tagNames.map(::normalizeTagKey).filter { it.isNotEmpty() }.distinct()
        if (keys.isNotEmpty()) append(" @tag(").append(keys.joinToString(", ")).append(')')
    }
}
