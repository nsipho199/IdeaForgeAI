package com.ideaforge.ai.core.cloud

interface CloudBuildProvider {
    val name: String
    suspend fun validateToken(): TokenValidation
    suspend fun ensureRepository(): Result<Unit>
    suspend fun pushFiles(projectDir: String, projectName: String, branch: String): Result<String>
    suspend fun pushFix(projectDir: String, projectName: String, branch: String, changedFiles: Map<String, String>): Result<Unit>
    suspend fun startBuild(branch: String): Result<String>
    suspend fun getBuildStatus(buildId: String): Result<CloudBuildStatus>
    suspend fun getBuildLogs(buildId: String): Result<String>
    suspend fun downloadApk(buildId: String, destPath: String): Result<String>
    suspend fun cancelBuild(buildId: String): Result<Unit>
    suspend fun cleanup(branch: String): Result<Unit>
    fun isTransientError(error: Exception): Boolean
}
