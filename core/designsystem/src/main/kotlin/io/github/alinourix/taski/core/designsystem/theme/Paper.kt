package io.github.alinourix.taski.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paper: the default look — calm, warm and tonal. Pages are an off-white
 * canvas; content sits in large rounded containers one tonal step above it, so
 * structure comes from surface, not from lines. Colour is scarce: ink and warm
 * grey for text, one blue for action and the hero, muted pastels only where a
 * value has a colour (tags, states). Selection is tonal; saturated fills are
 * kept for the action button and a single hero moment per screen.
 */
object Paper {
    val InkLight = Color(0xFF2B2A26)
    val InkDark = Color(0xFFE8E6E1)
    val MutedLight = Color(0xFF66645E)
    val MutedDark = Color(0xFFA5A29B)
    val Blue = Color(0xFF2F6FE0)
    val BlueDark = Color(0xFF8DB4FF)

    fun lightScheme(): ColorScheme = lightColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE8FB),
        onPrimaryContainer = Color(0xFF0F2E66),
        inversePrimary = BlueDark,
        // Selection and toggles are warm-grey tonal, never a saturated fill.
        secondary = Color(0xFF5B5951),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6E3DA),
        onSecondaryContainer = InkLight,
        tertiary = Color(0xFFB85F00),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFBE7D2),
        onTertiaryContainer = Color(0xFF5E3000),
        background = Color(0xFFFBFAF7),
        onBackground = InkLight,
        surface = Color(0xFFFBFAF7),
        onSurface = InkLight,
        surfaceVariant = Color(0xFFEDEAE2),
        onSurfaceVariant = MutedLight,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF302F2B),
        inverseOnSurface = Color(0xFFF3F1EB),
        error = Color(0xFFC93B36),
        onError = Color.White,
        errorContainer = Color(0xFFFCE4E2),
        onErrorContainer = Color(0xFF8A2421),
        outline = Color(0xFFB9B6AD),
        outlineVariant = Color(0xFFE3E0D7),
        scrim = Color(0xFF0F0F0F),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE9E6DE),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F2EC),
        surfaceContainer = Color(0xFFEFECE5),
        surfaceContainerHigh = Color(0xFFE9E6DE),
        surfaceContainerHighest = Color(0xFFE3E0D7),
    )

    fun darkScheme(): ColorScheme = darkColorScheme(
        primary = BlueDark,
        onPrimary = Color(0xFF0A2A5E),
        primaryContainer = Color(0xFF22406F),
        onPrimaryContainer = Color(0xFFD9E5FF),
        inversePrimary = Blue,
        secondary = Color(0xFFC9C6BE),
        onSecondary = Color(0xFF2B2A26),
        secondaryContainer = Color(0xFF3A3935),
        onSecondaryContainer = InkDark,
        tertiary = Color(0xFFE39B57),
        onTertiary = Color(0xFF3B1E00),
        tertiaryContainer = Color(0xFF5A3510),
        onTertiaryContainer = Color(0xFFFBDCBC),
        background = Color(0xFF131211),
        onBackground = InkDark,
        surface = Color(0xFF131211),
        onSurface = InkDark,
        surfaceVariant = Color(0xFF2A2926),
        onSurfaceVariant = MutedDark,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFE8E6E1),
        inverseOnSurface = Color(0xFF2B2A26),
        error = Color(0xFFFF8A82),
        onError = Color(0xFF3B0907),
        errorContainer = Color(0xFF5E2723),
        onErrorContainer = Color(0xFFFFD8D3),
        outline = Color(0xFF6A6861),
        outlineVariant = Color(0xFF34332F),
        scrim = Color.Black,
        surfaceBright = Color(0xFF373632),
        surfaceDim = Color(0xFF0E0D0C),
        surfaceContainerLowest = Color(0xFF0E0D0C),
        surfaceContainerLow = Color(0xFF1B1A18),
        surfaceContainer = Color(0xFF201F1C),
        surfaceContainerHigh = Color(0xFF2A2926),
        surfaceContainerHighest = Color(0xFF34332F),
    )

    /** Generous corners: containers and sheets are large, chips and tags stay small. */
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
}

/** Notion's select-option hues. [accent] tints icons and rings; the container pair is the tag itself. */
enum class Hue(
    private val light: AccentRoles,
    private val dark: AccentRoles,
) {
    Gray(roles(0xFF787774, 0xFFE3E2E0, 0xFF32302C), roles(0xFF9B9B9B, 0xFF5A5A5A, 0xFFEDEDED)),
    Brown(roles(0xFF9F6B53, 0xFFEEE0DA, 0xFF442A1E), roles(0xFFBA856F, 0xFF603B2C, 0xFFF1E3DC)),
    Orange(roles(0xFFD9730D, 0xFFFADEC9, 0xFF49290E), roles(0xFFC77D48, 0xFF854C1D, 0xFFFBE4D2)),
    Yellow(roles(0xFFCB912F, 0xFFFDECC8, 0xFF402C1B), roles(0xFFCA9849, 0xFF89632A, 0xFFFBEFD6)),
    Green(roles(0xFF448361, 0xFFDBEDDB, 0xFF1C3829), roles(0xFF529E72, 0xFF2B593F, 0xFFDDF0E2)),
    Teal(roles(0xFF2E8A8A, 0xFFD5EDEC, 0xFF153C3C), roles(0xFF4AA5A5, 0xFF1F5454, 0xFFD6F0EF)),
    Blue(roles(0xFF337EA9, 0xFFD3E5EF, 0xFF183347), roles(0xFF379AD3, 0xFF28456C, 0xFFDCE9F6)),
    Purple(roles(0xFF9065B0, 0xFFE8DEEE, 0xFF412454), roles(0xFF9D68D3, 0xFF492F64, 0xFFEBDFF6)),
    Pink(roles(0xFFC14C8A, 0xFFF5E0E9, 0xFF4C2337), roles(0xFFD15796, 0xFF69314C, 0xFFF6DDE9)),
    Red(roles(0xFFD44C47, 0xFFFFE2DD, 0xFF5D1715), roles(0xFFDF5452, 0xFF6E3630, 0xFFFBDDDA)),
    ;

    fun roles(dark: Boolean): AccentRoles = if (dark) this.dark else light
}

private fun roles(accent: Long, container: Long, onContainer: Long) =
    AccentRoles(Color(accent), Color.White, Color(container), Color(onContainer))
