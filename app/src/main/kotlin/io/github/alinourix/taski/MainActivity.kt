package io.github.alinourix.taski

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.alinourix.taski.core.alarms.LaunchExtras
import io.github.alinourix.taski.core.designsystem.theme.TaskiTheme
import io.github.alinourix.taski.core.domain.AppLanguage
import io.github.alinourix.taski.core.domain.ThemeMode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Something the app was asked to open from outside: a notification, or text shared into it. */
sealed interface ExternalRequest {
    data class OpenTask(val id: String) : ExternalRequest
    data object OpenTimer : ExternalRequest
    data class NewTask(val title: String) : ExternalRequest
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val request = mutableStateOf<ExternalRequest?>(null)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.prefs.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        request.value = intent.toRequest()
        followLanguagePreference()
        askForNotifications()

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            val current = prefs ?: return@setContent
            val dark = when (current.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val persian = resources.configuration.locales[0].language == "fa"
            TaskiTheme(darkTheme = dark, dynamicColor = current.dynamicColor, persian = persian) {
                TaskiApp(
                    prefs = current,
                    persian = persian,
                    clock = viewModel.clock,
                    request = request.value,
                    onRequestHandled = { request.value = null },
                    onTabChanged = viewModel::setLastTab,
                    onSendDigest = viewModel::sendDigest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toRequest()?.let { request.value = it }
    }

    /** The language setting drives AppCompat's per-app locale, which recreates the activity when it changes. */
    private fun followLanguagePreference() {
        lifecycleScope.launch {
            viewModel.prefs.filterNotNull().map { it.language }.distinctUntilChanged().collect { language ->
                val wanted = if (language == AppLanguage.System) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.code)
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
                    AppCompatDelegate.setApplicationLocales(wanted)
                }
            }
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun Intent.toRequest(): ExternalRequest? = when {
        getStringExtra(LaunchExtras.OPEN_TASK) != null -> ExternalRequest.OpenTask(getStringExtra(LaunchExtras.OPEN_TASK)!!)
        getBooleanExtra(LaunchExtras.OPEN_TIMER, false) -> ExternalRequest.OpenTimer
        action == Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { ExternalRequest.NewTask(it.trim().lines().first().take(200)) }
        else -> null
    }
}
