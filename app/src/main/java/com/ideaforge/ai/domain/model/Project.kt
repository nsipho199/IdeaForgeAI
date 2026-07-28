package com.ideaforge.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val idea: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ProjectStatus,
    val apkPath: String? = null,
    val apkSize: Long = 0L,
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val packageName: String = "",
    val buildLogs: List<String> = emptyList()
)

@Serializable
enum class ProjectStatus {
    DRAFT,
    BUILDING,
    BUILT,
    FAILED
}
