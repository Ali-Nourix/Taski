package io.github.alinourix.taski.feature.timer

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TimerRoute

fun NavController.navigateToTimer() = navigate(TimerRoute) { launchSingleTop = true }

fun NavGraphBuilder.timerScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    composable<TimerRoute> { FocusTimerScreen(onBack = onBack, onOpenTask = onOpenTask) }
}
