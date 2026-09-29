package io.github.alinourix.taski.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.designsystem.theme.TaskiTheme
import io.github.alinourix.taski.core.designsystem.theme.accentRoles
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus

/** The plugin's palette hues. Each is harmonised toward the theme before use. */
fun ColorToken.baseColor(): Color = when (this) {
    ColorToken.Palette.Red -> Color(0xFFE5484D)
    ColorToken.Palette.Orange -> Color(0xFFE8762D)
    ColorToken.Palette.Yellow -> Color(0xFFC9A400)
    ColorToken.Palette.Green -> Color(0xFF3AA675)
    ColorToken.Palette.Cyan -> Color(0xFF1E8A9C)
    ColorToken.Palette.Blue -> Color(0xFF2F6FBD)
    ColorToken.Palette.Purple -> Color(0xFF7048B6)
    ColorToken.Palette.Pink -> Color(0xFFC2407F)
    ColorToken.Palette.Gray -> Color(0xFF75726D)
    is ColorToken.Custom -> Color(0xFF000000.toInt() or rgb)
}

/** Container and label colours for a tag or project colour in the current theme. */
@Composable
fun ColorToken.roles(): AccentRoles {
    val primary = MaterialTheme.colorScheme.primary
    val dark = TaskiTheme.isDark
    // A custom colour is the user's exact choice; palette hues bend toward the theme.
    val harmonize = this !is ColorToken.Custom
    return remember(this, primary, dark) { accentRoles(baseColor(), primary, dark, harmonize) }
}

@Composable
fun Priority.roles(): AccentRoles = when (this) {
    Priority.Highest -> ColorToken.Palette.Red
    Priority.High -> ColorToken.Palette.Orange
    Priority.Medium -> ColorToken.Palette.Yellow
    Priority.Low -> ColorToken.Palette.Blue
    Priority.Lowest -> ColorToken.Palette.Gray
}.roles()

@Composable
fun TaskStatus.roles(): AccentRoles {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        TaskStatus.NotStarted -> AccentRoles(scheme.outline, scheme.surface, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        TaskStatus.InProgress -> AccentRoles(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
        TaskStatus.Done -> ColorToken.Palette.Green.roles()
        TaskStatus.NotDone -> AccentRoles(scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer)
    }
}
