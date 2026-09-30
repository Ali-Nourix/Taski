package io.github.alinourix.taski.core.alarms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.alinourix.taski.core.designsystem.component.ShapeIcon
import io.github.alinourix.taski.core.designsystem.theme.TaskiTheme
import io.github.alinourix.taski.core.domain.ThemeMode
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.reminder.Strictness
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.UiConfig
import io.github.alinourix.taski.core.ui.component.DueChip
import io.github.alinourix.taski.core.ui.component.PriorityChip
import io.github.alinourix.taski.core.ui.format.localizeDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The plugin's full-screen reminder alert, for strict mode: shown over the
 * lock screen, it can be snoozed, and acknowledged only after a short hold.
 */
@AndroidEntryPoint
class ReminderAlertActivity : ComponentActivity() {
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var digest: DigestRunner
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var clock: Clock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by preferences.preferences.collectAsState(initial = null)
            val current = prefs ?: return@setContent
            var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
            LaunchedEffect(Unit) { tasks = digest.currentTasks() }
            val dark = when (current.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val persian = LocalConfiguration.current.locales[0].language == "fa"
            TaskiTheme(darkTheme = dark, materialYou = current.materialYou, persian = persian) {
                CompositionLocalProvider(LocalUiConfig provides UiConfig(current.calendar, persian, current.density, clock.today(), clock.zone())) {
                    val strict = current.digest.strictness == Strictness.Strict
                    var hold by remember { mutableIntStateOf(if (strict) current.digest.strictHoldSeconds else 0) }
                    LaunchedEffect(Unit) { while (hold > 0) { delay(1_000); hold-- } }
                    BackHandler(enabled = strict) {}
                    Alert(
                        tasks = tasks.take(current.digest.maxTasksShown),
                        total = tasks.size,
                        hold = hold,
                        strict = strict,
                        snoozeMinutes = current.digest.snoozeMinutes,
                        onAcknowledge = { notifier.cancelDigest(); finish() },
                        onSnooze = {
                            notifier.cancelDigest()
                            scheduler.snoozeDigest(current.digest.snoozeMinutes)
                            finish()
                        },
                        onOpen = { taskId ->
                            notifier.cancelDigest()
                            lifecycleScope.launch { notifier.openTask(taskId).send(); finish() }
                        },
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Alert(
    tasks: List<TaskItem>,
    total: Int,
    hold: Int,
    strict: Boolean,
    snoozeMinutes: Int,
    onAcknowledge: () -> Unit,
    onSnooze: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val persian = LocalUiConfig.current.persian
    Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShapeIcon(Icons.Rounded.NotificationsActive, polygon = MaterialShapes.SoftBurst, size = 88.dp, iconSize = 40.dp)
                    Text(
                        pluralStringResource(R.plurals.digest_title, total, total).localizeDigits(persian),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    if (strict) Text(stringResource(R.string.alert_strict_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(tasks, key = { it.id }) { item ->
                            Surface(onClick = { onOpen(item.id) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.task.title, style = MaterialTheme.typography.titleSmall)
                                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        DueChip(item.task)
                                        item.task.priority?.let { PriorityChip(it) }
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = onAcknowledge, enabled = hold == 0, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (hold > 0) stringResource(R.string.alert_acknowledge_in, hold).localizeDigits(persian)
                            else stringResource(if (strict) R.string.alert_acknowledge else R.string.alert_dismiss),
                        )
                    }
                    FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_snooze, snoozeMinutes).localizeDigits(persian))
                    }
                    if (tasks.isEmpty()) OutlinedButton(onClick = onAcknowledge) { Text(stringResource(R.string.alert_dismiss)) }
                }
            }
        }
    }
}
