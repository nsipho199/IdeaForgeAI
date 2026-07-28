package com.ideaforge.ai.data.repository

import com.ideaforge.ai.core.database.BuildHistoryDao
import com.ideaforge.ai.core.database.BuildLogDao
import com.ideaforge.ai.core.database.BuildLogEntity
import com.ideaforge.ai.data.mapper.BuildHistoryMapper
import com.ideaforge.ai.domain.model.BuildHistoryItem
import com.ideaforge.ai.domain.repository.BuildRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildRepositoryImpl @Inject constructor(
    private val buildHistoryDao: BuildHistoryDao,
    private val buildLogDao: BuildLogDao
) : BuildRepository {

    override fun getAllBuildHistory(): Flow<List<BuildHistoryItem>> {
        return buildHistoryDao.getAllBuildHistory().map { entities ->
            entities.map { BuildHistoryMapper.toDomain(it) }
        }
    }

    override suspend fun getBuildById(id: String): BuildHistoryItem? {
        return buildHistoryDao.getBuildById(id)?.let { BuildHistoryMapper.toDomain(it) }
    }

    override fun searchBuildHistory(query: String): Flow<List<BuildHistoryItem>> {
        return buildHistoryDao.searchBuildHistory(query).map { entities ->
            entities.map { BuildHistoryMapper.toDomain(it) }
        }
    }

    override suspend fun saveBuild(build: BuildHistoryItem) {
        buildHistoryDao.insertBuild(BuildHistoryMapper.toEntity(build))
    }

    override suspend fun deleteBuild(id: String) {
        buildHistoryDao.deleteBuildById(id)
    }

    override suspend fun deleteAllBuildHistory() {
        buildHistoryDao.deleteAllBuildHistory()
    }

    override suspend fun getBuildCount(): Int {
        return buildHistoryDao.getBuildCount()
    }

    override suspend fun getTotalStorageUsed(): Long {
        return buildHistoryDao.getTotalStorageUsed() ?: 0L
    }

    override suspend fun addBuildLog(
        requestId: String,
        stage: String,
        message: String,
        isError: Boolean
    ) {
        buildLogDao.insertLog(
            BuildLogEntity(
                requestId = requestId,
                stage = stage,
                message = message,
                isError = isError
            )
        )
    }

    override fun getBuildLogs(requestId: String): Flow<List<String>> {
        return buildLogDao.getLogsForRequest(requestId).map { logs ->
            logs.map { "${it.stage}: ${it.message}" }
        }
    }

    override suspend fun clearBuildLogs(requestId: String) {
        buildLogDao.deleteLogsForRequest(requestId)
    }

    override suspend fun clearAllLogs() {
        buildLogDao.deleteAllLogs()
    }

    override fun createBuildHistoryItem(
        projectName: String,
        idea: String,
        status: com.ideaforge.ai.domain.model.ProjectStatus
    ): BuildHistoryItem {
        return BuildHistoryItem(
            id = UUID.randomUUID().toString(),
            projectName = projectName,
            idea = idea,
            status = status,
            completedAt = System.currentTimeMillis()
        )
    }
}
