package io.github.alinourix.taski.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.alinourix.taski.core.data.db.dao.HistoryDao
import io.github.alinourix.taski.core.data.db.dao.LinkDao
import io.github.alinourix.taski.core.data.db.dao.OutboxDao
import io.github.alinourix.taski.core.data.db.dao.ProjectDao
import io.github.alinourix.taski.core.data.db.dao.ReminderStateDao
import io.github.alinourix.taski.core.data.db.dao.SavedViewDao
import io.github.alinourix.taski.core.data.db.dao.TagDao
import io.github.alinourix.taski.core.data.db.dao.TaskDao
import io.github.alinourix.taski.core.data.db.dao.TimerStateDao
import io.github.alinourix.taski.core.data.db.entity.ActivityEntity
import io.github.alinourix.taski.core.data.db.entity.OutboxEntity
import io.github.alinourix.taski.core.data.db.entity.ProjectEntity
import io.github.alinourix.taski.core.data.db.entity.ReminderStateEntity
import io.github.alinourix.taski.core.data.db.entity.SavedViewEntity
import io.github.alinourix.taski.core.data.db.entity.TagEntity
import io.github.alinourix.taski.core.data.db.entity.TaskCompletionEntity
import io.github.alinourix.taski.core.data.db.entity.TaskDependencyEntity
import io.github.alinourix.taski.core.data.db.entity.TaskEntity
import io.github.alinourix.taski.core.data.db.entity.TaskFtsEntity
import io.github.alinourix.taski.core.data.db.entity.TaskTagEntity
import io.github.alinourix.taski.core.data.db.entity.TimerSessionEntity
import io.github.alinourix.taski.core.data.db.entity.TimerStateEntity

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        TagEntity::class,
        SavedViewEntity::class,
        TaskTagEntity::class,
        TaskDependencyEntity::class,
        TaskCompletionEntity::class,
        TimerSessionEntity::class,
        ActivityEntity::class,
        OutboxEntity::class,
        ReminderStateEntity::class,
        TimerStateEntity::class,
        TaskFtsEntity::class,
    ],
)
abstract class TaskiDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun tagDao(): TagDao
    abstract fun savedViewDao(): SavedViewDao
    abstract fun linkDao(): LinkDao
    abstract fun historyDao(): HistoryDao
    abstract fun outboxDao(): OutboxDao
    abstract fun reminderStateDao(): ReminderStateDao
    abstract fun timerStateDao(): TimerStateDao

    companion object {
        const val NAME = "taski.db"
    }
}
