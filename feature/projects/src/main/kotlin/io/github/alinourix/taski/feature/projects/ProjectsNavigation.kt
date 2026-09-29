package io.github.alinourix.taski.feature.projects

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProjectsRoute

/** A project's tasks; a null [id] is the Inbox. */
@Serializable
data class ProjectRoute(val id: String? = null)

fun NavController.navigateToProject(id: String?) = navigate(ProjectRoute(id)) { launchSingleTop = true }

fun NavGraphBuilder.projectScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    composable<ProjectRoute> { ProjectScreen(onBack = onBack, onOpenTask = onOpenTask) }
}
