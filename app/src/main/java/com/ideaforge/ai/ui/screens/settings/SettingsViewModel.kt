package com.ideaforge.ai.ui.screens.settings

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideaforge.ai.core.cloud.TokenValidation
import com.ideaforge.ai.core.cloud.providers.GitHubActionsProvider
import com.ideaforge.ai.core.di.PreferencesManager
import com.ideaforge.ai.core.network.BuildReadiness
import com.ideaforge.ai.core.network.DiagnosticResult
import com.ideaforge.ai.core.network.NetworkDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    val themeMode: StateFlow<String> = preferencesManager.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val language: StateFlow<String> = preferencesManager.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")
    val notificationsEnabled: StateFlow<Boolean> = preferencesManager.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val githubToken: StateFlow<String> = preferencesManager.githubToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val githubRepo: StateFlow<String> = preferencesManager.githubRepo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ideaforge-cloud/builder")

    private val _tokenValidation = MutableStateFlow<TokenValidation?>(null)
    val tokenValidation: StateFlow<TokenValidation?> = _tokenValidation.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _networkResults = MutableStateFlow<List<DiagnosticResult>>(emptyList())
    val networkResults: StateFlow<List<DiagnosticResult>> = _networkResults.asStateFlow()

    private val _authResults = MutableStateFlow<List<DiagnosticResult>>(emptyList())
    val authResults: StateFlow<List<DiagnosticResult>> = _authResults.asStateFlow()

    private val _isRunningDiagnostics = MutableStateFlow(false)
    val isRunningDiagnostics: StateFlow<Boolean> = _isRunningDiagnostics.asStateFlow()

    private val _readiness = MutableStateFlow<BuildReadiness?>(null)
    val readiness: StateFlow<BuildReadiness?> = _readiness.asStateFlow()

    private val _exportText = MutableStateFlow<String?>(null)
    val exportText: StateFlow<String?> = _exportText.asStateFlow()

    fun setThemeMode(mode: String) { viewModelScope.launch { preferencesManager.setThemeMode(mode) } }
    fun setLanguage(lang: String) { viewModelScope.launch { preferencesManager.setLanguage(lang) } }
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) } }
    fun setGithubToken(token: String) {
        preferencesManager.setGithubToken(token)
        _tokenValidation.value = null
    }
    fun setGithubRepo(repo: String) { viewModelScope.launch { preferencesManager.setGithubRepo(repo) } }

    fun getOpenCodeApiKey(): String = preferencesManager.getOpenCodeApiKey()
    fun setOpenCodeApiKey(key: String) { preferencesManager.setOpenCodeApiKey(key) }

    fun validateGithubToken() {
        val token = preferencesManager.getGithubToken()
        if (token.isBlank()) {
            _tokenValidation.value = TokenValidation(valid = false, error = "No token set")
            return
        }

        viewModelScope.launch {
            _isValidating.value = true
            try {
                val validation = withContext(Dispatchers.IO) {
                    val provider = GitHubActionsProvider(token, "dummy", "dummy")
                    provider.validateToken()
                }
                _tokenValidation.value = validation
            } catch (e: Exception) {
                _tokenValidation.value = TokenValidation(
                    valid = false,
                    error = "Validation failed: ${e.message}"
                )
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun runFullDiagnostics() {
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            _networkResults.value = emptyList()
            _authResults.value = emptyList()
            _readiness.value = null
            _exportText.value = null
            try {
                val token = preferencesManager.getGithubToken()
                val apiKey = preferencesManager.getOpenCodeApiKey()
                val report = withContext(Dispatchers.IO) {
                    NetworkDiagnostics.runFullDiagnostics(getApplication(), token, apiKey)
                }
                _networkResults.value = report.networkResults
                _authResults.value = report.authResults
                _readiness.value = report.readiness
                _exportText.value = report.exportText
            } catch (e: Exception) {
                _readiness.value = BuildReadiness()
                _exportText.value = "Diagnostics crashed: ${e.message}"
            } finally {
                _isRunningDiagnostics.value = false
            }
        }
    }

    fun exportDiagnostics() {
        val text = _exportText.value ?: return
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IdeaForge Diagnostics", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
