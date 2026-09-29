package io.github.alinourix.taski

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.alarms.DigestRunner
import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val digest: DigestRunner,
    val clock: Clock,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences?> = preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLastTab(tab: String) {
        viewModelScope.launch { preferences.update { if (it.lastTab == tab) it else it.copy(lastTab = tab) } }
    }

    fun sendDigest(onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(digest.fire()) }
    }
}
