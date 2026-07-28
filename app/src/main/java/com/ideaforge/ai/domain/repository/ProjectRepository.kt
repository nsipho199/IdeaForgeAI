package com.ideaforge.ai.domain.repository

import com.ideaforge.ai.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    suspend fun getProjectById(id: String): Project?
    fun searchProjects(query: String): Flow<List<Project>>
    suspend fun saveProject(project: Project)
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(id: String)
    suspend fun deleteAllProjects()
    suspend fun getProjectCount(): Int
    fun getActiveBuilds(): Flow<List<Project>>
    suspend fun createProject(name: String, description: String, idea: String): Project
    suspend fun duplicateProject(id: String): Project?
}
