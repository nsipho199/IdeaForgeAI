package com.ideaforge.ai.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildHistoryDao {

    @Query("SELECT * FROM build_history ORDER BY completedAt DESC")
    fun getAllBuildHistory(): Flow<List<BuildHistoryEntity>>

    @Query("SELECT * FROM build_history WHERE id = :id")
    suspend fun getBuildById(id: String): BuildHistoryEntity?

    @Query("SELECT * FROM build_history WHERE projectName LIKE '%' || :query || '%' OR idea LIKE '%' || :query || '%'")
    fun searchBuildHistory(query: String): Flow<List<BuildHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildHistoryEntity)

    @Delete
    suspend fun deleteBuild(build: BuildHistoryEntity)

    @Query("DELETE FROM build_history WHERE id = :id")
    suspend fun deleteBuildById(id: String)

    @Query("DELETE FROM build_history")
    suspend fun deleteAllBuildHistory()

    @Query("SELECT COUNT(*) FROM build_history")
    suspend fun getBuildCount(): Int

    @Query("SELECT SUM(apkSize) FROM build_history WHERE apkSize > 0")
    suspend fun getTotalStorageUsed(): Long?
}
