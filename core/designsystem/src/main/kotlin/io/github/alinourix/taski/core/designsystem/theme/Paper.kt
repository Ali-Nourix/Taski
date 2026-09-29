package io.github.alinourix.taski.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paper: the default look. A document-like canvas where colour is scarce and
 * means something — ink and grey for text, one blue for action and focus,
 * hairlines for structure, and muted pastels only on tags and states.
 * The values are Notion's, so a task board reads the way a Notion database does.
 */
object Paper {
    val InkLight = Color(0xFF37352F)
    val InkDark = Color(0xFFD4D4D4)
    val MutedLight = Color(0xFF787774)
    val MutedDark = Color(0xFF9B9B9B)
    val HairlineLight = Color(0xFFE9E9E7)
    val HairlineDark = Color(0xFF2F2F2F)
    val Blue = Color(0xFF2383E2)
    val BlueDark = Color(0xFF529CCA)

    fun lightScheme(): ColorScheme = lightColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7F3F8),
        onPrimaryContainer = Color(0xFF1F5B85),
        inversePrimary = BlueDark,
        // Selection and toggles stay ink-on-grey, like Notion's selected rows.
        secondary = InkLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDEDEB),
        onSecondaryContainer = InkLight,
        tertiary = Color(0xFFD9730D),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFBECDD),
        onTertiaryContainer = Color(0xFF6B3A09),
        background = Color.White,
        onBackground = InkLight,
        surface = Color.White,
        onSurface = InkLight,
        surfaceVariant = Color(0xFFF1F1EF),
        onSurfaceVariant = MutedLight,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF2F2F2F),
        inverseOnSurface = Color(0xFFF1F1EF),
        error = Color(0xFFD44C47),
        onError = Color.White,
        errorContainer = Color(0xFFFDEBEC),
        onErrorContainer = Color(0xFF9F2F2D),
        outline = Color(0xFFC4C3BF),
        outlineVariant = HairlineLight,
        scrim = Color(0xFF0F0F0F),
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFEEEEEC),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFBFBFA),
        surfaceContainer = Color(0xFFF7F6F3),
        surfaceContainerHigh = Color(0xFFF1F1EF),
        surfaceContainerHighest = Color(0xFFEBEBEA),
    )

    fun darkScheme(): ColorScheme = darkColorScheme(
        primary = BlueDark,
        onPrimary = Color(0xFF0B2233),
        primaryContainer = Color(0xFF1F3A4D),
        onPrimaryContainer = Color(0xFFBFDDF1),
        inversePrimary = Blue,
        secondary = InkDark,
        onSecondary = Color(0xFF191919),
        secondaryContainer = Color(0xFF333333),
        onSecondaryContainer = InkDark,
        tertiary = Color(0xFFC77D48),
        onTertiary = Color(0xFF2A1504),
        tertiaryContainer = Color(0xFF4A2F1A),
        onTertiaryContainer = Color(0xFFF3D2B5),
        background = Color(0xFF191919),
        onBackground = InkDark,
        surface = Color(0xFF191919),
        onSurface = InkDark,
        surfaceVariant = Color(0xFF252525),
        onSurfaceVariant = MutedDark,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFE6E6E4),
        inverseOnSurface = Color(0xFF252525),
        error = Color(0xFFDF5452),
        onError = Color(0xFF2B0606),
        errorContainer = Color(0xFF522E2A),
        onErrorContainer = Color(0xFFFFD1CD),
        outline = Color(0xFF5A5A5A),
        outlineVariant = HairlineDark,
        scrim = Color.Black,
        surfaceBright = Color(0xFF2F2F2F),
        surfaceDim = Color(0xFF141414),
        surfaceContainerLowest = Color(0xFF141414),
        surfaceContainerLow = Color(0xFF1C1C1C),
        surfaceContainer = Color(0xFF202020),
        surfaceContainerHigh = Color(0xFF252525),
        surfaceContainerHighest = Color(0xFF2C2C2C),
    )

    /** Crisp corners: small on chips and rows, softer only on sheets. */
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(16.dp),
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
