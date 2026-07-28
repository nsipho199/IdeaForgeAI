package com.ideaforge.ai.ui.screens.apks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideaforge.ai.domain.model.BuildHistoryItem
import com.ideaforge.ai.domain.repository.BuildRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApksViewModel @Inject constructor(
    application: Application,
    private val buildRepository: BuildRepository
) : AndroidViewModel(application) {

    val builds: StateFlow<List<BuildHistoryItem>> = buildRepository.getAllBuildHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _totalStorageUsed = MutableStateFlow(0L)
    val totalStorageUsed: StateFlow<Long> = _totalStorageUsed.asStateFlow()

    init {
        viewModelScope.launch {
            _totalStorageUsed.value = buildRepository.getTotalStorageUsed()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteBuild(id: String) {
        viewModelScope.launch {
            buildRepository.deleteBuild(id)
            _totalStorageUsed.value = buildRepository.getTotalStorageUsed()
        }
    }

    fun deleteAllBuilds() {
        viewModelScope.launch {
            buildRepository.deleteAllBuildHistory()
            _totalStorageUsed.value = 0L
        }
    }
}
