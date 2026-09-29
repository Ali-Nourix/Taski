package io.github.alinourix.taski.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.alinourix.taski.core.domain.AppLanguage
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.Density
import io.github.alinourix.taski.core.domain.ThemeMode
import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.model.ViewDefinition
import io.github.alinourix.taski.core.domain.reminder.AgeFilterMode
import io.github.alinourix.taski.core.domain.reminder.DigestSettings
import io.github.alinourix.taski.core.domain.reminder.Strictness
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.time.DateCodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Device preferences. Enums are kept by their stable codes, times as `HH:MM`. */
@Singleton
class DataStorePreferencesRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = store.data.map(::read).distinctUntilChanged()

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): UserPreferences {
        val defaults = UserPreferences()
        val d = defaults.digest
        return UserPreferences(
            language = AppLanguage.fromCode(p[Keys.language]),
            calendar = CalendarSystem.fromCode(p[Keys.calendar]),
            themeMode = ThemeMode.fromCode(p[Keys.theme]),
            dynamicColor = p[Keys.dynamicColor] ?: defaults.dynamicColor,
            density = Density.fromCode(p[Keys.density]),
            lastTab = p[Keys.lastTab] ?: defaults.lastTab,
            board = ViewDefinition.decode(p[Keys.board]) ?: defaults.board,
            collapsedGroups = p[Keys.collapsed] ?: emptySet(),
            defaultTimerMinutes = p[Keys.timerMinutes] ?: defaults.defaultTimerMinutes,
            timerSound = p[Keys.timerSound] ?: defaults.timerSound,
            markOverdueNotDone = p[Keys.markOverdue] ?: defaults.markOverdueNotDone,
            onboardingDone = p[Keys.onboarding] ?: false,
            digest = DigestSettings(
                enabled = p[Keys.digestEnabled] ?: d.enabled,
                times = p[Keys.digestTimes]?.split(',')?.mapNotNull(DateCodes::parseTime)?.sorted() ?: d.times,
                repeatEnabled = p[Keys.repeatEnabled] ?: d.repeatEnabled,
                repeatIntervalMinutes = p[Keys.repeatMinutes] ?: d.repeatIntervalMinutes,
                quietHoursEnabled = p[Keys.quietEnabled] ?: d.quietHoursEnabled,
                quietStart = DateCodes.parseTime(p[Keys.quietStart]) ?: d.quietStart,
                quietEnd = DateCodes.parseTime(p[Keys.quietEnd]) ?: d.quietEnd,
                strictness = p[Keys.strictness]?.let(Strictness::fromCode) ?: d.strictness,
                strictHoldSeconds = p[Keys.holdSeconds] ?: d.strictHoldSeconds,
                snoozeMinutes = p[Keys.snoozeMinutes] ?: d.snoozeMinutes,
                priorities = p[Keys.priorities]?.mapNotNull(Priority::fromCode)?.toSet() ?: d.priorities,
                includeWithoutPriority = p[Keys.withoutPriority] ?: d.includeWithoutPriority,
                statuses = p[Keys.statuses]?.map(TaskStatus::fromCode)?.toSet() ?: d.statuses,
                includeOverdue = p[Keys.overdue] ?: d.includeOverdue,
                dueWithinDays = p[Keys.dueWithin] ?: d.dueWithinDays,
                includeWithoutDeadline = p[Keys.withoutDeadline] ?: d.includeWithoutDeadline,
                createdWithinDays = p[Keys.createdWithin] ?: d.createdWithinDays,
                ageFilterMode = p[Keys.ageMode]?.let(AgeFilterMode::fromCode) ?: d.ageFilterMode,
                maxTasksShown = p[Keys.maxTasks] ?: d.maxTasksShown,
            ),
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, u: UserPreferences) {
        p[Keys.language] = u.language.code
        p[Keys.calendar] = u.calendar.code
        p[Keys.theme] = u.themeMode.code
        p[Keys.dynamicColor] = u.dynamicColor
        p[Keys.density] = u.density.code
        p[Keys.lastTab] = u.lastTab
        p[Keys.board] = u.board.encode()
        p[Keys.collapsed] = u.collapsedGroups
        p[Keys.timerMinutes] = u.defaultTimerMinutes
        p[Keys.timerSound] = u.timerSound
        p[Keys.markOverdue] = u.markOverdueNotDone
        p[Keys.onboarding] = u.onboardingDone
        val d = u.digest
        p[Keys.digestEnabled] = d.enabled
        p[Keys.digestTimes] = d.times.distinct().sorted().joinToString(",") { DateCodes.time(it) }
        p[Keys.repeatEnabled] = d.repeatEnabled
        p[Keys.repeatMinutes] = d.repeatIntervalMinutes
        p[Keys.quietEnabled] = d.quietHoursEnabled
        p[Keys.quietStart] = DateCodes.time(d.quietStart)
        p[Keys.quietEnd] = DateCodes.time(d.quietEnd)
        p[Keys.strictness] = d.strictness.code
        p[Keys.holdSeconds] = d.strictHoldSeconds
        p[Keys.snoozeMinutes] = d.snoozeMinutes
        p[Keys.priorities] = d.priorities.map { it.code }.toSet()
        p[Keys.withoutPriority] = d.includeWithoutPriority
        p[Keys.statuses] = d.statuses.map { it.code }.toSet()
        p[Keys.overdue] = d.includeOverdue
        p[Keys.dueWithin] = d.dueWithinDays
        p[Keys.withoutDeadline] = d.includeWithoutDeadline
        p[Keys.createdWithin] = d.createdWithinDays
        p[Keys.ageMode] = d.ageFilterMode.code
        p[Keys.maxTasks] = d.maxTasksShown
    }

    private object Keys {
        val language = stringPreferencesKey("language")
        val calendar = stringPreferencesKey("calendar")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val density = stringPreferencesKey("density")
        val lastTab = stringPreferencesKey("last_tab")
        val board = stringPreferencesKey("board_view")
        val collapsed = stringSetPreferencesKey("collapsed_groups")
        val timerMinutes = intPreferencesKey("default_timer_minutes")
        val timerSound = booleanPreferencesKey("timer_sound")
        val markOverdue = booleanPreferencesKey("mark_overdue_not_done")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val digestEnabled = booleanPreferencesKey("digest_enabled")
        val digestTimes = stringPreferencesKey("digest_times")
        val repeatEnabled = booleanPreferencesKey("digest_repeat_enabled")
        val repeatMinutes = intPreferencesKey("digest_repeat_minutes")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = stringPreferencesKey("quiet_start")
        val quietEnd = stringPreferencesKey("quiet_end")
        val strictness = stringPreferencesKey("strictness")
        val holdSeconds = intPreferencesKey("strict_hold_seconds")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val priorities = stringSetPreferencesKey("digest_priorities")
        val withoutPriority = booleanPreferencesKey("digest_without_priority")
        val statuses = stringSetPreferencesKey("digest_statuses")
        val overdue = booleanPreferencesKey("digest_overdue")
        val dueWithin = intPreferencesKey("digest_due_within_days")
        val withoutDeadline = booleanPreferencesKey("digest_without_deadline")
        val createdWithin = intPreferencesKey("digest_created_within_days")
        val ageMode = stringPreferencesKey("digest_age_mode")
        val maxTasks = intPreferencesKey("digest_max_tasks")
    }
}
