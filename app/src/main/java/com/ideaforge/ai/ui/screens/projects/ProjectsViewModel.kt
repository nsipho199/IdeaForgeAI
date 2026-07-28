package com.ideaforge.ai.ui.screens.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideaforge.ai.domain.model.Project
import com.ideaforge.ai.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    application: Application,
    private val projectRepository: ProjectRepository
) : AndroidViewModel(application) {

    val projects: StateFlow<List<Project>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(id)
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            projectRepository.duplicateProject(id)
        }
    }

    fun deleteAllProjects() {
        viewModelScope.launch {
            projectRepository.deleteAllProjects()
        }
    }
}
