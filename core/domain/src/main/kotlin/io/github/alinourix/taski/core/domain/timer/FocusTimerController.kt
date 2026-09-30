package io.github.alinourix.taski.core.domain.timer

import kotlinx.coroutines.flow.Flow

/**
 * Starts and steers the one focus timer this device runs. The implementation
 * keeps the countdown in local storage, shows it in a notification, sets the
 * alarm for its end, and records a session when it stops.
 */
interface FocusTimerController {
    val active: Flow<FocusTimerState?>

    /** Starts a countdown on [taskId], stopping (and recording) any other one first. */
    suspend fun start(taskId: String, minutes: Int)
    suspend fun pause()
    suspend fun resume()
    suspend fun addMinutes(minutes: Int)
    /** Back to the full duration, paused. */
    suspend fun reset()
    /** Ends the countdown and records the time spent. */
    suspend fun stop()
}
