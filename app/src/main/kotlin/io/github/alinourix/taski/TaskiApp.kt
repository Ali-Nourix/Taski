package io.github.alinourix.taski

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.ui.LocalClock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.UiConfig
import io.github.alinourix.taski.core.ui.component.LocalSnackbarHostState
import io.github.alinourix.taski.feature.board.BoardRoute
import io.github.alinourix.taski.feature.board.BoardScreen
import io.github.alinourix.taski.feature.editor.QuickAddSheet
import io.github.alinourix.taski.feature.editor.navigateToTask
import io.github.alinourix.taski.feature.editor.taskEditorScreen
import io.github.alinourix.taski.feature.projects.ProjectsRoute
import io.github.alinourix.taski.feature.projects.ProjectsScreen
import io.github.alinourix.taski.feature.projects.navigateToProject
import io.github.alinourix.taski.feature.projects.projectScreen
import io.github.alinourix.taski.feature.settings.SettingsRoute
import io.github.alinourix.taski.feature.settings.SettingsScreen
import io.github.alinourix.taski.feature.settings.navigateToReminderSettings
import io.github.alinourix.taski.feature.settings.navigateToTrash
import io.github.alinourix.taski.feature.settings.settingsDetailScreens
import io.github.alinourix.taski.feature.tags.navigateToTags
import io.github.alinourix.taski.feature.tags.tagsScreen
import io.github.alinourix.taski.feature.timer.navigateToTimer
import io.github.alinourix.taski.feature.timer.timerScreen
import io.github.alinourix.taski.feature.today.TodayRoute
import io.github.alinourix.taski.feature.today.TodayScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private enum class Tab(val key: String, val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val selectedIcon: ImageVector, val label: Int) {
    Today("today", TodayRoute, TodayRoute::class, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny, R.string.nav_today),
    Board("board", BoardRoute, BoardRoute::class, Icons.AutoMirrored.Outlined.ViewList, Icons.AutoMirrored.Rounded.ViewList, R.string.nav_board),
    Projects("projects", ProjectsRoute, ProjectsRoute::class, Icons.Outlined.Folder, Icons.Rounded.Folder, R.string.nav_projects),
    Settings("settings", SettingsRoute, SettingsRoute::class, Icons.Outlined.Settings, Icons.Rounded.Settings, R.string.nav_settings),
}

