package com.ideaforge.ai.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ideaforge.ai.core.build.BuildManager
import com.ideaforge.ai.core.cloud.providers.GitHubActionsProvider
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.core.di.PreferencesManager
import com.ideaforge.ai.core.network.ApiService
import com.ideaforge.ai.domain.model.BuildProgress
import com.ideaforge.ai.domain.model.BuildStage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@HiltWorker
class BuildWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_IDEA = "idea"
        const val KEY_PROJECT_NAME = "project_name"
        const val KEY_PACKAGE_NAME = "package_name"
        const val KEY_GITHUB_REPO = "github_repo"
        const val KEY_OPENCODE_API_KEY = "opencode_api_key"
        const val PROGRESS_DATA = "progress_data"
        const val WORK_NAME = "ideaforge_build"
        const val WORK_BUILD_TAG = "ideaforge_build_tag"
    }

    override suspend fun doWork(): Result {
        return try {
            val idea = inputData.getString(KEY_IDEA) ?: return Result.failure()
            val projectName = inputData.getString(KEY_PROJECT_NAME) ?: "MyApp"
            val packageName = inputData.getString(KEY_PACKAGE_NAME) ?: "com.myapp"
            val githubRepo = inputData.getString(KEY_GITHUB_REPO) ?: "ideaforge-cloud/builder"

            val githubToken = preferencesManager.getGithubToken()
            val openCodeApiKey = inputData.getString(KEY_OPENCODE_API_KEY)
                ?: preferencesManager.getOpenCodeApiKey()

            if (githubToken.isBlank()) {
                val progress = BuildProgress(
                    requestId = id.toString(),
                    stage = BuildStage.FAILED,
                    progress = 0f,
                    message = "GitHub token required",
                    error = "Set your GitHub Personal Access Token in Settings.\n\n" +
                            "Go to: github.com \u2192 Settings \u2192 Developer settings \u2192 Personal access tokens \u2192 Tokens (classic) \u2192 Generate new token\n\n" +
                            "Check 'repo' and 'workflow' scopes, then generate."
                )
                val data = workDataOf(PROGRESS_DATA to progressToJson(progress))
                setProgressAsync(data)
                return Result.failure(data)
            }

            setForegroundAsync(createForegroundInfo("Validating GitHub token...", 0))

            val (owner, repo) = githubRepo.split("/", limit = 2).let {
                if (it.size == 2) it[0] to it[1] else "ideaforge-cloud" to "builder"
            }

            val cloudProvider = GitHubActionsProvider(githubToken, owner, repo)

            val validation = withContext(Dispatchers.IO) { cloudProvider.validateToken() }
            if (!validation.valid) {
                val errorDetail = buildString {
                    appendLine("GitHub token validation failed:")
                    appendLine(validation.error ?: "Unknown error")
                    appendLine()
                    appendLine("How to fix:")
                    appendLine("1. Go to github.com \u2192 Settings \u2192 Developer settings")
                    appendLine("2. Click 'Personal access tokens' \u2192 'Tokens (classic)'")
                    appendLine("3. Generate new token (classic)")
                    appendLine("4. Check 'repo' and 'workflow' scopes")
                    appendLine("5. Generate and paste the token in Settings")
                }
                val progress = BuildProgress(
                    requestId = id.toString(),
                    stage = BuildStage.FAILED,
                    progress = 0f,
                    message = "GitHub token validation failed",
                    error = errorDetail
                )
                val data = workDataOf(PROGRESS_DATA to progressToJson(progress))
                setProgressAsync(data)
                return Result.failure(data)
            }

            val actualOwner = validation.username ?: owner

            if (openCodeApiKey.isBlank()) {
                val progress = BuildProgress(
                    requestId = id.toString(),
                    stage = BuildStage.FAILED,
                    progress = 0f,
                    message = "Gemini API key required",
                    error = "Add your Google Gemini API key in Settings.\n\n" +
                            "Get a free key at: ai.google.dev \u2192 Get API key \u2192 Create API key\n\n" +
                            "FREE: 15 requests/min, 1M tokens/day"
                )
                val data = workDataOf(PROGRESS_DATA to progressToJson(progress))
                setProgressAsync(data)
                return Result.failure(data)
            }

            val validatedProvider = GitHubActionsProvider(githubToken, actualOwner, repo)
            val buildManager = BuildManager(applicationContext, apiService, validatedProvider, openCodeApiKey)

            val result = buildManager.executeBuild(idea, projectName, packageName) { event ->
                val stage = when (event.phase) {
                    com.ideaforge.ai.core.cloud.BuildPhase.PLANNING -> BuildStage.CONNECTING
                    com.ideaforge.ai.core.cloud.BuildPhase.GENERATING -> BuildStage.GENERATING_CODE
                    com.ideaforge.ai.core.cloud.BuildPhase.ANALYZING -> BuildStage.ANALYZING
                    com.ideaforge.ai.core.cloud.BuildPhase.SEARCHING_FIXES -> BuildStage.SEARCHING_FIXES
                    com.ideaforge.ai.core.cloud.BuildPhase.CALLING_AI_REPAIR -> BuildStage.CALLING_AI_REPAIR
                    com.ideaforge.ai.core.cloud.BuildPhase.APPLYING_FIX -> BuildStage.APPLYING_FIX
                    com.ideaforge.ai.core.cloud.BuildPhase.REBUILDING -> BuildStage.REBUILDING
                    com.ideaforge.ai.core.cloud.BuildPhase.ROLLING_BACK -> BuildStage.ROLLING_BACK
                    com.ideaforge.ai.core.cloud.BuildPhase.VALIDATING -> BuildStage.UPLOADING
                    com.ideaforge.ai.core.cloud.BuildPhase.UPLOADING -> BuildStage.UPLOADING
                    com.ideaforge.ai.core.cloud.BuildPhase.QUEUED -> BuildStage.QUEUED
                    com.ideaforge.ai.core.cloud.BuildPhase.BUILDING -> BuildStage.BUILDING
                    com.ideaforge.ai.core.cloud.BuildPhase.TESTING -> BuildStage.TESTING
                    com.ideaforge.ai.core.cloud.BuildPhase.SIGNING -> BuildStage.SIGNING
                    com.ideaforge.ai.core.cloud.BuildPhase.PACKAGING -> BuildStage.PACKAGING
                    com.ideaforge.ai.core.cloud.BuildPhase.DOWNLOADING -> BuildStage.DOWNLOADING_APK
                    com.ideaforge.ai.core.cloud.BuildPhase.COMPLETED -> BuildStage.COMPLETED
                    com.ideaforge.ai.core.cloud.BuildPhase.FAILED -> BuildStage.FAILED
                }
                val progress = BuildProgress(
                    requestId = id.toString(),
                    stage = stage,
                    progress = event.progress,
                    message = event.message,
                    logs = event.logs,
                    projectDir = event.projectDir,
                    apkPath = event.apkPath,
                    error = event.error
                )
                setProgressAsync(workDataOf(PROGRESS_DATA to progressToJson(progress)))
                setForegroundAsync(createForegroundInfo(event.message, event.progress.toInt()))
            }

            if (result.isSuccess) {
                val progress = BuildProgress(
                    requestId = id.toString(),
                    stage = BuildStage.COMPLETED,
                    progress = 100f,
                    message = "Build complete!",
                    apkPath = result.getOrDefault("")
                )
                val data = workDataOf(PROGRESS_DATA to progressToJson(progress))
                setProgressAsync(data)
                Result.success(data)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                val progress = BuildProgress(
                    requestId = id.toString(),
                    stage = BuildStage.FAILED,
                    progress = 0f,
                    message = "Build failed",
                    error = error
                )
                val data = workDataOf(PROGRESS_DATA to progressToJson(progress))
                setProgressAsync(data)
                Result.failure(data)
            }
        } catch (e: Exception) {
            val error = "Unexpected error: ${e.message ?: e.javaClass.simpleName}"
            val progress = BuildProgress(
                requestId = id.toString(),
                stage = BuildStage.FAILED,
                progress = 0f,
                message = "Build crashed",
                error = error
            )
            val data = workDataOf(PROGRESS_DATA to progressToJson(progress))
            setProgressAsync(data)
            Result.failure(data)
        }
    }

    private fun progressToJson(progress: BuildProgress): String {
        return JSONObject().apply {
            put("requestId", progress.requestId)
            put("stage", progress.stage.name)
            put("progress", progress.progress.toDouble())
            put("message", progress.message)
            put("logs", progress.logs.joinToString("\n"))
            put("projectDir", progress.projectDir ?: "")
            put("apkPath", progress.apkPath ?: "")
            put("error", progress.error ?: "")
            put("retryCount", progress.retryCount)
        }.toString()
    }

    private fun createForegroundInfo(message: String, progress: Int): ForegroundInfo {
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("IdeaForge AI")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(AppConstants.BUILD_NOTIFICATION_ID, notification)
    }
}
