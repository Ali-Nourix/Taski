package io.github.alinourix.taski.core.domain.query

import io.github.alinourix.taski.core.domain.Fixtures.item
import io.github.alinourix.taski.core.domain.Fixtures.project
import io.github.alinourix.taski.core.domain.Fixtures.tag
import io.github.alinourix.taski.core.domain.Fixtures.today
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskQueryTest {
    private val work = tag("work", "Work", "a0")
    private val home = tag("home", "Home", "a1")
    private val client = project("p1", "Client")

    private val items = listOf(
        item("a", "Write brief", priority = Priority.High, due = today.minusDays(2), tags = listOf(work), sortKey = "a3"),
        item("b", "Water plants", status = TaskStatus.Done, due = today.minusDays(1), tags = listOf(home), sortKey = "a1"),
        item("c", "Call client", status = TaskStatus.InProgress, priority = Priority.Highest, due = today, project = client, sortKey = "a2"),
        item("d", "Read", tags = listOf(work, home), sortKey = "a4"),
        item("e", "Plan trip", priority = Priority.Low, due = today.plusDays(20), sortKey = "a0"),
    )

    @Test
    fun filtersByStatusPriorityTagAndProject() {
        assertEquals(listOf("a", "d", "e"), TaskQuery.filter(items, ViewDefinition(statuses = listOf("not_started"))).map { it.id })
        assertEquals(listOf("b", "d"), TaskQuery.filter(items, ViewDefinition(priorities = listOf(ViewDefinition.NONE))).map { it.id })
        assertEquals(listOf("a", "d"), TaskQuery.filter(items, ViewDefinition(tagIds = listOf("work"))).map { it.id })
        assertEquals(listOf("c", "e"), TaskQuery.filter(items, ViewDefinition(tagIds = listOf(ViewDefinition.NONE))).map { it.id })
        assertEquals(listOf("c"), TaskQuery.filter(items, ViewDefinition(projectIds = listOf("p1"))).map { it.id })
    }

    @Test
    fun searchMatchesTitlesTagNamesAndProjectNames() {
        assertEquals(listOf("b", "d"), TaskQuery.filter(items, ViewDefinition(), "home").map { it.id })
        assertEquals(listOf("c"), TaskQuery.filter(items, ViewDefinition(), "client call").map { it.id })
        assertEquals(listOf("e"), TaskQuery.filter(items, ViewDefinition(), "zzz", matchingIds = setOf("e")).map { it.id })
    }

    @Test
    fun sortsWithMissingValuesLastInEitherDirection() {
        assertEquals(listOf("c", "a", "e", "b", "d"), TaskQuery.sort(items, SortKey.Priority, false, today).map { it.id })
        assertEquals(listOf("e", "a", "c", "b", "d"), TaskQuery.sort(items, SortKey.Priority, true, today).map { it.id })
        assertEquals(listOf("a", "b", "c", "e", "d"), TaskQuery.sort(items, SortKey.Deadline, false, today).map { it.id })
        assertEquals(listOf("e", "b", "c", "a", "d"), TaskQuery.sort(items, SortKey.Manual, false, today).map { it.id })
    }

    @Test
    fun groupsLikeTheBoard() {
        val byStatus = TaskQuery.group(items, GroupBy.Status, listOf(work, home), today)
        assertEquals(listOf("not_started", "in_progress", "not_done", "done"), byStatus.map { it.value })
        assertEquals(0, byStatus.first { it.value == "not_done" }.items.size, "empty status columns are kept")

        val byTag = TaskQuery.group(items, GroupBy.Tag, listOf(work, home), today)
        assertEquals(listOf("work", "home", ViewDefinition.NONE), byTag.map { it.value })
        assertEquals(listOf("a", "d"), byTag[0].items.map { it.id })
        assertEquals(listOf("b", "d"), byTag[1].items.map { it.id }, "a task with two tags is under both")

        val byDeadline = TaskQuery.group(items, GroupBy.Deadline, emptyList(), today)
        assertEquals(listOf("overdue", "today", "later", "past", "none"), byDeadline.map { it.value })
    }

    @Test
    fun todayHoldsWhatIsDueOrUnderWay() {
        assertEquals(listOf("a", "c"), TaskQuery.today(items, today).map { it.id })
        assertEquals(Summary(total = 5, done = 1, overdue = 1), TaskQuery.summarize(items, today))
    }

    @Test
    fun viewDefinitionsAreVersionedJson() {
        val view = ViewDefinition(groupBy = GroupBy.Tag, sortKey = SortKey.Deadline, tagIds = listOf("work"))
        assertEquals(view, ViewDefinition.decode(view.encode()))
        assertEquals(null, ViewDefinition.decode("""{"v":9}"""))
    }
}
