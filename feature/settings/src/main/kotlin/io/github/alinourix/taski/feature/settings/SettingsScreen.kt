package io.github.alinourix.taski.feature.settings

import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.domain.AppLanguage
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.Density
import io.github.alinourix.taski.core.domain.ThemeMode
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.component.LocalSnackbarHostState
import io.github.alinourix.taski.core.ui.format.localizeDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onOpenReminders: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenTrash: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    val persian = LocalUiConfig.current.persian
    val importedText = stringResource(R.string.settings_imported)
    val exportedText = stringResource(R.string.settings_exported)
    val failedText = stringResource(R.string.settings_import_failed)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch {
            for (uri in uris) {
                val read = withContext(Dispatchers.IO) {
                    runCatching {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        } ?: "Imported"
                        name to context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    }.getOrNull()
                }
                if (read == null) {
                    snackbar.showSnackbar(failedText)
                    continue
                }
                viewModel.import(read.first, read.second) { result ->
                    scope.launch {
                        snackbar.showSnackbar(importedText.replace("%1\$d", result.tasks.toString()).replace("%2\$s", read.first.removeSuffix(".md")).localizeDigits(persian))
                    }
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            viewModel.export { text ->
                scope.launch {
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } }
                    snackbar.showSnackbar(exportedText)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.settings_title)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        val current = prefs
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.settings_appearance)) {
                    val languageNames = mapOf(
                        AppLanguage.System to stringResource(R.string.settings_language_system),
                        AppLanguage.English to "English",
                        AppLanguage.Persian to "فارسی",
                    )
                    SettingRow(0, 5, stringResource(R.string.settings_language), below = {
                        ConnectedToggleGroup(AppLanguage.entries, current.language, { v -> viewModel.update { it.copy(language = v) } }, languageNames::getValue)
                    })
                    val calendars = mapOf(
                        CalendarSystem.Gregorian to stringResource(R.string.settings_calendar_gregorian),
                        CalendarSystem.Jalali to stringResource(R.string.settings_calendar_jalali),
                    )
                    SettingRow(1, 5, stringResource(R.string.settings_calendar), below = {
                        ConnectedToggleGroup(CalendarSystem.entries, current.calendar, { v -> viewModel.update { it.copy(calendar = v) } }, calendars::getValue)
                    })
                    val themes = mapOf(
                        ThemeMode.System to stringResource(R.string.settings_theme_system),
                        ThemeMode.Light to stringResource(R.string.settings_theme_light),
                        ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
                    )
                    SettingRow(2, 5, stringResource(R.string.settings_theme), below = {
                        ConnectedToggleGroup(ThemeMode.entries, current.themeMode, { v -> viewModel.update { it.copy(themeMode = v) } }, themes::getValue)
                    })
                    val densities = mapOf(
                        Density.Comfortable to stringResource(R.string.settings_density_comfortable),
                        Density.Compact to stringResource(R.string.settings_density_compact),
                    )
                    SettingRow(3, 5, stringResource(R.string.settings_density), below = {
                        ConnectedToggleGroup(Density.entries, current.density, { v -> viewModel.update { it.copy(density = v) } }, densities::getValue)
                    })
                    SwitchRow(
                        4, 5, stringResource(R.string.settings_dynamic_color),
                        current.materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        { v -> viewModel.update { it.copy(materialYou = v) } },
                        supporting = stringResource(R.string.settings_dynamic_color_body),
                        icon = Icons.Rounded.Palette,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_tasks)) {
                    SwitchRow(0, 3, stringResource(R.string.settings_mark_overdue), current.markOverdueNotDone, { v -> viewModel.update { it.copy(markOverdueNotDone = v) } },
                        supporting = stringResource(R.string.settings_mark_overdue_body), icon = Icons.Rounded.TaskAlt)
                    SettingRow(1, 3, stringResource(R.string.settings_timer_minutes), icon = Icons.Rounded.Timer, below = {
                        Stepper(
                            "${current.defaultTimerMinutes}′".localizeDigits(persian),
                            current.defaultTimerMinutes,
                            { v -> viewModel.update { it.copy(defaultTimerMinutes = v) } },
                            5..180,
                            step = 5,
                        )
                    })
                    SwitchRow(2, 3, stringResource(R.string.settings_timer_sound), current.timerSound, { v -> viewModel.update { it.copy(timerSound = v) } }, icon = Icons.Rounded.VolumeUp)
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_reminders)) {
                    NavRow(0, 3, stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_body), Icons.Rounded.Notifications, onOpenReminders)
                    NavRow(1, 3, stringResource(R.string.settings_tags), stringResource(R.string.settings_tags_body), Icons.Rounded.Sell, onOpenTags)
                    NavRow(2, 3, stringResource(R.string.settings_trash), stringResource(R.string.settings_trash_body), Icons.Rounded.DeleteSweep, onOpenTrash)
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_obsidian)) {
                    SettingRow(0, 2, stringResource(R.string.settings_import), supporting = stringResource(R.string.settings_import_body), icon = Icons.Rounded.FileDownload,
                        onClick = { importLauncher.launch(arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")) })
                    SettingRow(1, 2, stringResource(R.string.settings_export), supporting = stringResource(R.string.settings_export_body), icon = Icons.Rounded.FileUpload,
                        onClick = { exportLauncher.launch("Taski.md") })
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_about)) {
                    SettingRow(0, 2, stringResource(R.string.settings_sync), supporting = stringResource(R.string.settings_sync_body), icon = Icons.Rounded.CloudOff)
                    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
                    SettingRow(1, 2, "Taski", supporting = stringResource(R.string.settings_version, version).localizeDigits(persian), icon = Icons.Rounded.Info)
                }
            }
        }
    }
}
