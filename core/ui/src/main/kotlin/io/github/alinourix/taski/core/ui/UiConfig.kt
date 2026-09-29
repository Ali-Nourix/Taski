package io.github.alinourix.taski.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.Density
import java.time.LocalDate
import java.time.ZoneId

/** How dates and lists are presented on this device, provided once at the root. */
@Immutable
data class UiConfig(
    val calendar: CalendarSystem = CalendarSystem.Gregorian,
    /** True when the app is showing Persian: Persian digits and month names. */
    val persian: Boolean = false,
    val density: Density = Density.Comfortable,
    val today: LocalDate = io.github.alinourix.taski.core.domain.time.SystemClock().today(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val compact: Boolean get() = density == Density.Compact
}

val LocalUiConfig = staticCompositionLocalOf { UiConfig() }

/** The injected clock, for composables that tick. Provided at the root from the same [Clock] the data layer uses. */
val LocalClock = staticCompositionLocalOf<io.github.alinourix.taski.core.domain.time.Clock> {
    io.github.alinourix.taski.core.domain.time.SystemClock()
}
