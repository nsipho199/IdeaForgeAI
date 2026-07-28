package com.ideaforge.ai.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

    @Query("SELECT * FROM projects WHERE status = 'BUILDING'")
    fun getActiveBuilds(): Flow<List<ProjectEntity>>
}
