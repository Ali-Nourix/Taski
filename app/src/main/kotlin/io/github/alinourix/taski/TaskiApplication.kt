package io.github.alinourix.taski

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import io.github.alinourix.taski.core.alarms.DefaultFocusTimerController
import io.github.alinourix.taski.core.alarms.Notifier
import io.github.alinourix.taski.core.alarms.ReminderScheduler
import io.github.alinourix.taski.core.data.di.ApplicationScope
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TaskiApplication : Application() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var timer: DefaultFocusTimerController
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            notifier.createChannels(preferences.preferences.first())
            timer.restore()
        }
        // Alarms follow the database from here on, whoever wrote to it.
        scheduler.start(scope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch {
                    if (preferences.preferences.first().markOverdueNotDone) tasks.markOverdueAsNotDone()
                }
            }
        })
    }
}
