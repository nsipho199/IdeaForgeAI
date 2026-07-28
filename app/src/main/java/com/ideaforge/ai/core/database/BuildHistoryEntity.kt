package com.ideaforge.ai.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ideaforge.ai.core.constants.AppConstants

@Entity(tableName = AppConstants.BUILD_HISTORY_TABLE)
data class BuildHistoryEntity(
    @PrimaryKey
    val id: String,
    val projectName: String,
    val idea: String,
    val status: String,
    val apkPath: String? = null,
    val apkSize: Long = 0L,
    val buildDuration: Long = 0L,
    val completedAt: Long,
    val errorMessage: String? = null,
    val buildLogs: String = ""
)
