package io.github.alinourix.taski.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alinourix.taski.core.domain.UserPreferences
import io.github.alinourix.taski.core.domain.model.Task
import io.github.alinourix.taski.core.domain.reminder.DigestSettings
import io.github.alinourix.taski.core.domain.repository.ImportResult
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import io.github.alinourix.taski.core.domain.repository.TransferRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val transfer: TransferRepository,
    private val tasks: TaskRepository,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences?> = preferences.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val trash: StateFlow<List<Task>> = tasks.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch { preferences.update(transform) }
    }

    fun updateDigest(transform: (DigestSettings) -> DigestSettings) = update { it.copy(digest = transform(it.digest)) }

    fun import(name: String, content: String, onDone: (ImportResult) -> Unit) {
        viewModelScope.launch { onDone(transfer.importMarkdown(name, content)) }
    }

    fun export(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(transfer.exportMarkdown()) }
    }

    fun restore(id: String) {
        viewModelScope.launch { tasks.restore(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { tasks.purgeTrash() }
    }
}
