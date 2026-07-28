package com.ideaforge.ai.core.di

import android.content.Context
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.core.security.SecureTokenStorage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = AppConstants.DATASTORE_NAME)

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureTokenStorage: SecureTokenStorage
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey(AppConstants.KEY_THEME_MODE)
        val LANGUAGE = stringPreferencesKey(AppConstants.KEY_LANGUAGE)
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey(AppConstants.KEY_NOTIFICATIONS_ENABLED)
        val AUTO_FIX_ERRORS = booleanPreferencesKey(AppConstants.KEY_AUTO_FIX_ERRORS)
        val MAX_RETRIES = intPreferencesKey(AppConstants.KEY_MAX_RETRIES)
        val CODE_QUALITY = stringPreferencesKey(AppConstants.KEY_CODE_QUALITY)
        val GITHUB_REPO = stringPreferencesKey("github_repo")
    }

    private val _githubToken = MutableStateFlow(secureTokenStorage.getGitHubToken())
    private val _openCodeApiKey = MutableStateFlow(secureTokenStorage.getOpenCodeApiKey())

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val autoFixErrors: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_FIX_ERRORS] ?: true }
    val maxRetries: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_RETRIES] ?: 3 }
    val codeQuality: Flow<String> = context.dataStore.data.map { it[Keys.CODE_QUALITY] ?: "standard" }
    val githubRepo: Flow<String> = context.dataStore.data.map { it[Keys.GITHUB_REPO] ?: "ideaforge-cloud/builder" }
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()
    val openCodeApiKey: StateFlow<String> = _openCodeApiKey.asStateFlow()

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoFixErrors(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FIX_ERRORS] = enabled }
    }

    suspend fun setMaxRetries(retries: Int) {
        context.dataStore.edit { it[Keys.MAX_RETRIES] = retries }
    }

    suspend fun setCodeQuality(quality: String) {
        context.dataStore.edit { it[Keys.CODE_QUALITY] = quality }
    }

    fun setGithubToken(token: String) {
        secureTokenStorage.setGitHubToken(token)
        _githubToken.value = token
    }

    fun getGithubToken(): String = secureTokenStorage.getGitHubToken()

    fun getOpenCodeApiKey(): String = secureTokenStorage.getOpenCodeApiKey()

    fun setOpenCodeApiKey(key: String) {
        secureTokenStorage.setOpenCodeApiKey(key)
        _openCodeApiKey.value = key
    }

    suspend fun setGithubRepo(repo: String) {
        context.dataStore.edit { it[Keys.GITHUB_REPO] = repo }
    }
}
