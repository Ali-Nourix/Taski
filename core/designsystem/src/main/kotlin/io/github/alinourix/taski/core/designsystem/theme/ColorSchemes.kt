@file:SuppressLint("RestrictedApi")

package io.github.alinourix.taski.core.designsystem.theme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeTonalSpot

/** The brand seed used when the device offers no wallpaper colours. */
const val BRAND_SEED: Int = 0xFF4F5BD5.toInt()

/**
 * A full Material 3 scheme generated from one seed with Material Color
 * Utilities, so every role (containers, surfaces, fixed colours) has the
 * tone the spec asks for rather than a hand-picked guess.
 */
fun seededColorScheme(seed: Int, dark: Boolean, expressive: Boolean = true): ColorScheme {
    val hct = Hct.fromInt(seed)
    val scheme: DynamicScheme = if (expressive) SchemeExpressive(hct, dark, 0.0) else SchemeTonalSpot(hct, dark, 0.0)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        inversePrimary = Color(scheme.inversePrimary),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        surfaceTint = Color(scheme.primary),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
        surfaceBright = Color(scheme.surfaceBright),
        surfaceDim = Color(scheme.surfaceDim),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
    )
}

/** The four roles a coloured element needs, like the Material "custom colour" group. */
data class AccentRoles(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Colour roles for a tag or project colour: harmonised toward the current
 * primary so a "blue" tag belongs to the theme the way the plugin's tags
 * belong to the Obsidian theme, then toned for light or dark so the label
 * always reads (tones 40/100/90/10 in light, 80/20/30/90 in dark).
 */
fun accentRoles(color: Color, primary: Color, dark: Boolean, harmonize: Boolean = true): AccentRoles {
    val source = if (harmonize) Blend.harmonize(color.toArgb(), primary.toArgb()) else color.toArgb()
    val hct = Hct.fromInt(source)
    fun tone(t: Double) = Color(Hct.from(hct.hue, hct.chroma.coerceAtLeast(if (t in 20.0..90.0) 16.0 else 0.0), t).toInt())
    return if (dark) {
        AccentRoles(accent = tone(80.0), onAccent = tone(20.0), container = tone(30.0), onContainer = tone(90.0))
    } else {
        AccentRoles(accent = tone(40.0), onAccent = tone(100.0), container = tone(90.0), onContainer = tone(10.0))
    }
}
