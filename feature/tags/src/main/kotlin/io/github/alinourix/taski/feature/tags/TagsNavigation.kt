package io.github.alinourix.taski.feature.tags

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TagsRoute

fun NavController.navigateToTags() = navigate(TagsRoute) { launchSingleTop = true }

fun NavGraphBuilder.tagsScreen(onBack: () -> Unit) {
    composable<TagsRoute> { TagManagerScreen(onBack = onBack) }
}
