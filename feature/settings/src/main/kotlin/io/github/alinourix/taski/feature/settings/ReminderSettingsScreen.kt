package io.github.alinourix.taski.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.reminder.AgeFilterMode
import io.github.alinourix.taski.core.domain.reminder.Strictness
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.TaskIcons
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.picker.TimeDialog
import java.time.LocalTime
import io.github.alinourix.taski.core.ui.R as UiR

private enum class TimeTarget { NewSlot, QuietStart, QuietEnd }

@Composable
fun ReminderSettingsScreen(onBack: () -> Unit, onSendTest: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val persian = LocalUiConfig.current.persian
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var picking by remember { mutableStateOf<TimeTarget?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = prefs ?: run { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val digest = current.digest
        fun t(time: LocalTime) = CalendarText.time(time, persian)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp)) {
            item { PermissionBanner() }
            item {
                SettingsGroup(stringResource(R.string.reminders_digest)) {
                    SwitchRow(0, 4, stringResource(R.string.reminders_digest), digest.enabled, { v -> viewModel.updateDigest { it.copy(enabled = v) } },
                        supporting = stringResource(R.string.reminders_digest_body), icon = Icons.Rounded.NotificationsActive)
                    SettingRow(1, 4, stringResource(R.string.reminders_times), below = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            digest.times.forEach { time ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.updateDigest { d -> d.copy(times = d.times - time) } },
                                    label = { Text(t(time)) },
                                    trailingIcon = { Icon(Icons.Rounded.Close, stringResource(UiR.string.action_delete), Modifier.size(18.dp)) },
                                )
                            }
                            AssistChip(onClick = { picking = TimeTarget.NewSlot }, label = { Text(stringResource(R.string.reminders_add_time)) },
                                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
                        }
                    })
                    SwitchRow(2, 4, stringResource(R.string.reminders_repeat), digest.repeatEnabled, { v -> viewModel.updateDigest { it.copy(repeatEnabled = v) } }, icon = Icons.Rounded.Repeat)
                    SettingRow(3, 4, stringResource(R.string.reminders_repeat_every, digest.repeatIntervalMinutes).localizeDigits(persian), below = {
                        Stepper("", digest.repeatIntervalMinutes, { v -> viewModel.updateDigest { it.copy(repeatIntervalMinutes = v) } }, 15..720, step = 15)
                    })
                }
            }
            item {
                SettingsGroup(stringResource(R.string.reminders_quiet)) {
                    SwitchRow(0, 2, stringResource(R.string.reminders_quiet), digest.quietHoursEnabled, { v -> viewModel.updateDigest { it.copy(quietHoursEnabled = v) } },
                        supporting = stringResource(R.string.reminders_quiet_range, t(digest.quietStart), t(digest.quietEnd)), icon = Icons.Rounded.Bedtime)
                    SettingRow(1, 2, stringResource(R.string.reminders_quiet_range, t(digest.quietStart), t(digest.quietEnd)), below = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { picking = TimeTarget.QuietStart }, label = { Text(stringResource(R.string.reminders_quiet_start) + " " + t(digest.quietStart)) })
                            AssistChip(onClick = { picking = TimeTarget.QuietEnd }, label = { Text(stringResource(R.string.reminders_quiet_end) + " " + t(digest.quietEnd)) })
                        }
                    })
                }
            }
            item {
                SettingsGroup(stringResource(R.string.reminders_strictness)) {
                    val names = mapOf(
                        Strictness.Gentle to stringResource(R.string.reminders_gentle),
                        Strictness.Normal to stringResource(R.string.reminders_normal),
                        Strictness.Strict to stringResource(R.string.reminders_strict),
                    )
                    val bodies = mapOf(
                        Strictness.Gentle to stringResource(R.string.reminders_gentle_body),
                        Strictness.Normal to stringResource(R.string.reminders_normal_body),
                        Strictness.Strict to stringResource(R.string.reminders_strict_body),
                    )
                    SettingRow(0, 2, names.getValue(digest.strictness), supporting = bodies.getValue(digest.strictness), below = {
                        ConnectedToggleGroup(Strictness.entries, digest.strictness, { v -> viewModel.updateDigest { it.copy(strictness = v) } }, names::getValue)
                    })
                    SettingRow(1, 2, stringResource(R.string.reminders_snooze, digest.snoozeMinutes).localizeDigits(persian), below = {
                        Column {
                            Stepper("", digest.snoozeMinutes, { v -> viewModel.updateDigest { it.copy(snoozeMinutes = v) } }, 5..120, step = 5)
                            if (digest.strictness == Strictness.Strict) {
                                Stepper(stringResource(R.string.reminders_hold, digest.strictHoldSeconds).localizeDigits(persian), digest.strictHoldSeconds,
                                    { v -> viewModel.updateDigest { it.copy(strictHoldSeconds = v) } }, 0..60)
                            }
                        }
                    })
                }
            }
            item {
                SettingsGroup(stringResource(R.string.reminders_filters)) {
                    SettingRow(0, 5, stringResource(R.string.reminders_priorities), below = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Priority.entries.forEach { p ->
                                FilterChip(
                                    selected = p in digest.priorities,
                                    onClick = { viewModel.updateDigest { d -> d.copy(priorities = if (p in d.priorities) d.priorities - p else d.priorities + p) } },
                                    label = { Text(priorityLabel(p)) },
                                    leadingIcon = { Icon(TaskIcons.priority(p), null, Modifier.size(18.dp)) },
                                )
                            }
                            FilterChip(
                                selected = digest.includeWithoutPriority,
                                onClick = { viewModel.updateDigest { it.copy(includeWithoutPriority = !it.includeWithoutPriority) } },
                                label = { Text(stringResource(R.string.reminders_no_priority)) },
                            )
                        }
                    })
                    SettingRow(1, 5, stringResource(R.string.reminders_statuses), below = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(TaskStatus.NotStarted, TaskStatus.InProgress, TaskStatus.NotDone).forEach { s ->
                                FilterChip(
                                    selected = s in digest.statuses,
                                    onClick = { viewModel.updateDigest { d -> d.copy(statuses = if (s in d.statuses) d.statuses - s else d.statuses + s) } },
                                    label = { Text(statusLabel(s)) },
                                )
                            }
                        }
                    })
                    SettingRow(2, 5, if (digest.dueWithinDays == 0) stringResource(R.string.reminders_due_today_only) else stringResource(R.string.reminders_due_within, digest.dueWithinDays).localizeDigits(persian), below = {
                        Column {
                            Stepper("", digest.dueWithinDays, { v -> viewModel.updateDigest { it.copy(dueWithinDays = v) } }, 0..60)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(digest.includeOverdue, { viewModel.updateDigest { it.copy(includeOverdue = !it.includeOverdue) } }, label = { Text(stringResource(R.string.reminders_overdue)) })
                                FilterChip(digest.includeWithoutDeadline, { viewModel.updateDigest { it.copy(includeWithoutDeadline = !it.includeWithoutDeadline) } }, label = { Text(stringResource(R.string.reminders_no_deadline)) })
                            }
                        }
                    })
                    SettingRow(3, 5, if (digest.createdWithinDays == 0) stringResource(R.string.reminders_age_off) else stringResource(R.string.reminders_created_within, digest.createdWithinDays).localizeDigits(persian), below = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stepper("", digest.createdWithinDays, { v -> viewModel.updateDigest { it.copy(createdWithinDays = v) } }, 0..90)
                            if (digest.createdWithinDays > 0) {
                                val modes = mapOf(
                                    AgeFilterMode.Only to stringResource(R.string.reminders_age_only),
                                    AgeFilterMode.Or to stringResource(R.string.reminders_age_or),
                                    AgeFilterMode.And to stringResource(R.string.reminders_age_and),
                                )
                                Text(stringResource(R.string.reminders_age_mode), style = MaterialTheme.typography.labelLarge)
                                ConnectedToggleGroup(AgeFilterMode.entries, digest.ageFilterMode, { v -> viewModel.updateDigest { it.copy(ageFilterMode = v) } }, modes::getValue)
                            }
                        }
                    })
                    SettingRow(4, 5, stringResource(R.string.reminders_max, digest.maxTasksShown).localizeDigits(persian), below = {
                        Stepper("", digest.maxTasksShown, { v -> viewModel.updateDigest { it.copy(maxTasksShown = v) } }, 1..50)
                    })
                }
            }
            item {
                Button(onClick = onSendTest, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Rounded.Send, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.reminders_test), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    when (val target = picking) {
        null -> Unit
        else -> {
            val digest = prefs?.digest
            val initial = when (target) {
                TimeTarget.NewSlot -> LocalTime.of(18, 0)
                TimeTarget.QuietStart -> digest?.quietStart ?: LocalTime.of(23, 0)
                TimeTarget.QuietEnd -> digest?.quietEnd ?: LocalTime.of(7, 0)
            }
            TimeDialog(initial, onDismiss = { picking = null }) { time ->
                viewModel.updateDigest { d ->
                    when (target) {
                        TimeTarget.NewSlot -> d.copy(times = (d.times + time).distinct().sorted())
                        TimeTarget.QuietStart -> d.copy(quietStart = time)
                        TimeTarget.QuietEnd -> d.copy(quietEnd = time)
                    }
                }
                picking = null
            }
        }
    }
}

/** Explains, and offers to fix, the two permissions reminders depend on. */
@Composable
private fun PermissionBanner() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notificationsOn = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactOn = remember(tick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    if (!notificationsOn) {
        Banner(stringResource(R.string.reminders_permission_notifications)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
    }
    if (!exactOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Banner(stringResource(R.string.reminders_permission_exact)) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
        }
    }
}

@Composable
private fun Banner(text: String, onFix: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.WarningAmber, null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onFix) { Text(stringResource(R.string.reminders_permission_fix)) }
        }
    }
}
