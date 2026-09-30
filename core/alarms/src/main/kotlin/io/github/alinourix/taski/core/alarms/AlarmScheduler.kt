package io.github.alinourix.taski.core.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager, used exactly when the user allowed exact alarms and within a
 * short window otherwise, so a reminder is never lost for want of a permission.
 */
@Singleton
class AlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(requestCode: Int, atMillis: Long, intent: Intent) {
        val pending = pendingIntent(requestCode, intent)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, atMillis, WINDOW_MILLIS, pending)
        }
    }

    fun cancel(requestCode: Int, intent: Intent) {
        alarms.cancel(pendingIntent(requestCode, intent))
    }

    private fun pendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val WINDOW_MILLIS = 5 * 60_000L
    }
}
