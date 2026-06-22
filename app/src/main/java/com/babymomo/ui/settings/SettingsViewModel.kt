package com.babymomo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.core.common.AppSettings
import com.babymomo.core.common.SettingsRepository
import com.babymomo.core.llm.RemoteLlmProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SettingsViewModel — bridges the persisted SettingsRepository with the live
 * RemoteLlmProvider.
 *
 * Every time the user changes a remote-provider field, this ViewModel:
 *   1. Persists it to DataStore (survives app restart)
 *   2. Calls RemoteLlmProvider.configure() (takes effect immediately)
 *
 * This fixes the v0.3 bug where the API key was ephemeral Compose state that
 * was never persisted and never applied.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val remoteProvider: RemoteLlmProvider
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    /** Called when ANY remote field changes. Persists + applies immediately. */
    fun updateRemoteConfig(enabled: Boolean, baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            repo.updateRemoteConfig(enabled, baseUrl, apiKey, model)
            // Apply to the live provider immediately — no restart needed
            if (enabled && apiKey.isNotBlank()) {
                remoteProvider.configure(baseUrl, apiKey, model)
            }
        }
    }

    fun setInternetEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setInternetEnabled(enabled) }
    }

    fun setExtractionEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setExtractionEnabled(enabled) }
    }

    fun setCriticEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setCriticEnabled(enabled) }
    }
}
