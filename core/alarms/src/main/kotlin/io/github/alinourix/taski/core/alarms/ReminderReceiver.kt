package io.github.alinourix.taski.core.alarms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.reminder.ReminderRules
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ReminderRecord
import io.github.alinourix.taski.core.domain.repository.ReminderStateRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Alarms and notification actions for task reminders and the digest. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var states: ReminderStateRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var digest: DigestRunner
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                handle(intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(intent: Intent) {
        val prefs = preferences.preferences.first()
        notifier.createChannels(prefs)
        when (intent.action) {
            ACTION_TASK -> {
                val id = intent.taskId() ?: return
                val task = tasks.observeItem(id).first()?.task ?: return
                val at = ReminderRules.taskReminderAt(task, clock.zone()) ?: return
                val key = ReminderRules.deliveryKey(task, at)
                val state = states.get(id)
                val snoozeDue = state?.snoozedUntil?.let { it <= clock.nowMillis() + 1_000 } == true
                if (state?.deliveredKey == key && !snoozeDue) return
                notifier.taskReminder(task, prefs, clock.today())
                states.put(ReminderRecord(id, null, key, null))
                scheduler.rebuildNow()
            }
            ACTION_DONE -> {
                val id = intent.taskId() ?: return
                tasks.setStatus(id, TaskStatus.Done)
                notifier.cancelTaskReminder(id)
            }
            ACTION_SNOOZE -> {
                val id = intent.taskId() ?: return
                val until = clock.nowMillis() + prefs.digest.snoozeMinutes * 60_000L
                val state = states.get(id)
                states.put(ReminderRecord(id, null, state?.deliveredKey, until))
                notifier.cancelTaskReminder(id)
                scheduler.rebuildNow()
            }
            ACTION_DIGEST -> digest.fire()
            ACTION_SNOOZE_DIGEST -> {
                notifier.cancelDigest()
                scheduler.snoozeDigest(prefs.digest.snoozeMinutes)
            }
        }
    }

    companion object {
        const val ACTION_TASK = "io.github.alinourix.taski.REMINDER_TASK"
        const val ACTION_DONE = "io.github.alinourix.taski.REMINDER_DONE"
        const val ACTION_SNOOZE = "io.github.alinourix.taski.REMINDER_SNOOZE"
        const val ACTION_DIGEST = "io.github.alinourix.taski.DIGEST"
        const val ACTION_SNOOZE_DIGEST = "io.github.alinourix.taski.DIGEST_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun taskIntent(context: Context, taskId: String) =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_TASK).putExtra(EXTRA_TASK_ID, taskId)

        fun digestIntent(context: Context, snoozed: Boolean = false) =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DIGEST).putExtra("snoozed", snoozed)

        fun pending(context: Context, action: String, taskId: String?): PendingIntent = PendingIntent.getBroadcast(
            context,
            (action + taskId).hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Boot, app update, clock and time-zone changes: every alarm is set again from the data. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var timer: DefaultFocusTimerController

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                scheduler.rebuildNow()
                timer.restore()
            } finally {
                pending.finish()
            }
        }
    }
}
