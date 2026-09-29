package io.github.alinourix.taski.core.alarms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.repository.TimerRepository
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.timer.FocusTimerController
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The focus timer, without a foreground service: the countdown lives in a
 * local table as timestamps, the notification counts down with the system
 * chronometer, and an alarm fires when it reaches zero. So the timer survives
 * the app being closed, and costs nothing while it runs.
 */
@Singleton
class DefaultFocusTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timers: TimerRepository,
    private val tasks: TaskRepository,
    private val preferences: PreferencesRepository,
    private val alarms: AlarmScheduler,
    private val notifier: Notifier,
    private val clock: Clock,
) : FocusTimerController {
    private val mutex = Mutex()

    override val active: Flow<FocusTimerState?> = timers.observeActive()

    override suspend fun start(taskId: String, minutes: Int) = mutex.withLock {
        val current = timers.active()
        if (current != null && current.taskId == taskId) {
            apply(current.start(clock.nowMillis()))
            return@withLock
        }
        current?.let { record(it, finished = false) }
        apply(FocusTimerState(taskId, minutes.coerceAtLeast(1) * 60).start(clock.nowMillis()))
    }

    override suspend fun pause() = mutex.withLock { timers.active()?.let { apply(it.pause(clock.nowMillis())) } ?: Unit }

    override suspend fun resume() = mutex.withLock { timers.active()?.let { apply(it.start(clock.nowMillis())) } ?: Unit }

    override suspend fun addMinutes(minutes: Int) = mutex.withLock { timers.active()?.let { apply(it.addMinutes(minutes)) } ?: Unit }

    override suspend fun reset() = mutex.withLock {
        val current = timers.active() ?: return@withLock
        record(current, finished = false)
        apply(FocusTimerState(current.taskId, current.plannedSeconds))
    }

    override suspend fun stop() = mutex.withLock {
        val current = timers.active() ?: return@withLock
        record(current, finished = false)
        clear()
    }

    /** The end alarm: finish if the countdown really is over (it may have been paused or extended since). */
    suspend fun onEndAlarm() = mutex.withLock {
        val current = timers.active() ?: return@withLock
        val now = clock.nowMillis()
        if (!current.isRunning || !current.isFinished(now)) {
            apply(current)
            return@withLock
        }
        record(current, finished = true)
        clear()
        val title = tasks.observeItem(current.taskId).first()?.task?.title.orEmpty()
        val prefs = preferences.preferences.first()
        notifier.createChannels(prefs)
        notifier.timerFinished(title, prefs)
    }

    /** After a reboot: the alarm is gone, so set it (and the notification) again. */
    suspend fun restore() = mutex.withLock {
        val current = timers.active() ?: return@withLock
        if (current.isRunning && current.isFinished(clock.nowMillis())) {
            record(current, finished = true)
            clear()
        } else {
            apply(current)
        }
    }

    private suspend fun apply(state: FocusTimerState) {
        timers.setActive(state)
        val endIntent = TimerReceiver.endIntent(context)
        val endsAt = state.endsAt()
        if (state.isRunning && endsAt != null) alarms.schedule(END_REQUEST, endsAt, endIntent) else alarms.cancel(END_REQUEST, endIntent)
        val prefs = preferences.preferences.first()
        notifier.createChannels(prefs)
        val title = tasks.observeItem(state.taskId).first()?.task?.title.orEmpty()
        notifier.timer(state, title, prefs, clock.nowMillis())
    }

    private suspend fun clear() {
        timers.setActive(null)
        alarms.cancel(END_REQUEST, TimerReceiver.endIntent(context))
        notifier.cancelTimer()
    }

    /** One session per countdown, holding the time actually focused (pauses excluded). */
    private suspend fun record(state: FocusTimerState, finished: Boolean) {
        val startedAt = state.startedAt ?: return
        val spent = state.spentSeconds(clock.nowMillis()).coerceAtMost(state.plannedSeconds.toLong())
        timers.recordSession(state.taskId, startedAt, startedAt + spent * 1000, state.plannedSeconds, finished)
    }

    private companion object {
        const val END_REQUEST = 3
    }
}

@AndroidEntryPoint
class TimerReceiver : BroadcastReceiver() {
    @Inject lateinit var timer: DefaultFocusTimerController

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_END -> timer.onEndAlarm()
                    ACTION_PAUSE -> timer.pause()
                    ACTION_RESUME -> timer.resume()
                    ACTION_ADD_FIVE -> timer.addMinutes(5)
                    ACTION_STOP -> timer.stop()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_END = "io.github.alinourix.taski.TIMER_END"
        const val ACTION_PAUSE = "io.github.alinourix.taski.TIMER_PAUSE"
        const val ACTION_RESUME = "io.github.alinourix.taski.TIMER_RESUME"
        const val ACTION_ADD_FIVE = "io.github.alinourix.taski.TIMER_ADD_FIVE"
        const val ACTION_STOP = "io.github.alinourix.taski.TIMER_STOP"

        fun endIntent(context: Context) = Intent(context, TimerReceiver::class.java).setAction(ACTION_END)

        fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(), Intent(context, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
