package io.github.alinourix.taski.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.alinourix.taski.core.designsystem.R

/** Vazirmatn, a variable font with Persian and Latin glyphs, used whenever the app speaks Persian. */
@OptIn(ExperimentalTextApi::class)
val Vazirmatn: FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            resId = R.font.vazirmatn,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

/** The Material 3 type scale, including the emphasized styles, in [family]. */
fun taskiTypography(family: FontFamily?): Typography {
    val base = Typography()
    if (family == null) return base
    fun TextStyle.withFamily() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily(),
        displayLargeEmphasized = base.displayLargeEmphasized.withFamily(),
        displayMediumEmphasized = base.displayMediumEmphasized.withFamily(),
        displaySmallEmphasized = base.displaySmallEmphasized.withFamily(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.withFamily(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.withFamily(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.withFamily(),
        titleLargeEmphasized = base.titleLargeEmphasized.withFamily(),
        titleMediumEmphasized = base.titleMediumEmphasized.withFamily(),
        titleSmallEmphasized = base.titleSmallEmphasized.withFamily(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.withFamily(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.withFamily(),
        bodySmallEmphasized = base.bodySmallEmphasized.withFamily(),
        labelLargeEmphasized = base.labelLargeEmphasized.withFamily(),
        labelMediumEmphasized = base.labelMediumEmphasized.withFamily(),
        labelSmallEmphasized = base.labelSmallEmphasized.withFamily(),
    )
}