@Composable
fun TaskiApp(
    prefs: UserPreferences,
    persian: Boolean,
    clock: Clock,
    request: ExternalRequest?,
    onRequestHandled: () -> Unit,
    onTabChanged: (String) -> Unit,
    onSendDigest: ((Int) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }
    val startTab = remember { Tab.entries.firstOrNull { it.key == prefs.lastTab } ?: Tab.Today }
    var quickAdd by rememberSaveable { mutableStateOf<String?>(null) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    // "Today" moves on at midnight without the app being reopened.
    val today by produceState(clock.today()) {
        while (true) {
            delay(60_000 - clock.nowMillis() % 60_000)
            value = clock.today()
        }
    }

    LaunchedEffect(currentTab) { currentTab?.let { onTabChanged(it.key) } }
    LaunchedEffect(request) {
        when (request) {
            is ExternalRequest.OpenTask -> navController.navigateToTask(request.id)
            ExternalRequest.OpenTimer -> navController.navigateToTimer()
            is ExternalRequest.NewTask -> quickAdd = request.title
            null -> return@LaunchedEffect
        }
        onRequestHandled()
    }

    fun openTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val digestSent = stringResource(R.string.digest_sent)
    val digestNothing = stringResource(R.string.digest_nothing)

    CompositionLocalProvider(
        LocalUiConfig provides UiConfig(prefs.calendar, persian, prefs.density, today, clock.zone()),
        LocalClock provides clock,
        LocalSnackbarHostState provides snackbar,
    ) {
        Row(Modifier.fillMaxSize()) {
            if (wide && currentTab != null) {
                WideNavigationRail(state = rememberWideNavigationRailState()) {
                    Tab.entries.forEach { tab ->
                        WideNavigationRailItem(
                            selected = tab == currentTab,
                            onClick = { openTab(tab) },
                            icon = { Icon(if (tab == currentTab) tab.selectedIcon else tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                            railExpanded = false,
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = if (currentTab != null) WindowInsets(0) else WindowInsets.navigationBars,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide && currentTab != null) {
                        ShortNavigationBar {
                            Tab.entries.forEach { tab ->
                                ShortNavigationBarItem(
                                    selected = tab == currentTab,
                                    onClick = { openTab(tab) },
                                    icon = { Icon(if (tab == currentTab) tab.selectedIcon else tab.icon, null) },
                                    label = { Text(stringResource(tab.label)) },
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    // The board docks its own add button into its floating toolbar.
                    AnimatedVisibility(visible = currentTab == Tab.Today || currentTab == Tab.Projects, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        CreateMenu(
                            expanded = fabOpen,
                            onExpandedChange = { fabOpen = it },
                            onNewTask = { fabOpen = false; quickAdd = "" },
                            onFocus = { fabOpen = false; navController.navigateToTimer() },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = startTab.route) {
                        composable<TodayRoute> {
                            TodayScreen(onOpenTask = navController::navigateToTask, onOpenTimer = navController::navigateToTimer, contentPadding = padding)
                        }
                        composable<BoardRoute> {
                            BoardScreen(
                                onOpenTask = navController::navigateToTask,
                                onManageTags = navController::navigateToTags,
                                onAddTask = { quickAdd = "" },
                                onOpenTimer = navController::navigateToTimer,
                                contentPadding = padding,
                            )
                        }
                        composable<ProjectsRoute> {
                            ProjectsScreen(onOpenProject = navController::navigateToProject, contentPadding = padding)
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                onOpenReminders = navController::navigateToReminderSettings,
                                onOpenTags = navController::navigateToTags,
                                onOpenTrash = navController::navigateToTrash,
                                contentPadding = padding,
                            )
                        }
                        taskEditorScreen(onBack = navController::popBackStack, onOpenTask = navController::navigateToTask, onOpenTimer = navController::navigateToTimer)
                        projectScreen(onBack = navController::popBackStack, onOpenTask = navController::navigateToTask, onOpenTimer = navController::navigateToTimer)
                        tagsScreen(onBack = navController::popBackStack)
                        timerScreen(onBack = navController::popBackStack, onOpenTask = navController::navigateToTask)
                        settingsDetailScreens(onBack = navController::popBackStack, onSendTestDigest = {
                            onSendDigest { count -> scope.launch { snackbar.showSnackbar(if (count > 0) digestSent else digestNothing) } }
                        })
                    }
                }
            }
        }

        quickAdd?.let { initial ->
            val projectId = backStack?.arguments?.getString("id")?.takeIf { destination?.hasRoute(io.github.alinourix.taski.feature.projects.ProjectRoute::class) == true }
            QuickAddSheet(
                onDismiss = { quickAdd = null },
                projectId = projectId,
                dueDate = today.takeIf { currentTab == Tab.Today },
                initialTitle = initial,
            )
        }
    }
}

/** The expressive FAB menu: the button morphs into a close button as the items fan out. */
@Composable
private fun CreateMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onNewTask: () -> Unit, onFocus: () -> Unit) {
    val menuLabel = stringResource(R.string.fab_menu)
    val closeLabel = stringResource(R.string.fab_close)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) closeLabel else menuLabel
                    stateDescription = if (expanded) closeLabel else menuLabel
                },
            ) {
                val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                Icon(icon, contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        FloatingActionButtonMenuItem(onClick = onFocus, text = { Text(stringResource(R.string.fab_focus)) }, icon = { Icon(Icons.Rounded.Timer, null) })
        FloatingActionButtonMenuItem(onClick = onNewTask, text = { Text(stringResource(R.string.fab_new_task)) }, icon = { Icon(Icons.Rounded.Add, null) })
    }
}
