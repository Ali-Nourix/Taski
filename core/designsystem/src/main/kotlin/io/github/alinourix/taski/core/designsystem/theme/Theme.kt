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
data class TaskiThemeState(val isDark: Boolean)

private val LocalTaskiTheme = staticCompositionLocalOf { TaskiThemeState(isDark = false) }

object TaskiTheme {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalTaskiTheme.current.isDark
}

/**
 * Material 3 Expressive: the expressive motion scheme (spring-based, with a
 * little overshoot), wallpaper colours on Android 12+ or a scheme generated
 * from the brand seed, and Vazirmatn when [persian] is set.
 */
@Composable
fun TaskiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    persian: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(darkTheme) { seededColorScheme(BRAND_SEED, darkTheme) }
    }
    val typography = remember(persian) { taskiTypography(if (persian) Vazirmatn else null) }

    CompositionLocalProvider(LocalTaskiTheme provides TaskiThemeState(darkTheme)) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            content = content,
        )
    }
}
