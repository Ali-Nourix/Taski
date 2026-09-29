package io.github.alinourix.taski.core.domain

import io.github.alinourix.taski.core.domain.model.Coded
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.model.codeOf
import io.github.alinourix.taski.core.domain.reminder.DigestSettings
import io.github.alinourix.taski.core.domain.timer.FocusTimerState

enum class AppLanguage(override val code: String) : Coded {
    System("system"), English("en"), Persian("fa");

    companion object {
        fun fromCode(code: String?) = codeOf<AppLanguage>(code) ?: System
    }
}

enum class CalendarSystem(override val code: String) : Coded {
    Gregorian("gregorian"), Jalali("jalali");

    companion object {
        fun fromCode(code: String?) = codeOf<CalendarSystem>(code) ?: Gregorian
    }
}

enum class ThemeMode(override val code: String) : Coded {
    System("system"), Light("light"), Dark("dark");

    companion object {
        fun fromCode(code: String?) = codeOf<ThemeMode>(code) ?: System
    }
}

enum class Density(override val code: String) : Coded {
    Comfortable("comfortable"), Compact("compact");

    companion object {
        fun fromCode(code: String?) = codeOf<Density>(code) ?: Comfortable
    }
}

/**
 * Settings that belong to this device and are never synced: how the app
 * looks and behaves here, including when this phone should nag.
 */
data class UserPreferences(
    val language: AppLanguage = AppLanguage.System,
    val calendar: CalendarSystem = CalendarSystem.Gregorian,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Wallpaper colours (Android 12+) instead of the Paper look. */
    val materialYou: Boolean = false,
    val density: Density = Density.Comfortable,
    val lastTab: String = "today",
    val board: ViewDefinition = ViewDefinition(),
    val collapsedGroups: Set<String> = emptySet(),
    val defaultTimerMinutes: Int = FocusTimerState.DEFAULT_MINUTES,
    val timerSound: Boolean = true,
    /** The plugin moves a task whose deadline passed into "Not done". */
    val markOverdueNotDone: Boolean = true,
    val digest: DigestSettings = DigestSettings(),
    val onboardingDone: Boolean = false,
)
