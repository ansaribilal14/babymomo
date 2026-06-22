package com.babymomo.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SettingsRepository — persists app settings using Jetpack DataStore.
 *
 * The critical settings are the remote LLM provider config (URL, API key, model name,
 * enabled flag). In v0.3 these were stored as ephemeral Compose state in SettingsScreen
 * — meaning the user's API key was lost on every navigation away from the screen AND
 * never applied to RemoteLlmProvider. This repository fixes both issues: values persist
 * across app restarts, and SettingsViewModel applies them to RemoteLlmProvider on change.
 *
 * Defaults: Groq is pre-configured as the default remote provider (free, fast, works
 * with Llama 3.3). The user just needs to paste a free API key from console.groq.com.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "babymomo_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    companion object {
        val REMOTE_ENABLED = booleanPreferencesKey("remote_enabled")
        val REMOTE_BASE_URL = stringPreferencesKey("remote_base_url")
        val REMOTE_API_KEY = stringPreferencesKey("remote_api_key")
        val REMOTE_MODEL = stringPreferencesKey("remote_model")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val INTERNET_ENABLED = booleanPreferencesKey("internet_enabled")
        val EXTRACTION_ENABLED = booleanPreferencesKey("extraction_enabled")
        val CRITIC_ENABLED = booleanPreferencesKey("critic_enabled")

        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { prefs ->
        AppSettings(
            remoteEnabled = prefs[REMOTE_ENABLED] ?: false,
            remoteBaseUrl = prefs[REMOTE_BASE_URL] ?: DEFAULT_BASE_URL,
            remoteApiKey = prefs[REMOTE_API_KEY] ?: "",
            remoteModel = prefs[REMOTE_MODEL] ?: DEFAULT_MODEL,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
            internetEnabled = prefs[INTERNET_ENABLED] ?: false,
            extractionEnabled = prefs[EXTRACTION_ENABLED] ?: true,
            criticEnabled = prefs[CRITIC_ENABLED] ?: true
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        ctx.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun updateRemoteConfig(enabled: Boolean, baseUrl: String, apiKey: String, model: String) {
        ctx.dataStore.edit { prefs ->
            prefs[REMOTE_ENABLED] = enabled
            prefs[REMOTE_BASE_URL] = baseUrl
            prefs[REMOTE_API_KEY] = apiKey
            prefs[REMOTE_MODEL] = model
        }
    }

    suspend fun setInternetEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[INTERNET_ENABLED] = enabled }
    }

    suspend fun setExtractionEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[EXTRACTION_ENABLED] = enabled }
    }

    suspend fun setCriticEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[CRITIC_ENABLED] = enabled }
    }
}

data class AppSettings(
    val remoteEnabled: Boolean = false,
    val remoteBaseUrl: String = SettingsRepository.DEFAULT_BASE_URL,
    val remoteApiKey: String = "",
    val remoteModel: String = SettingsRepository.DEFAULT_MODEL,
    val onboardingCompleted: Boolean = false,
    val internetEnabled: Boolean = false,
    val extractionEnabled: Boolean = true,
    val criticEnabled: Boolean = true
)

/**
 * Hilt entry point for accessing SettingsRepository from a Composable
 * (where we can't use @Inject). Used by BabymomoApp() to check onboarding state.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface OnboardingEntryPoint {
    fun settingsRepository(): SettingsRepository
}

