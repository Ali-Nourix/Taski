package io.github.alinourix.taski.core.alarms

import io.github.alinourix.taski.core.domain.model.TaskItem
import io.github.alinourix.taski.core.domain.reminder.ReminderRules
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and shows the daily digest: the plugin's reminder alert, as a notification. */
@Singleton
class DigestRunner @Inject constructor(
    private val tasks: TaskRepository,
    private val preferences: PreferencesRepository,
    private val notifier: Notifier,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    /** The tasks a digest would list right now, most urgent first. */
    suspend fun currentTasks(): List<TaskItem> {
        val prefs = preferences.preferences.first()
        return ReminderRules.digestTasks(tasks.observeItems().first(), prefs.digest, clock.today(), clock.now())
    }

    /** Shows the digest. A scheduled run stays silent when there is nothing to nag about. */
    suspend fun fire(): Int {
        val prefs = preferences.preferences.first()
        if (prefs.markOverdueNotDone) tasks.markOverdueAsNotDone()
        val selected = currentTasks()
        scheduler.lastDigestAt = clock.nowMillis()
        if (selected.isNotEmpty()) {
            notifier.createChannels(prefs)
            notifier.digest(selected.take(prefs.digest.maxTasksShown), selected.size, prefs, clock.today())
        }
        scheduler.rebuildNow()
        return selected.size
    }
}
