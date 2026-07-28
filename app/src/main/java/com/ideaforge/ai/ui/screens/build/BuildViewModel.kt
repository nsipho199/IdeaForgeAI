package com.ideaforge.ai.ui.screens.build

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import androidx.work.*
import com.ideaforge.ai.core.cloud.providers.GitHubActionsProvider
import com.ideaforge.ai.core.di.PreferencesManager
import com.ideaforge.ai.domain.model.BuildProgress
import com.ideaforge.ai.domain.model.BuildStage
import com.ideaforge.ai.worker.BuildWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _progress = MutableStateFlow<BuildProgress?>(null)
    val progress: StateFlow<BuildProgress?> = _progress.asStateFlow()

    var projectDir by mutableStateOf<String?>(null)
        private set

    var apkPath by mutableStateOf<String?>(null)
        private set

    var buildLogs by mutableStateOf<List<String>>(emptyList())
        private set

    private var workManager: WorkManager? = null
    private var workObserver: androidx.lifecycle.Observer<WorkInfo?>? = null
    private var observedLiveData: LiveData<WorkInfo?>? = null
    var isBuilding = false
        private set

    fun startBuild(context: Context, idea: String) {
        if (isBuilding) return
        isBuilding = true
        workManager = WorkManager.getInstance(context)

        viewModelScope.launch {
            val githubToken = preferencesManager.getGithubToken()

            if (githubToken.isBlank()) {
                _progress.value = BuildProgress(
                    requestId = "",
                    stage = BuildStage.FAILED,
                    progress = 0f,
                    message = "GitHub token required",
                    error = "Set your GitHub Personal Access Token in Settings.\n\n" +
                            "Go to: github.com \u2192 Settings \u2192 Developer settings \u2192 Personal access tokens \u2192 Tokens (classic) \u2192 Generate new token\n\n" +
                            "Check 'repo' and 'workflow' scopes, then generate."
                )
                isBuilding = false
                return@launch
            }

            _progress.value = BuildProgress(
                requestId = "",
                stage = BuildStage.CONNECTING,
                progress = 3f,
                message = "Validating GitHub token..."
            )

            val validation = withContext(Dispatchers.IO) {
                try {
                    val provider = GitHubActionsProvider(githubToken, "dummy", "dummy")
                    provider.validateToken()
                } catch (e: Exception) {
                    com.ideaforge.ai.core.cloud.TokenValidation(
                        valid = false,
                        error = "Connection failed: ${e.message}"
                    )
                }
            }

            if (!validation.valid) {
                _progress.value = BuildProgress(
                    requestId = "",
                    stage = BuildStage.FAILED,
                    progress = 0f,
                    message = "GitHub token validation failed",
                    error = validation.error ?: "Invalid token"
                )
                isBuilding = false
                return@launch
            }

            val projectName = extractProjectName(idea)
            val packageName = generatePackageName(projectName)
            val actualOwner = validation.username ?: "ideaforge-cloud"
            val githubRepo = "$actualOwner/builder"
            val openCodeApiKey = preferencesManager.getOpenCodeApiKey()

            val inputData = workDataOf(
                BuildWorker.KEY_IDEA to idea,
                BuildWorker.KEY_PROJECT_NAME to projectName,
                BuildWorker.KEY_PACKAGE_NAME to packageName,
                BuildWorker.KEY_GITHUB_REPO to githubRepo,
                BuildWorker.KEY_OPENCODE_API_KEY to openCodeApiKey
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val buildRequest = OneTimeWorkRequestBuilder<BuildWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(BuildWorker.WORK_BUILD_TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            workManager?.enqueueUniqueWork(
                BuildWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildRequest
            )

            _progress.value = BuildProgress(
                requestId = buildRequest.id.toString(),
                stage = BuildStage.CONNECTING,
                progress = 5f,
                message = "Starting build as ${validation.username}..."
            )

            removeObserver()

            val liveData = workManager?.getWorkInfoByIdLiveData(buildRequest.id) ?: return@launch
            val observer = object : androidx.lifecycle.Observer<WorkInfo?> {
                override fun onChanged(value: WorkInfo?) {
                    if (value == null) return

                    when (value.state) {
                        WorkInfo.State.RUNNING -> {
                            val progressData = value.progress.getString(BuildWorker.PROGRESS_DATA)
                            if (progressData != null) {
                                onBuildProgress(progressData)
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val progressData = value.outputData.getString(BuildWorker.PROGRESS_DATA)
                            if (progressData != null) {
                                onBuildProgress(progressData)
                            }
                            onBuildComplete()
                            isBuilding = false
                            removeObserver()
                        }
                        WorkInfo.State.FAILED -> {
                            val progressData = value.outputData.getString(BuildWorker.PROGRESS_DATA)
                            if (progressData != null) {
                                onBuildProgress(progressData)
                            }
                            if (_progress.value?.stage != BuildStage.FAILED) {
                                onBuildFailed()
                            }
                            isBuilding = false
                            removeObserver()
                        }
                        WorkInfo.State.CANCELLED -> {
                            _progress.value = _progress.value?.copy(
                                stage = BuildStage.FAILED,
                                message = "Build cancelled"
                            )
                            isBuilding = false
                            removeObserver()
                        }
                        else -> {}
                    }
                }
            }
            workObserver = observer
            observedLiveData = liveData
            liveData.observeForever(observer)
        }
    }

    private fun removeObserver() {
        workObserver?.let { observer ->
            observedLiveData?.removeObserver(observer)
        }
        workObserver = null
        observedLiveData = null
    }

    fun cancelBuild() {
        workManager?.cancelUniqueWork(BuildWorker.WORK_NAME)
        isBuilding = false
        removeObserver()
    }

    private fun onBuildProgress(workData: String?) {
        if (workData == null) return
        try {
            val json = JSONObject(workData)
            val stage = try {
                BuildStage.valueOf(json.getString("stage"))
            } catch (e: Exception) {
                BuildStage.CONNECTING
            }
            val progress = BuildProgress(
                requestId = json.optString("requestId", ""),
                stage = stage,
                progress = json.optDouble("progress", 0.0).toFloat(),
                message = json.optString("message", ""),
                logs = json.optString("logs", "").split("\n").filter { it.isNotBlank() },
                projectDir = json.optString("projectDir", "").ifBlank { null },
                apkPath = json.optString("apkPath", "").ifBlank { null },
                error = json.optString("error", "").ifBlank { null },
                retryCount = json.optInt("retryCount", 0)
            )
            _progress.value = progress
            progress.projectDir?.let { projectDir = it }
            progress.apkPath?.let { apkPath = it }
            buildLogs = progress.logs
        } catch (e: Exception) {
            _progress.value = _progress.value?.copy(
                stage = BuildStage.FAILED,
                message = "Failed to parse progress",
                error = e.message
            )
        }
    }

    private fun onBuildComplete() {
        _progress.value = _progress.value?.copy(
            stage = BuildStage.COMPLETED,
            message = "Build complete!"
        )
    }

    private fun onBuildFailed() {
        _progress.value = _progress.value?.copy(
            stage = BuildStage.FAILED,
            message = "Build failed",
            error = _progress.value?.error
        )
    }

    private fun extractProjectName(idea: String): String {
        val words = idea.split(" ").filter { it.length > 2 }.take(3)
        return if (words.isEmpty()) "MyApp" else words.joinToString("") {
            it.replaceFirstChar { c -> c.uppercase() }
        }.take(20)
    }

    private fun generatePackageName(projectName: String): String {
        val sanitized = projectName.lowercase().replace("[^a-z0-9]".toRegex(), "").take(20)
        return "com.ideaforge.generated.$sanitized"
    }

    override fun onCleared() {
        super.onCleared()
        removeObserver()
    }
}
