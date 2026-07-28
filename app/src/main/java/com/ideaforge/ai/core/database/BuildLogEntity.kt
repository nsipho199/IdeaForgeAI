package com.ideaforge.ai.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ideaforge.ai.core.constants.AppConstants

@Entity(tableName = AppConstants.BUILD_LOG_TABLE)
data class BuildLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String,
    val stage: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)
