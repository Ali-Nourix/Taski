package io.github.alinourix.taski

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.ViewLayout
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Touches the timeline the way a person does — a press and drag on a bar, a drag on its
 * end — and checks what was saved, so the gestures are proven and not only the maths behind them.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TimelineInteractionTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @get:Rule val compose = createEmptyComposeRule()

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var clock: Clock

    // xxhdpi is 3 pixels per dp; an hour of the day grid is 88dp.
    private val hourPx = 88 * 3f

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking { preferences.update { it.copy(materialYou = false, lastTab = "board", board = it.board.copy(layout = ViewLayout.Timeline)) } }
    }

    private fun settle() {
        repeat(3) {
            compose.waitForIdle()
            Thread.sleep(150)
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        }
        compose.waitForIdle()
    }

    private fun task(id: String): Task = runBlocking { tasks.observeItem(id).first()!!.task }

    @Test
    fun daysWithNothingFoldIntoOneRow() {
        val monday = clock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        runBlocking {
            tasks.create(NewTask("Early in the week", dueDate = monday.plusDays(1)))
            tasks.create(NewTask("Late in the week", dueDate = monday.plusDays(5)))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onNodeWithText("Early in the week").assertExists()
            compose.onNodeWithText("Late in the week").assertExists()
            val folded = compose.onAllNodes(hasText("free day", substring = true)).fetchSemanticsNodes()
            assertTrue("the quiet days between are one row, not one row each", folded.isNotEmpty())
        }
    }

    @Test
    fun theAgendaShowsTheFreeStretchBetweenTasks() {
        val today = clock.today()
        runBlocking {
            tasks.create(NewTask("Morning block", startDate = today, startTime = LocalTime.of(9, 0), dueDate = today, dueTime = LocalTime.of(10, 0)))
            tasks.create(NewTask("Afternoon block", startDate = today, startTime = LocalTime.of(13, 0), dueDate = today, dueTime = LocalTime.of(14, 0)))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onAllNodes(hasText("Day") and hasClickAction())[0].performClick()
            settle()
            compose.onNodeWithText("Morning block").assertExists()
            compose.onNodeWithText("Afternoon block").assertExists()
            // The stretch between them is offered back, as a length of time.
            compose.onNodeWithText("3h free").assertExists()
        }
    }

    @Test
    fun theEndsOfARangeOpenTheirPickers() {
        val today = clock.today()
        runBlocking { tasks.create(NewTask("Over three days", startDate = today, dueDate = today.plusDays(2))) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onNodeWithTag("range-end").performClick()
            settle()
            compose.onNodeWithText("Due date").assertExists()
        }
    }

    @Test
    fun theStartOfARangeOpensItsPicker() {
        val today = clock.today()
        runBlocking { tasks.create(NewTask("Over three days", startDate = today, dueDate = today.plusDays(2))) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onNodeWithTag("range-start").performClick()
            settle()
            compose.onNodeWithText("Start date").assertExists()
        }
    }

    @Test
    fun pressingAndDraggingABlockMovesItByTheHour() {
        val today = clock.today()
        val id = runBlocking { tasks.create(NewTask("Solo", startDate = today, startTime = LocalTime.of(9, 0), dueDate = today, dueTime = LocalTime.of(10, 0))) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onAllNodes(hasText("Day") and hasClickAction())[0].performClick()
            settle()
            compose.onNode(hasContentDescription("Hour grid")).performClick()
            settle()
            compose.onNodeWithText("Solo").performTouchInput {
                down(center)
                advanceEventTime(800)
                moveBy(Offset(0f, hourPx + 10f))
                up()
            }
            settle()
            assertEquals(LocalTime.of(10, 0), task(id).startTime)
            assertEquals(LocalTime.of(11, 0), task(id).dueTime)
            assertEquals(today, task(id).startDate)
        }
    }

    @Test
    fun draggingTheBottomOfABlockChangesWhenItEnds() {
        val today = clock.today()
        val id = runBlocking { tasks.create(NewTask("Solo", startDate = today, startTime = LocalTime.of(9, 0), dueDate = today, dueTime = LocalTime.of(10, 0))) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onAllNodes(hasText("Day") and hasClickAction())[0].performClick()
            settle()
            compose.onNode(hasContentDescription("Hour grid")).performClick()
            settle()
            compose.onNode(hasContentDescription("Change end")).performTouchInput {
                down(center)
                // The drag starts counting once it passes the touch slop, so add the slop to move exactly an hour.
                moveBy(Offset(0f, hourPx + viewConfiguration.touchSlop))
                up()
            }
            settle()
            assertEquals("the start stays", LocalTime.of(9, 0), task(id).startTime)
            assertEquals(LocalTime.of(11, 0), task(id).dueTime)
        }
    }
}
