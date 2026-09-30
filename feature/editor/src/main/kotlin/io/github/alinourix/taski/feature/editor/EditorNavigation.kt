package io.github.alinourix.taski.feature.editor

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class TaskEditorRoute(val id: String)

fun NavController.navigateToTask(id: String) = navigate(TaskEditorRoute(id)) { launchSingleTop = true }

fun NavGraphBuilder.taskEditorScreen(
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenTimer: () -> Unit,
) {
    composable<TaskEditorRoute> {
        TaskEditorScreen(onBack = onBack, onOpenTask = onOpenTask, onOpenTimer = onOpenTimer)
    }
}
