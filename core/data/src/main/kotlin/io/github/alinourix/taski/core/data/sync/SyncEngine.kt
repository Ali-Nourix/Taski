package io.github.alinourix.taski.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
    /** No account: everything stays on this device, which is a complete way to use the app. */
    data object Disabled : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * The seam a Supabase sync engine will fill. It pushes the outbox, pulls rows
 * newer than its `server_updated_at` cursor, merges them with
 * [io.github.alinourix.taski.core.domain.sync.ConflictRules], and writes them
 * back through Room, where every screen's Flow picks them up.
 */
interface SyncEngine {
    val state: StateFlow<SyncState>

    /** Push and pull now, if there is an account. */
    suspend fun requestSync()

    /** First sign-in: claim this device's rows for [userId], then sync. */
    suspend fun onSignedIn(userId: String)

    suspend fun onSignedOut()
}

/** Bound today: the app has no network code, so there is nothing to do. */
@Singleton
class NoOpSyncEngine @Inject constructor() : SyncEngine {
    private val mutableState = MutableStateFlow<SyncState>(SyncState.Disabled)
    override val state: StateFlow<SyncState> = mutableState.asStateFlow()

    override suspend fun requestSync() = Unit

    override suspend fun onSignedIn(userId: String) = Unit

    override suspend fun onSignedOut() = Unit
}
