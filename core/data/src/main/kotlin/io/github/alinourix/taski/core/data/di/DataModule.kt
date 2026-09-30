package io.github.alinourix.taski.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.alinourix.taski.core.data.db.TaskiDatabase
import io.github.alinourix.taski.core.data.prefs.DataStorePreferencesRepository
import io.github.alinourix.taski.core.data.repository.DefaultActivityRepository
import io.github.alinourix.taski.core.data.repository.DefaultProjectRepository
import io.github.alinourix.taski.core.data.repository.DefaultSavedViewRepository
import io.github.alinourix.taski.core.data.repository.DefaultTagRepository
import io.github.alinourix.taski.core.data.repository.DefaultTaskRepository
import io.github.alinourix.taski.core.data.repository.DefaultTimerRepository
import io.github.alinourix.taski.core.data.sync.NoOpSyncEngine
import io.github.alinourix.taski.core.data.sync.PersistentHlcClock
import io.github.alinourix.taski.core.data.sync.SyncEngine
import io.github.alinourix.taski.core.data.transfer.DefaultTransferRepository
import io.github.alinourix.taski.core.domain.id.IdGenerator
import io.github.alinourix.taski.core.domain.id.UuidV7Generator
import io.github.alinourix.taski.core.domain.repository.ActivityRepository
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.SavedViewRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.repository.TimerRepository
import io.github.alinourix.taski.core.domain.repository.TransferRepository
import io.github.alinourix.taski.core.domain.sync.HlcClock
import io.github.alinourix.taski.core.domain.time.Clock
import io.github.alinourix.taski.core.domain.time.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for work that outlives any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @Singleton
    fun clock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun ids(clock: Clock): IdGenerator = UuidV7Generator(clock)

    @Provides
    @Singleton
    fun hlc(@ApplicationContext context: Context, clock: Clock): HlcClock =
        PersistentHlcClock(context.getSharedPreferences("taski_device", Context.MODE_PRIVATE), clock)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TaskiDatabase =
        Room.databaseBuilder(context, TaskiDatabase::class.java, TaskiDatabase.NAME).build()

    @Provides
    @Singleton
    fun preferencesStore(@ApplicationContext context: Context): DataStore<Preferences> = context.preferencesStore

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds abstract fun tasks(impl: DefaultTaskRepository): TaskRepository
    @Binds abstract fun projects(impl: DefaultProjectRepository): ProjectRepository
    @Binds abstract fun tags(impl: DefaultTagRepository): TagRepository
    @Binds abstract fun views(impl: DefaultSavedViewRepository): SavedViewRepository
    @Binds abstract fun timers(impl: DefaultTimerRepository): TimerRepository
    @Binds abstract fun activity(impl: DefaultActivityRepository): ActivityRepository
    @Binds abstract fun preferences(impl: DataStorePreferencesRepository): PreferencesRepository
    @Binds abstract fun transfer(impl: DefaultTransferRepository): TransferRepository
    @Binds abstract fun sync(impl: NoOpSyncEngine): SyncEngine
    @Binds abstract fun reminderState(impl: io.github.alinourix.taski.core.data.repository.DefaultReminderStateRepository): io.github.alinourix.taski.core.domain.repository.ReminderStateRepository
}
