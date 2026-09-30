package io.github.alinourix.taski

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.ViewLayout
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The density budget from the design skill, held as tests: a screen must show its content near the top
 * and not stack controls in front of it. Measured on a 412 x 915 dp phone; the numbers are ceilings, not targets.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class DensityBudgetTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @get:Rule val compose = createEmptyComposeRule()

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var clock: Clock

    @Before
    fun setUp() {
        hilt.inject()
    }

    private fun settle() {
        repeat(3) {
            compose.waitForIdle()
            Thread.sleep(150)
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        }
        compose.waitForIdle()
    }

    private fun show(layout: ViewLayout) = runBlocking {
        preferences.update { it.copy(materialYou = false, lastTab = "board", board = it.board.copy(layout = layout)) }
    }

    /** Where the content starts, as a share of the screen's height, and how many controls sit above it. */
    private fun measure(text: String): Pair<Float, Int> {
        val screen = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        val top = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        val above = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().count { it.boundsInRoot.bottom <= top }
        return top / screen to above
    }

    @Test
    fun theListShowsItsFirstTaskInTheTopQuarterBehindFewControls() {
        show(ViewLayout.List)
        runBlocking { tasks.create(NewTask("Budget probe", dueDate = clock.today())) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            val (share, controls) = measure("Budget probe")
            assertTrue("content starts at ${(share * 100).toInt()}% of the screen; the budget is 25%", share <= 0.25f)
            assertTrue("$controls controls before the content; the budget is 6", controls <= 6)
        }
    }

    @Test
    fun theWeekPlanShowsItsFirstCardInTheTopThird() {
        show(ViewLayout.Timeline)
        runBlocking { tasks.create(NewTask("Budget probe", dueDate = clock.today())) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            val (share, _) = measure("Budget probe")
            assertTrue("content starts at ${(share * 100).toInt()}% of the screen; the budget is 38%", share <= 0.38f)
        }
    }

    @Test
    fun theAgendaShowsItsFirstTaskBeforeTheMiddleOfTheScreen() {
        show(ViewLayout.Timeline)
        val today = clock.today()
        runBlocking { tasks.create(NewTask("Budget probe", startDate = today, startTime = LocalTime.of(9, 0), dueDate = today, dueTime = LocalTime.of(10, 0))) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onAllNodes(androidx.compose.ui.test.hasText("Week") and hasClickAction())[0].performClick()
            settle()
            val items = compose.onAllNodes(androidx.compose.ui.test.hasText("Day") and hasClickAction())
            items[items.fetchSemanticsNodes().size - 1].performClick()
            settle()
            val (share, _) = measure("Budget probe")
            assertTrue("content starts at ${(share * 100).toInt()}% of the screen; the budget is 45%", share <= 0.45f)
        }
    }

    @Test
    fun todayNamesItsNextTaskOnceNotTwice() {
        runBlocking { preferences.update { it.copy(materialYou = false, lastTab = "today") } }
        runBlocking { tasks.create(NewTask("Budget probe", dueDate = clock.today().minusDays(1))) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onAllNodesWithText("Budget probe").assertCountEquals(1)
        }
    }

    @Test
    fun theTaskPageFoldsItsEmptyRareProperties() {
        runBlocking { preferences.update { it.copy(materialYou = false, lastTab = "today") } }
        runBlocking { tasks.create(NewTask("Budget probe", dueDate = clock.today())) }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            compose.onNodeWithText("Budget probe").performClick()
            settle()
            compose.onAllNodesWithText("Repeat").assertCountEquals(0)
            compose.onAllNodesWithText("Reminder").assertCountEquals(0)
            compose.onNodeWithText("More").performClick()
            settle()
            compose.onAllNodesWithText("Repeat").assertCountEquals(1)
            compose.onAllNodesWithText("Reminder").assertCountEquals(1)
        }
    }
}
