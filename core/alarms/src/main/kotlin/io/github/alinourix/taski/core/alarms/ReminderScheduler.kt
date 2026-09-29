package io.github.alinourix.taski.core.alarms

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.reminder.ReminderRules
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ReminderRecord
import io.github.alinourix.taski.core.domain.repository.ReminderStateRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the alarms in step with the data. Alarms are never edited one by one:
 * after any change to a task (made here, or arriving later by sync) or to the
 * reminder settings, the whole set is rebuilt from [ReminderRules], so what is
 * scheduled can never drift from what the tasks say.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    private val preferences: PreferencesRepository,
    private val states: ReminderStateRepository,
    private val alarms: AlarmScheduler,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val digestPrefs = context.getSharedPreferences("taski_digest", Context.MODE_PRIVATE)

    var lastDigestAt: Long?
        get() = digestPrefs.getLong(KEY_LAST_DIGEST, 0).takeIf { it > 0 }
        set(value) = digestPrefs.edit().putLong(KEY_LAST_DIGEST, value ?: 0).apply()

    /** Follows the database and the settings for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(tasks.observeItems(), preferences.preferences) { items, prefs -> items to prefs }
                .debounce(300)
                .collect { (items, prefs) -> rebuild(items, prefs) }
        }
    }

    /** One rebuild now, for callers without a long-lived scope (boot, time change). */
    suspend fun rebuildNow() = rebuild(tasks.observeItems().first(), preferences.preferences.first())

    private suspend fun rebuild(items: List<TaskItem>, prefs: UserPreferences) = mutex.withLock {
        val now = clock.nowMillis()
        val zone = clock.zone()
        val existing = states.all().associateBy { it.taskId }
        val wanted = mutableSetOf<String>()

        for (item in items) {
            val task = item.task
            val at = ReminderRules.taskReminderAt(task, zone)?.toEpochMilli() ?: continue
            val state = existing[task.id]
            val snoozed = state?.snoozedUntil?.takeIf { it > now }
            val key = ReminderRules.deliveryKey(task, Instant.ofEpochMilli(at))
            val delivered = state?.deliveredKey == key
            // A reminder missed while the phone was off still arrives, if it is recent.
            val fireAt = when {
                snoozed != null -> snoozed
                delivered -> continue
                at > now -> at
                now - at < MISSED_GRACE_MILLIS -> now + 1_000
                else -> continue
            }
            wanted += task.id
            if (state?.scheduledAt != fireAt) {
                alarms.schedule(requestCode(task.id), fireAt, ReminderReceiver.taskIntent(context, task.id))
                states.put(ReminderRecord(task.id, fireAt, state?.deliveredKey, state?.snoozedUntil))
            }
        }

        val stale = existing.keys - wanted
        for (id in stale) alarms.cancel(requestCode(id), ReminderReceiver.taskIntent(context, id))
        // Keep the delivery key of tasks still around, so an alert is not repeated; drop the rest.
        val liveIds = items.map { it.id }.toSet()
        states.remove(stale.filter { it !in liveIds })
        stale.filter { it in liveIds }.forEach { id -> existing[id]?.let { states.put(it.copy(scheduledAt = null)) } }

        val nextDigest = ReminderRules.nextDigestAt(Instant.ofEpochMilli(now), zone, prefs.digest, lastDigestAt?.let(Instant::ofEpochMilli))
        val digestIntent = ReminderReceiver.digestIntent(context)
        if (nextDigest == null) alarms.cancel(DIGEST_REQUEST, digestIntent) else alarms.schedule(DIGEST_REQUEST, nextDigest.toEpochMilli(), digestIntent)
    }

    fun snoozeDigest(minutes: Int) {
        alarms.schedule(DIGEST_SNOOZE_REQUEST, clock.nowMillis() + minutes * 60_000L, ReminderReceiver.digestIntent(context, snoozed = true))
    }

    companion object {
        const val DIGEST_REQUEST = 1
        const val DIGEST_SNOOZE_REQUEST = 2
        private const val MISSED_GRACE_MILLIS = 6 * 60 * 60_000L
        private const val KEY_LAST_DIGEST = "last_digest_at"

        fun requestCode(taskId: String) = 100_000 + (taskId.hashCode() and 0xFFFFFF)
    }
}

internal fun Intent.taskId(): String? = getStringExtra(ReminderReceiver.EXTRA_TASK_ID)
