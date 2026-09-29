package io.github.alinourix.taski

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.alinourix.taski.core.domain.ThemeMode
import io.github.alinourix.taski.core.domain.model.ViewLayout
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * Drives the real app — Hilt graph, Room, DataStore, navigation — on the JVM
 * and records what each screen looks like. Run `./gradlew :app:recordRoborazziDebug`
 * to refresh docs/screenshots.
 */
@OptIn(ExperimentalRoborazziApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AppScreenshotTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @get:Rule val compose = createEmptyComposeRule()

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var tags: TagRepository
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var timer: FocusTimerController
    @Inject lateinit var clock: Clock

    private lateinit var ids: Map<String, String>

    @Before
    fun setUp() {
        hilt.inject()
    }

    private fun seed(persian: Boolean = false, theme: ThemeMode = ThemeMode.Light) = runBlocking {
        ids = Sample.seed(tasks, tags, projects, preferences, persian, clock.today())
        preferences.update { it.copy(themeMode = theme) }
    }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java).also { settle() }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(400)
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction())[0].performClick()
        settle()
    }

    private fun openCreateMenu() {
        compose.onAllNodes(hasClickAction() and androidx.compose.ui.test.hasContentDescription("Create"))[0].performClick()
        settle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("${SHOTS}/$name.png")

    @Test
    fun englishLight() {
        seed()
        launch().use {
            shoot("today")
            tap("Board")
            shoot("board-list")
            tap("Projects")
            shoot("projects")
            tap("Settings")
            shoot("settings")
            tap("Today")
            tap("Draft the launch brief")
            shoot("editor")
        }
    }

    @Test
    fun englishDarkBoardAndTimer() {
        seed(theme = ThemeMode.Dark)
        runBlocking {
            preferences.update { it.copy(board = it.board.copy(layout = ViewLayout.Board), lastTab = "board") }
            timer.start(ids.getValue("brief"), 25)
        }
        launch().use {
            shoot("board-kanban-dark")
            tap("Today")
            shoot("today-dark")
            openCreateMenu()
            tap("Focus timer")
            shoot("timer-dark")
        }
    }

    @Test
    fun quickAddSheet() {
        seed()
        launch().use {
            openCreateMenu()
            captureScreenRoboImage("${SHOTS}/fab-menu.png")
            tap("New task")
            captureScreenRoboImage("${SHOTS}/quick-add.png")
        }
    }

    @Test
    @Config(qualifiers = "fa-w412dp-h915dp-xxhdpi")
    fun persianJalali() {
        seed(persian = true)
        launch().use {
            shoot("today-fa")
            tap("بورد")
            shoot("board-fa")
            tap("امروز")
            tap("نوشتن پیش‌نویس معرفی محصول")
            shoot("editor-fa")
        }
    }

    private companion object {
        const val SHOTS = "../docs/screenshots"
    }
}
