package io.github.alinourix.taski.core.domain.markdown

import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskMarkdownTest {
    @Test
    fun readsThePluginSyntax() {
        val note = """
            # Weekly
            - [/] Draft the brief @priority(high) @due(2026-12-31) @repeat(2w) @tag(work, Deep Work) ^task-abc
              - [x] Outline [progress:: 2/5]
              - [!] Missed call ⏫ 📅 2026-09-01
            - [ ] Legacy [due:: 2026-10-01] [priority:: low] @repeat(monthly)
            - [ ] کار فارسی @tag(کار)
            ```
            - [ ] not a task
            ```
        """.trimIndent()

        val tasks = TaskMarkdown.parse(note)

        assertEquals(3, tasks.size)
        val brief = tasks[0]
        assertEquals("Draft the brief", brief.title)
        assertEquals(TaskStatus.InProgress, brief.status)
        assertEquals(Priority.High, brief.priority)
        assertEquals(LocalDate.of(2026, 12, 31), brief.dueDate)
        assertEquals(RepeatRule(2, RepeatUnit.Week), brief.repeat)
        assertEquals(listOf("work", "deep-work"), brief.tagKeys)
        assertEquals(listOf("Outline", "Missed call"), brief.children.map { it.title })
        assertEquals(2 to 5, brief.children[0].progressDone to brief.children[0].progressTotal)
        assertEquals(TaskStatus.NotDone, brief.children[1].status)
        assertEquals(Priority.High, brief.children[1].priority)
        assertEquals(LocalDate.of(2026, 9, 1), brief.children[1].dueDate)

        assertEquals(Priority.Low, tasks[1].priority)
        assertEquals(RepeatRule(1, RepeatUnit.Month), tasks[1].repeat)
        assertEquals(listOf("کار"), tasks[2].tagKeys)
    }

    @Test
    fun writesTheCanonicalMarkers() {
        assertEquals(
            "  - [x] Ship it @priority(highest) @due(2026-12-31) @repeat(1w) @tag(deep-work)",
            TaskMarkdown.format(
                "Ship  it", TaskStatus.Done, Priority.Highest, LocalDate.of(2026, 12, 31),
                RepeatRule(1, RepeatUnit.Week), listOf("Deep Work"), depth = 1,
            ),
        )
    }

    @Test
    fun formattedLinesReadBackTheSame() {
        val line = TaskMarkdown.format("Round trip", TaskStatus.NotDone, Priority.Low, LocalDate.of(2027, 1, 2), RepeatRule(3, RepeatUnit.Day), listOf("a", "b"))
        val parsed = TaskMarkdown.parse(line).single()
        assertEquals(MarkdownTask("Round trip", TaskStatus.NotDone, Priority.Low, LocalDate.of(2027, 1, 2), RepeatRule(3, RepeatUnit.Day), listOf("a", "b")), parsed)
    }
}
