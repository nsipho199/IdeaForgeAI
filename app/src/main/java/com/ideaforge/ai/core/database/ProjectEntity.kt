package com.ideaforge.ai.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ideaforge.ai.core.constants.AppConstants

@Entity(tableName = AppConstants.PROJECT_TABLE)
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val idea: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val apkPath: String? = null,
    val apkSize: Long = 0L,
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val packageName: String = "",
    val buildLogs: String = ""
)
