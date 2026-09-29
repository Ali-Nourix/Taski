package io.github.alinourix.taski.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object ReminderSettingsRoute

@Serializable
data object TrashRoute

fun NavController.navigateToReminderSettings() = navigate(ReminderSettingsRoute) { launchSingleTop = true }
fun NavController.navigateToTrash() = navigate(TrashRoute) { launchSingleTop = true }

fun NavGraphBuilder.settingsDetailScreens(onBack: () -> Unit, onSendTestDigest: () -> Unit) {
    composable<ReminderSettingsRoute> { ReminderSettingsScreen(onBack = onBack, onSendTest = onSendTestDigest) }
    composable<TrashRoute> { TrashScreen(onBack = onBack) }
}
