package io.github.alinourix.taski.core.alarms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.query.TaskQuery
import io.github.alinourix.taski.core.domain.reminder.Strictness
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.localizeDigits
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Extras the app's launcher activity reads to open a task or the timer. */
object LaunchExtras {
    const val OPEN_TASK = "io.github.alinourix.taski.OPEN_TASK"
    const val OPEN_TIMER = "io.github.alinourix.taski.OPEN_TIMER"
}

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels(prefs: UserPreferences) {
        val c = context.localized(prefs.language)
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, c.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = c.getString(R.string.channel_reminders_body) },
                NotificationChannel(CHANNEL_DIGEST, c.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_DIGEST_QUIET, c.getString(R.string.channel_digest_quiet), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DIGEST_STRICT, c.getString(R.string.channel_digest_strict), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_TIMER, c.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(CHANNEL_TIMER_DONE, c.getString(R.string.channel_timer_done), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun taskReminder(task: Task, prefs: UserPreferences, today: LocalDate) {
        val c = context.localized(prefs.language)
        val snooze = prefs.digest.snoozeMinutes
        val builder = NotificationCompat.Builder(c, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(dueLine(c, task, prefs.calendar, today))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openTask(task.id))
            .addAction(0, c.getString(R.string.action_done), ReminderReceiver.pending(context, ReminderReceiver.ACTION_DONE, task.id))
            .addAction(0, c.getString(R.string.action_snooze, snooze).localizeDigits(c.isPersian()), ReminderReceiver.pending(context, ReminderReceiver.ACTION_SNOOZE, task.id))
        post(taskNotificationId(task.id), builder)
    }

    fun cancelTaskReminder(taskId: String) = manager.cancel(taskNotificationId(taskId))

    /** The daily digest, in the plugin's three strictness levels. */
    fun digest(items: List<TaskItem>, total: Int, prefs: UserPreferences, today: LocalDate) {
        val c = context.localized(prefs.language)
        val persian = c.isPersian()
        val title = c.resources.getQuantityString(R.plurals.digest_title, total, total).localizeDigits(persian)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        items.forEach { style.addLine("${it.task.title} · ${dueLine(c, it.task, prefs.calendar, today)}") }
        if (total > items.size) style.setSummaryText(c.getString(R.string.digest_more, total - items.size).localizeDigits(persian))
        val strictness = prefs.digest.strictness
        val channel = when (strictness) {
            Strictness.Gentle -> CHANNEL_DIGEST_QUIET
            Strictness.Normal -> CHANNEL_DIGEST
            Strictness.Strict -> CHANNEL_DIGEST_STRICT
        }
        val builder = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(items.firstOrNull()?.task?.title)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (strictness == Strictness.Gentle) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, c.getString(R.string.action_snooze, prefs.digest.snoozeMinutes).localizeDigits(persian), ReminderReceiver.pending(context, ReminderReceiver.ACTION_SNOOZE_DIGEST, null))
        if (strictness == Strictness.Strict) {
            val alert = PendingIntent.getActivity(
                context, DIGEST_ID, Intent(context, ReminderAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(alert, true).setOngoing(true)
        }
        post(DIGEST_ID, builder)
    }

    fun cancelDigest() = manager.cancel(DIGEST_ID)

    fun timer(state: FocusTimerState, taskTitle: String, prefs: UserPreferences, now: Long) {
        val c = context.localized(prefs.language)
        val persian = c.isPersian()
        val builder = NotificationCompat.Builder(c, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskTitle)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openTimer())
        val endsAt = state.endsAt()
        if (state.isRunning && endsAt != null) {
            builder.setContentText(c.getString(R.string.timer_running))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(endsAt)
                .addAction(0, c.getString(R.string.action_pause), TimerReceiver.pending(context, TimerReceiver.ACTION_PAUSE))
        } else {
            builder.setContentText(c.getString(R.string.timer_paused, FocusTimerState.formatClock(state.remainingSeconds(now))).localizeDigits(persian))
                .setShowWhen(false)
                .addAction(0, c.getString(R.string.action_resume), TimerReceiver.pending(context, TimerReceiver.ACTION_RESUME))
        }
        builder.addAction(0, c.getString(R.string.action_add_five), TimerReceiver.pending(context, TimerReceiver.ACTION_ADD_FIVE))
            .addAction(0, c.getString(R.string.action_stop), TimerReceiver.pending(context, TimerReceiver.ACTION_STOP))
        post(TIMER_ID, builder)
    }

    fun cancelTimer() = manager.cancel(TIMER_ID)

    fun timerFinished(taskTitle: String, prefs: UserPreferences) {
        val c = context.localized(prefs.language)
        val builder = NotificationCompat.Builder(c, CHANNEL_TIMER_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.timer_done_title))
            .setContentText(c.getString(R.string.timer_done_body, taskTitle))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openTimer())
        if (!prefs.timerSound) builder.setSilent(true)
        post(TIMER_DONE_ID, builder)
    }

    private fun dueLine(c: Context, task: Task, calendar: CalendarSystem, today: LocalDate): String {
        val due = task.dueDate ?: return c.getString(R.string.due_none)
        val persian = c.isPersian()
        val days = TaskQuery.daysUntil(due, today)
        val time = task.dueTime?.let { " " + CalendarText.time(it, persian) }.orEmpty()
        return when {
            days < 0 -> c.getString(R.string.due_overdue)
            days == 0L -> c.getString(R.string.due_today) + time
            days == 1L -> c.getString(R.string.due_tomorrow) + time
            days <= 6 -> c.getString(R.string.due_days_left, days.toInt()).localizeDigits(persian)
            else -> CalendarText.date(due, calendar, persian, today)
        }
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        manager.notify(id, builder.build())
    }

    private fun launchIntent(): Intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, launchIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun openTask(taskId: String): PendingIntent = PendingIntent.getActivity(
        context, taskId.hashCode(), launchIntent().putExtra(LaunchExtras.OPEN_TASK, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openTimer(): PendingIntent = PendingIntent.getActivity(
        context, TIMER_ID, launchIntent().putExtra(LaunchExtras.OPEN_TIMER, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun taskNotificationId(taskId: String) = 10_000 + (taskId.hashCode() and 0x7FFFFF)

    companion object {
        const val CHANNEL_REMINDERS = "task_reminders"
        const val CHANNEL_DIGEST = "digest"
        const val CHANNEL_DIGEST_QUIET = "digest_gentle"
        const val CHANNEL_DIGEST_STRICT = "digest_strict"
        const val CHANNEL_TIMER = "focus_timer"
        const val CHANNEL_TIMER_DONE = "focus_timer_done"
        const val DIGEST_ID = 1
        const val TIMER_ID = 2
        const val TIMER_DONE_ID = 3
    }
}
