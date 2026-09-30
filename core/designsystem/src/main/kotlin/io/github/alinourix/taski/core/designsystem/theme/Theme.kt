package io.github.alinourix.taski.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

@Immutable
data class TaskiThemeState(val isDark: Boolean, val paper: Boolean)

private val LocalTaskiTheme = staticCompositionLocalOf { TaskiThemeState(isDark = false, paper = true) }

object TaskiTheme {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalTaskiTheme.current.isDark

    /** True for the Paper look; false when colours come from the wallpaper. */
    val isPaper: Boolean
        @Composable @ReadOnlyComposable get() = LocalTaskiTheme.current.paper
}

/**
 * Material 3 Expressive underneath — its motion scheme (springs with a little
 * overshoot), components and shape morphs — dressed by default in [Paper]:
 * a calm document-like palette. With [materialYou] on Android 12+, the
 * wallpaper's colours are used instead. Vazirmatn when [persian] is set.
 */
@Composable
fun TaskiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    materialYou: Boolean = false,
    persian: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val wallpaper = materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        wallpaper -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> remember { Paper.darkScheme() }
        else -> remember { Paper.lightScheme() }
    }
    val typography = remember(persian) { taskiTypography(if (persian) Vazirmatn else null) }

    CompositionLocalProvider(LocalTaskiTheme provides TaskiThemeState(darkTheme, paper = !wallpaper)) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            shapes = if (wallpaper) null else Paper.shapes,
            content = content,
        )
    }
}
