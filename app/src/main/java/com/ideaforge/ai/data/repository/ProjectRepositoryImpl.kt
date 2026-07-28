package com.ideaforge.ai.data.repository

import com.ideaforge.ai.core.database.ProjectDao
import com.ideaforge.ai.data.mapper.ProjectMapper
import com.ideaforge.ai.domain.model.Project
import com.ideaforge.ai.domain.model.ProjectStatus
import com.ideaforge.ai.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { ProjectMapper.toDomain(it) }
        }
    }

    override suspend fun getProjectById(id: String): Project? {
        return projectDao.getProjectById(id)?.let { ProjectMapper.toDomain(it) }
    }

    override fun searchProjects(query: String): Flow<List<Project>> {
        return projectDao.searchProjects(query).map { entities ->
            entities.map { ProjectMapper.toDomain(it) }
        }
    }

    override suspend fun saveProject(project: Project) {
        projectDao.insertProject(ProjectMapper.toEntity(project))
    }

    override suspend fun updateProject(project: Project) {
        projectDao.updateProject(ProjectMapper.toEntity(project))
    }

    override suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    override suspend fun deleteAllProjects() {
        projectDao.deleteAllProjects()
    }

    override suspend fun getProjectCount(): Int {
        return projectDao.getProjectCount()
    }

    override fun getActiveBuilds(): Flow<List<Project>> {
        return projectDao.getActiveBuilds().map { entities ->
            entities.map { ProjectMapper.toDomain(it) }
        }
    }

    override suspend fun createProject(
        name: String,
        description: String,
        idea: String
    ): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            idea = idea,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = ProjectStatus.DRAFT
        )
        projectDao.insertProject(ProjectMapper.toEntity(project))
        return project
    }

    override suspend fun duplicateProject(id: String): Project? {
        val original = projectDao.getProjectById(id) ?: return null
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = ProjectStatus.DRAFT.name,
            apkPath = null,
            apkSize = 0L
        )
        projectDao.insertProject(duplicate)
        return ProjectMapper.toDomain(duplicate)
    }
}
