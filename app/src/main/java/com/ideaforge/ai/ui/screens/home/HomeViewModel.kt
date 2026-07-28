package com.ideaforge.ai.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideaforge.ai.core.cloud.TokenValidation
import com.ideaforge.ai.core.cloud.providers.GitHubActionsProvider
import com.ideaforge.ai.core.di.PreferencesManager
import com.ideaforge.ai.domain.model.BuildHistoryItem
import com.ideaforge.ai.domain.model.Project
import com.ideaforge.ai.domain.repository.BuildRepository
import com.ideaforge.ai.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val _ideaText = MutableStateFlow("")
    val ideaText: StateFlow<String> = _ideaText.asStateFlow()

    private val _recentIdeas = MutableStateFlow<List<String>>(emptyList())
    val recentIdeas: StateFlow<List<String>> = _recentIdeas.asStateFlow()

    val recentProjects: StateFlow<List<Project>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentBuilds: StateFlow<List<BuildHistoryItem>> = buildRepository.getAllBuildHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasGitHubToken: StateFlow<Boolean> = preferencesManager.githubToken
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasOpenCodeApiKey: StateFlow<Boolean> = preferencesManager.openCodeApiKey
        .map { key ->
            val blank = key.isBlank()
            android.util.Log.d("HomeVM", "openCodeApiKey check: key=${if (blank) "MISSING" else "PRESENT"} (length=${key.length})")
            if (blank) android.util.Log.d("HomeVM", "  -> Reason: key is null or blank")
            else android.util.Log.d("HomeVM", "  -> Reason: key is valid (${key.take(4)}...${key.takeLast(4)})")
            !blank
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _githubUsername = MutableStateFlow<String?>(null)
    val githubUsername: StateFlow<String?> = _githubUsername.asStateFlow()

    private val _tokenError = MutableStateFlow<String?>(null)
    val tokenError: StateFlow<String?> = _tokenError.asStateFlow()

    init {
        validateToken()
    }

    fun refreshApiKeyStatus() {
        val key = preferencesManager.getOpenCodeApiKey()
        val blank = key.isBlank()
        android.util.Log.d("HomeVM", "refreshApiKeyStatus: key=${if (blank) "MISSING" else "PRESENT"} (length=${key.length})")
        if (!blank) android.util.Log.d("HomeVM", "  -> Key found: ${key.take(4)}...${key.takeLast(4)}")
    }

    fun validateToken() {
        val token = preferencesManager.getGithubToken()
        if (token.isBlank()) {
            _githubUsername.value = null
            _tokenError.value = null
            return
        }
        viewModelScope.launch {
            try {
                val validation = withContext(Dispatchers.IO) {
                    val provider = GitHubActionsProvider(token, "dummy", "dummy")
                    provider.validateToken()
                }
                if (validation.valid) {
                    _githubUsername.value = validation.username
                    _tokenError.value = null
                } else {
                    _githubUsername.value = null
                    _tokenError.value = validation.error
                }
            } catch (e: Exception) {
                _githubUsername.value = null
                _tokenError.value = "Connection failed: ${e.message}"
            }
        }
    }

    fun updateIdeaText(text: String) {
        _ideaText.value = text
    }

    fun addRecentIdea(idea: String) {
        val current = _recentIdeas.value.toMutableList()
        current.remove(idea)
        current.add(0, idea)
        if (current.size > 10) current.removeLast()
        _recentIdeas.value = current
    }

    fun useRecentIdea(idea: String) {
        _ideaText.value = idea
    }

    fun clearIdea() {
        _ideaText.value = ""
    }

    fun deleteRecentIdea(idea: String) {
        _recentIdeas.value = _recentIdeas.value.filter { it != idea }
    }
}
