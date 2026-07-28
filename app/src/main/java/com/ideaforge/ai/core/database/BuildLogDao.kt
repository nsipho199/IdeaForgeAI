package com.ideaforge.ai.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildLogDao {

    @Query("SELECT * FROM build_logs WHERE requestId = :requestId ORDER BY timestamp ASC")
    fun getLogsForRequest(requestId: String): Flow<List<BuildLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BuildLogEntity)

    @Query("DELETE FROM build_logs WHERE requestId = :requestId")
    suspend fun deleteLogsForRequest(requestId: String)

    @Query("DELETE FROM build_logs")
    suspend fun deleteAllLogs()
}
