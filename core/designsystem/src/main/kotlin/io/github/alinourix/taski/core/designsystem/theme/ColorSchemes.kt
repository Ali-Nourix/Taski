@file:SuppressLint("RestrictedApi")

package io.github.alinourix.taski.core.designsystem.theme

import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.Hct

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
