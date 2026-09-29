package io.github.alinourix.taski.core.domain.timer

/**
 * A focus-timer countdown, computed from wall-clock stamps rather than counted
 * down in memory, so it survives the app being killed: the plugin's model.
 */
data class FocusTimerState(
    val taskId: String,
    val plannedSeconds: Int,
    /** Seconds banked by earlier runs, before the current one. */
    val bankedSeconds: Long = 0,
    /** Epoch millis the current run started, or null while paused. */
    val runningSince: Long? = null,
    /** Epoch millis of the first run in this countdown, for the session record. */
    val startedAt: Long? = null,
) {
    val isRunning: Boolean get() = runningSince != null

    fun spentSeconds(now: Long): Long = bankedSeconds + (runningSince?.let { (now - it).coerceAtLeast(0) / 1000 } ?: 0)

    fun remainingSeconds(now: Long): Long = plannedSeconds - spentSeconds(now)

    fun isFinished(now: Long): Boolean = remainingSeconds(now) <= 0

    /** Epoch millis at which a running countdown reaches zero. */
    fun endsAt(): Long? = runningSince?.let { it + (plannedSeconds - bankedSeconds) * 1000 }

    fun start(now: Long): FocusTimerState =
        if (isRunning) this else copy(runningSince = now, startedAt = startedAt ?: now)

    fun pause(now: Long): FocusTimerState =
        if (!isRunning) this else copy(bankedSeconds = spentSeconds(now), runningSince = null)

    fun addMinutes(minutes: Int): FocusTimerState = copy(plannedSeconds = plannedSeconds + minutes * 60)

    fun fraction(now: Long): Float =
        if (plannedSeconds <= 0) 1f else (spentSeconds(now).toFloat() / plannedSeconds).coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_MINUTES = 25

        /** `mm:ss`, or `h:mm:ss` once an hour is involved; negative once overrun. */
        fun formatClock(seconds: Long): String {
            val sign = if (seconds < 0) "-" else ""
            val abs = kotlin.math.abs(seconds)
            val h = abs / 3600
            val m = (abs % 3600) / 60
            val s = abs % 60
            return if (h > 0) "%s%d:%02d:%02d".format(sign, h, m, s) else "%s%d:%02d".format(sign, m, s)
        }
    }
}
