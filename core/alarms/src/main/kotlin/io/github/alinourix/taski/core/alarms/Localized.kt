package io.github.alinourix.taski.core.alarms

import android.content.Context
import android.content.res.Configuration
import io.github.alinourix.taski.core.domain.AppLanguage
import java.util.Locale

/**
 * A context in the app's chosen language. Notifications are built outside any
 * activity, where the per-app locale is not applied on older Android versions.
 */
internal fun Context.localized(language: AppLanguage): Context {
    if (language == AppLanguage.System) return this
    val config = Configuration(resources.configuration)
    val locale = Locale.forLanguageTag(language.code)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}

internal fun Context.isPersian(): Boolean = resources.configuration.locales[0].language == "fa"
