package io.github.alinourix.taski.core.alarms

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.alinourix.taski.core.domain.timer.FocusTimerController

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmsModule {
    @Binds
    abstract fun focusTimer(impl: DefaultFocusTimerController): FocusTimerController
}
