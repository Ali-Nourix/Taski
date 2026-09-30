package io.github.alinourix.taski.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.data.db.dao.OutboxDao
import io.github.alinourix.taski.core.data.repository.DefaultProjectRepository
import io.github.alinourix.taski.core.data.repository.DefaultTagRepository
import io.github.alinourix.taski.core.data.repository.DefaultTaskRepository
import io.github.alinourix.taski.core.data.repository.DefaultTimerRepository
import io.github.alinourix.taski.core.data.sync.ChangeWriter
import io.github.alinourix.taski.core.data.sync.LocalDataClaimer
import io.github.alinourix.taski.core.data.transfer.DefaultTransferRepository
import io.github.alinourix.taski.core.domain.id.UuidV7Generator
import io.github.alinourix.taski.core.domain.model.ActivityKind
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.sync.DefaultHlcClock
import io.github.alinourix.taski.core.domain.sync.FieldRevs
import io.github.alinourix.taski.core.domain.sync.Hlc
import io.github.alinourix.taski.core.domain.time.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: TaskiDatabase
    private val clock = FixedClock(LocalDate.of(2026, 9, 29).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    private val hlc = DefaultHlcClock(clock, "0123456789abcdef")
    private val ids = UuidV7Generator(clock, Random(42))
    private lateinit var writer: ChangeWriter
    private lateinit var tasks: DefaultTaskRepository
    private lateinit var tags: DefaultTagRepository
    private lateinit var projects: DefaultProjectRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TaskiDatabase::class.java).build()
        writer = ChangeWriter(db, hlc, clock, ids)
        tasks = DefaultTaskRepository(db, writer, clock)
        tags = DefaultTagRepository(db, writer)
        projects = DefaultProjectRepository(db, writer)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun outbox() = db.outboxDao().all()

    @Test
    fun creatingATaskWritesTheRowAndTheOutboxTogether() = runTest {
        val id = tasks.create(NewTask("Write brief", priority = Priority.High))

        val row = db.taskDao().get(id)!!
        assertEquals("not_started", row.status)
        assertEquals("high", row.priority)
        assertNull(row.sync.userId)
        assertNull(row.sync.serverUpdatedAt)
        assertEquals(Hlc.parse(row.sync.rev), FieldRevs.decode(row.fieldRevs)["title"])
        val entry = outbox().single { it.entity == "tasks" }
        assertEquals(id, entry.rowId)
        assertEquals("upsert", entry.op)
        assertEquals(1, outbox().count { it.entity == "activity_log" })
    }

    @Test
    fun anEditStampsOnlyTheFieldsItChangedAndTheOutboxCompacts() = runTest {
        val id = tasks.create(NewTask("Draft"))
        val before = FieldRevs.decode(db.taskDao().get(id)!!.fieldRevs)
        clock.advance(1_000)

        tasks.edit(id, listOf(TaskEdit.Title("Draft v2"), TaskEdit.SetPriority(null)))

        val after = FieldRevs.decode(db.taskDao().get(id)!!.fieldRevs)
        assertTrue(after["title"]!! > before["title"]!!)
        assertEquals(before["priority"], after["priority"], "an unchanged value is not re-stamped")
        assertEquals(before["notes"], after["notes"])
        val taskEntries = outbox().filter { it.entity == "tasks" }
        assertEquals(1, taskEntries.size, "several changes to one row fold into one entry")
        assertTrue("title" in OutboxDao.decodeFields(taskEntries.single().changedFields))
    }

    @Test
    fun completingARepeatingTaskRollsItForward() = runTest {
        val id = tasks.create(
            NewTask("Water plants", dueDate = LocalDate.of(2026, 9, 26), repeat = RepeatRule(1, RepeatUnit.Week), progress = StepProgress(3, 5)),
        )
        val sub = tasks.create(NewTask("Fill can", parentId = id))
        tasks.setStatus(sub, TaskStatus.Done)

        tasks.setStatus(id, TaskStatus.Done)

        val task = tasks.observeItem(id).first()!!.task
        assertEquals(TaskStatus.NotStarted, task.status)
        assertEquals(LocalDate.of(2026, 10, 3), task.dueDate, "keeps its weekday")
        assertEquals(0, task.progress?.done)
        assertEquals(TaskStatus.NotStarted, tasks.observeItem(sub).first()!!.task.status, "subtasks reopen")
        assertEquals(listOf(LocalDate.of(2026, 9, 26)), tasks.observeCompletions(id).first().map { it.occurrenceDate })
    }

    @Test
    fun aTaskCanSpanFromAStartToItsDue() = runTest {
        val id = tasks.create(
            NewTask("Write chapter", startDate = LocalDate.of(2026, 9, 28), startTime = LocalTime.of(9, 0), dueDate = LocalDate.of(2026, 9, 30), dueTime = LocalTime.of(17, 0)),
        )

        val task = tasks.observeItem(id).first()!!.task
        assertEquals(LocalDate.of(2026, 9, 28), task.startDate)
        assertEquals(LocalTime.of(9, 0), task.startTime)
        assertEquals(LocalDate.of(2026, 9, 30), task.dueDate)
        val fields = FieldRevs.decode(db.taskDao().get(id)!!.fieldRevs)
        assertNotNull(fields["start_date"], "the start has its own field clocks")
        assertNotNull(fields["start_time"])
    }

    @Test
    fun aStartTimeNeedsItsDate() = runTest {
        val id = tasks.create(NewTask("No date", startTime = LocalTime.of(9, 0), dueTime = LocalTime.of(10, 0)))

        val task = tasks.observeItem(id).first()!!.task
        assertNull(task.startTime)
        assertNull(task.dueTime)
    }

    @Test
    fun aStartMovedPastTheDueDragsTheDueAlongAndBothSync() = runTest {
        val id = tasks.create(NewTask("Plan", startDate = LocalDate.of(2026, 9, 28), dueDate = LocalDate.of(2026, 9, 30)))
        clock.advance(1_000)

        tasks.edit(id, listOf(TaskEdit.Start(LocalDate.of(2026, 10, 2), null)))

        val task = tasks.observeItem(id).first()!!.task
        assertEquals(LocalDate.of(2026, 10, 2), task.startDate)
        assertEquals(LocalDate.of(2026, 10, 2), task.dueDate)
        val changed = OutboxDao.decodeFields(outbox().single { it.entity == "tasks" }.changedFields)
        assertTrue("start_date" in changed && "due_date" in changed)
    }

    @Test
    fun aDueMovedBeforeTheStartDragsTheStartAlong() = runTest {
        val id = tasks.create(NewTask("Plan", startDate = LocalDate.of(2026, 9, 28), dueDate = LocalDate.of(2026, 9, 30)))

        tasks.edit(id, listOf(TaskEdit.Due(LocalDate.of(2026, 9, 26), null)))

        val task = tasks.observeItem(id).first()!!.task
        assertEquals(LocalDate.of(2026, 9, 26), task.startDate)
        assertEquals(LocalDate.of(2026, 9, 26), task.dueDate)
    }

    @Test
    fun movingBothEndsAtOnceIsOneEdit() = runTest {
        val id = tasks.create(NewTask("Plan", startDate = LocalDate.of(2026, 9, 28), dueDate = LocalDate.of(2026, 9, 29)))

        tasks.edit(id, listOf(TaskEdit.Start(LocalDate.of(2026, 10, 5), null), TaskEdit.Due(LocalDate.of(2026, 10, 6), null)))

        val task = tasks.observeItem(id).first()!!.task
        assertEquals(LocalDate.of(2026, 10, 5), task.startDate)
        assertEquals(LocalDate.of(2026, 10, 6), task.dueDate)
    }

    @Test
    fun aRepeatingTaskKeepsItsLengthWhenItRollsForward() = runTest {
        val id = tasks.create(
            NewTask(
                "Sprint", startDate = LocalDate.of(2026, 9, 23), dueDate = LocalDate.of(2026, 9, 26),
                repeat = RepeatRule(1, RepeatUnit.Week),
            ),
        )

        tasks.setStatus(id, TaskStatus.Done)

        val task = tasks.observeItem(id).first()!!.task
        assertEquals(LocalDate.of(2026, 10, 3), task.dueDate)
        assertEquals(LocalDate.of(2026, 9, 30), task.startDate, "the start moves by the same number of days")
    }

    @Test
    fun deleteIsATombstoneAndRestoreBringsBackTheSubtree() = runTest {
        val parent = tasks.create(NewTask("Trip"))
        val child = tasks.create(NewTask("Book train", parentId = parent))

        tasks.delete(parent)
        assertTrue(tasks.observeItems().first().isEmpty())
        assertEquals(listOf(parent), tasks.observeTrash().first().map { it.id }, "the subtask goes with its parent")
        assertEquals("delete", outbox().single { it.rowId == parent }.op)

        clock.advance(5_000)
        tasks.restore(parent)
        assertEquals(setOf(parent, child), tasks.observeItems().first().map { it.id }.toSet())
        assertEquals("upsert", outbox().single { it.rowId == parent }.op, "restore is an edit with a newer rev")
    }

    @Test
    fun purgingTheTrashKeepsTheTombstone() = runTest {
        val id = tasks.create(NewTask("Secret", notes = "private"))
        tasks.delete(id)
        tasks.purgeTrash()

        val row = db.taskDao().get(id)!!
        assertNotNull(row.sync.deletedAt)
        assertNotNull(row.purgedAt)
        assertEquals("", row.title)
        assertEquals("", row.notes)
        assertTrue(tasks.observeTrash().first().isEmpty())
    }

    @Test
    fun removingAndReAddingATagRevivesTheSameLinkRow() = runTest {
        val work = tags.create("Work", ColorToken.Palette.Blue)
        val id = tasks.create(NewTask("Report", tagIds = listOf(work)))
        val linkId = db.linkDao().taskTagsFor(id).single().id

        tasks.setTags(id, emptySet())
        assertNotNull(db.linkDao().taskTagsFor(id).single().sync.deletedAt)
        assertTrue(tasks.observeItem(id).first()!!.tags.isEmpty())

        tasks.setTags(id, setOf(work))
        val link = db.linkDao().taskTagsFor(id).single()
        assertEquals(linkId, link.id)
        assertNull(link.sync.deletedAt)
        assertEquals(listOf("Work"), tasks.observeItem(id).first()!!.tags.map { it.name })
        assertEquals(mapOf(work to 1), tags.observeUsage().first())
    }

    @Test
    fun dependenciesRefuseLoopsAndMarkTasksBlocked() = runTest {
        val a = tasks.create(NewTask("A"))
        val b = tasks.create(NewTask("B"))

        assertTrue(tasks.addDependency(a, b))
        assertFalse(tasks.addDependency(b, a))
        assertFalse(tasks.addDependency(a, a))
        assertTrue(tasks.observeItem(a).first()!!.isBlocked)

        tasks.setStatus(b, TaskStatus.Done)
        assertFalse(tasks.observeItem(a).first()!!.isBlocked, "a finished blocker no longer blocks")
    }

    @Test
    fun parentChainsCannotLoop() = runTest {
        val a = tasks.create(NewTask("A"))
        val b = tasks.create(NewTask("B", parentId = a))
        tasks.edit(a, listOf(TaskEdit.SetParent(b)))
        assertNull(db.taskDao().get(a)!!.parentId)
    }

    @Test
    fun manualOrderUsesFractionalKeys() = runTest {
        val first = tasks.create(NewTask("1"))
        val second = tasks.create(NewTask("2"))
        val third = tasks.create(NewTask("3"))

        tasks.move(third, afterId = first, beforeId = second)

        val order = tasks.observeItems().first().sortedBy { it.task.sortKey }.map { it.id }
        assertEquals(listOf(first, third, second), order)
        assertEquals(1, outbox().first { it.rowId == third }.let { OutboxDao.decodeFields(it.changedFields).count { f -> f == "sort_key" } })
    }

    @Test
    fun deletingAProjectMovesItsTasksToTheInbox() = runTest {
        val project = projects.create("Client", ColorToken.Palette.Green)
        val id = tasks.create(NewTask("Call", projectId = project))

        projects.delete(project)

        assertNull(tasks.observeItem(id).first()!!.task.projectId)
        assertTrue(db.historyDao().observeActivity(id).first().any { it.kind == ActivityKind.MovedToInbox.code })
    }

    @Test
    fun fullTextSearchFindsTitlesAndNotes() = runTest {
        val id = tasks.create(NewTask("Quarterly report", notes = "numbers for مشتری"))
        tasks.create(NewTask("Something else"))

        assertEquals(setOf(id), tasks.search("quart").first())
        assertEquals(setOf(id), tasks.search("مشتری").first())
        assertEquals(emptySet(), tasks.search("\"*").first())
    }

    @Test
    fun overdueOpenTasksBecomeNotDone() = runTest {
        val late = tasks.create(NewTask("Late", dueDate = LocalDate.of(2026, 9, 20)))
        val fine = tasks.create(NewTask("Fine", dueDate = LocalDate.of(2026, 9, 29)))
        assertEquals(1, tasks.markOverdueAsNotDone())
        assertEquals(TaskStatus.NotDone, tasks.observeItem(late).first()!!.task.status)
        assertEquals(TaskStatus.NotStarted, tasks.observeItem(fine).first()!!.task.status)
    }

    @Test
    fun localTimerStateNeverReachesTheOutbox() = runTest {
        val timers = DefaultTimerRepository(db, writer)
        val id = tasks.create(NewTask("Focus"))
        val before = outbox().size
        timers.setActive(io.github.alinourix.taski.core.domain.timer.FocusTimerState(id, 1500).start(clock.nowMillis()))
        assertEquals(before, outbox().size)
        timers.recordSession(id, clock.nowMillis(), clock.nowMillis() + 600_000, 1500, finished = false)
        assertEquals(1, outbox().count { it.entity == "timer_sessions" })
    }

    @Test
    fun signingInClaimsEveryLocalRow() = runTest {
        val id = tasks.create(NewTask("Mine", tagIds = listOf(tags.create("Home"))))
        db.outboxDao().remove(outbox().map { it.id })

        val claimed = LocalDataClaimer(db, hlc, clock, ids).claim("00000000-0000-7000-8000-000000000001")

        assertTrue(claimed >= 4)
        assertEquals("00000000-0000-7000-8000-000000000001", db.taskDao().get(id)!!.sync.userId)
        assertTrue(outbox().any { it.rowId == id && "user_id" in OutboxDao.decodeFields(it.changedFields) })
        assertEquals(claimed, outbox().size)
    }

    @Test
    fun importsAndExportsThePluginSyntax() = runTest {
        val transfer = DefaultTransferRepository(tasks, projects, tags)
        val note = """
            - [/] Draft the brief @priority(high) @due(2026-12-31) @repeat(2w) @tag(work)
              - [x] Outline
            - [ ] Call @tag(work, home)
        """.trimIndent()

        val result = transfer.importMarkdown("Weekly Tasks.md", note)

        assertEquals(3, result.tasks)
        assertEquals(2, result.tagsCreated)
        assertEquals("Weekly Tasks", projects.observeProjects().first().single().name)
        val exported = transfer.exportMarkdown()
        assertTrue("## Weekly Tasks" in exported)
        assertTrue("- [/] Draft the brief @priority(high) @due(2026-12-31) @repeat(2w) @tag(work)" in exported, exported)
        assertTrue("  - [x] Outline" in exported, exported)
        assertTrue("- [ ] Call @tag(work, home)" in exported, exported)
    }
}
