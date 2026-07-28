package com.ideaforge.ai.domain.repository

import com.ideaforge.ai.domain.model.BuildHistoryItem
import com.ideaforge.ai.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow

interface BuildRepository {
    fun getAllBuildHistory(): Flow<List<BuildHistoryItem>>
    suspend fun getBuildById(id: String): BuildHistoryItem?
    fun searchBuildHistory(query: String): Flow<List<BuildHistoryItem>>
    suspend fun saveBuild(build: BuildHistoryItem)
    suspend fun deleteBuild(id: String)
    suspend fun deleteAllBuildHistory()
    suspend fun getBuildCount(): Int
    suspend fun getTotalStorageUsed(): Long
    suspend fun addBuildLog(requestId: String, stage: String, message: String, isError: Boolean = false)
    fun getBuildLogs(requestId: String): Flow<List<String>>
    suspend fun clearBuildLogs(requestId: String)
    suspend fun clearAllLogs()
    fun createBuildHistoryItem(projectName: String, idea: String, status: ProjectStatus): BuildHistoryItem
}
