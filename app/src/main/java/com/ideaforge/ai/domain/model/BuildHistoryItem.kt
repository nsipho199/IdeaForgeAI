package com.ideaforge.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BuildHistoryItem(
    val id: String,
    val projectName: String,
    val idea: String,
    val status: ProjectStatus,
    val apkPath: String? = null,
    val apkSize: Long = 0L,
    val buildDuration: Long = 0L,
    val completedAt: Long,
    val errorMessage: String? = null,
    val buildLogs: List<String> = emptyList()
)
