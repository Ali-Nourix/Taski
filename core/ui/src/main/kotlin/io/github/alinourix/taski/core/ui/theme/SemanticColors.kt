package io.github.alinourix.taski.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.alinourix.taski.core.designsystem.theme.AccentRoles
import io.github.alinourix.taski.core.designsystem.theme.Hue
import io.github.alinourix.taski.core.designsystem.theme.TaskiTheme
import io.github.alinourix.taski.core.designsystem.theme.accentRoles
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus

/** The plugin's palette names, as Notion's hues. */
fun ColorToken.Palette.hue(): Hue = when (this) {
    ColorToken.Palette.Red -> Hue.Red
    ColorToken.Palette.Orange -> Hue.Orange
    ColorToken.Palette.Yellow -> Hue.Yellow
    ColorToken.Palette.Green -> Hue.Green
    ColorToken.Palette.Cyan -> Hue.Teal
    ColorToken.Palette.Blue -> Hue.Blue
    ColorToken.Palette.Purple -> Hue.Purple
    ColorToken.Palette.Pink -> Hue.Pink
    ColorToken.Palette.Gray -> Hue.Gray
}

/** The raw colour behind a token, for Material You harmonisation and custom colours. */
fun ColorToken.baseColor(): Color = when (this) {
    is ColorToken.Palette -> hue().roles(dark = false).accent
    is ColorToken.Custom -> Color(0xFF000000.toInt() or rgb)
}

/**
 * Container and label colours for a tag or project colour. On Paper, palette
 * hues are Notion's muted pastels exactly; with wallpaper colours they are
 * harmonised toward the theme. A custom colour is always the user's own choice,
 * toned for light or dark.
 */
@Composable
fun ColorToken.roles(): AccentRoles {
    val dark = TaskiTheme.isDark
    if (this is ColorToken.Palette && TaskiTheme.isPaper) return hue().roles(dark)
    val primary = MaterialTheme.colorScheme.primary
    val harmonize = this !is ColorToken.Custom
    return remember(this, primary, dark) { accentRoles(baseColor(), primary, dark, harmonize) }
}

private fun Priority.hue(): Hue = when (this) {
    Priority.Highest -> Hue.Red
    Priority.High -> Hue.Orange
    Priority.Medium -> Hue.Yellow
    Priority.Low -> Hue.Blue
    Priority.Lowest -> Hue.Gray
}

@Composable
fun Priority.roles(): AccentRoles = when (this) {
    Priority.Highest -> ColorToken.Palette.Red
    Priority.High -> ColorToken.Palette.Orange
    Priority.Medium -> ColorToken.Palette.Yellow
    Priority.Low -> ColorToken.Palette.Blue
    Priority.Lowest -> ColorToken.Palette.Gray
}.roles().takeUnless { TaskiTheme.isPaper } ?: hue().roles(TaskiTheme.isDark)

/** Notion's status colours: grey not started, blue in progress, green done; red for not done. */
@Composable
fun TaskStatus.roles(): AccentRoles = when (this) {
    TaskStatus.NotStarted -> Hue.Gray
    TaskStatus.InProgress -> Hue.Blue
    TaskStatus.Done -> Hue.Green
    TaskStatus.NotDone -> Hue.Red
}.let { hue ->
    if (TaskiTheme.isPaper) hue.roles(TaskiTheme.isDark) else when (this) {
        TaskStatus.NotStarted -> MaterialTheme.colorScheme.let { AccentRoles(it.outline, it.surface, it.surfaceContainerHighest, it.onSurfaceVariant) }
        TaskStatus.InProgress -> MaterialTheme.colorScheme.let { AccentRoles(it.primary, it.onPrimary, it.primaryContainer, it.onPrimaryContainer) }
        TaskStatus.Done -> ColorToken.Palette.Green.roles()
        TaskStatus.NotDone -> MaterialTheme.colorScheme.let { AccentRoles(it.error, it.onError, it.errorContainer, it.onErrorContainer) }
    }
}
