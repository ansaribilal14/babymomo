package com.babymomo.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.core.common.AppSettings
import com.babymomo.core.common.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainViewModel — provides app-level settings to the root composable.
 *
 * This replaces the EntryPointAccessors pattern that caused crashes on some devices.
 * Using @HiltViewModel + hiltViewModel() is the safe, Google-recommended way to
 * access Hilt-provided dependencies from a Composable.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    fun setOnboardingCompleted() {
        viewModelScope.launch { settingsRepo.setOnboardingCompleted(true) }
    }
}
