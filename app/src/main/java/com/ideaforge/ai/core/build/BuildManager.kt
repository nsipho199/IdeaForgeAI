package com.ideaforge.ai.core.build

import android.content.Context
import android.os.Environment
import com.ideaforge.ai.core.cloud.CloudBuildProvider
import com.ideaforge.ai.core.cloud.CloudBuildStatus
import com.ideaforge.ai.core.cloud.BuildPhase
import com.ideaforge.ai.core.cloud.MAX_BUILD_RETRIES
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.core.network.ApiService
import kotlinx.coroutines.delay
import java.io.File

class BuildManager(
    private val context: Context,
    private val apiService: ApiService,
    private val cloudProvider: CloudBuildProvider,
    private val apiKey: String
) {
    private val projectGenerator = ProjectGenerator(apiService, apiKey)
    private val repairAgent = RepairAgent(apiService, apiKey)
    private val snapshotManager = SnapshotManager(context)

    data class BuildEvent(
        val phase: BuildPhase,
        val message: String,
        val progress: Float,
        val projectDir: String? = null,
        val apkPath: String? = null,
        val error: String? = null,
        val logs: List<String> = emptyList()
    )

    suspend fun executeBuild(
        idea: String,
        projectName: String,
        packageName: String,
        onEvent: (BuildEvent) -> Unit
    ): Result<String> {
        val allLogs = mutableListOf<String>()
        val projectDir = getProjectDir(projectName)

        allLogs.add("[BUILD] Starting autonomous build pipeline")
        allLogs.add("[BUILD] Idea: ${idea.take(100)}...")
        allLogs.add("[BUILD] Project: $projectName | Package: $packageName")
        allLogs.add("[BUILD] Max repair attempts: $MAX_BUILD_RETRIES")

        onEvent(BuildEvent(BuildPhase.PLANNING, "Planning your app...", 5f, logs = allLogs))

        onEvent(BuildEvent(BuildPhase.GENERATING, "Generating code with Gemini AI...", 10f, logs = allLogs))
        allLogs.add("[AI] Requesting Gemini code generation...")

        var files: Map<String, String>
        val genResult = projectGenerator.generateProject(idea, projectName, packageName)
        if (genResult.isFailure) {
            allLogs.add("[AI] Generation failed: ${genResult.exceptionOrNull()?.message}")
            return Result.failure(Exception("Code generation failed: ${genResult.exceptionOrNull()?.message}"))
        }
        files = genResult.getOrDefault(emptyMap())
        allLogs.add("[AI] Generated ${files.size} files")
        files = ensureCriticalFiles(files, projectName, packageName)
        allLogs.add("[BUILD] ${files.size} files after critical file check")
        writeFiles(projectDir, files)

        onEvent(BuildEvent(BuildPhase.VALIDATING, "Validating project structure...", 18f, projectDir = projectDir.absolutePath, logs = allLogs))
        val validation = ProjectValidator.validate(projectDir.absolutePath)
        if (!validation.valid) {
            allLogs.add("[BUILD] Validation issues (auto-patching): ${validation.errors.joinToString("; ")}")
            for (err in validation.errors) {
                if (err.contains("Missing AndroidManifest.xml")) {
                    val patched = ensureCriticalFiles(files, projectName, packageName)
                    files = patched
                    writeFiles(projectDir, files)
                    allLogs.add("[BUILD] Patched missing files")
                }
            }
            val recheck = ProjectValidator.validate(projectDir.absolutePath)
            if (!recheck.valid) {
                allLogs.add("[BUILD] Validation still failing after patch: ${recheck.errors.joinToString("; ")}")
            }
        }
        allLogs.add("[BUILD] Proceeding to cloud build (${validation.warnings.size} warnings)")

        onEvent(BuildEvent(BuildPhase.UPLOADING, "Ensuring cloud repository...", 22f, projectDir = projectDir.absolutePath, logs = allLogs))
        val ensureResult = retryOnTransient(3) { cloudProvider.ensureRepository() }
        if (ensureResult.isFailure) {
            allLogs.add("[BUILD] Repository setup failed: ${ensureResult.exceptionOrNull()?.message}")
            return Result.failure(Exception("Repository setup failed: ${ensureResult.exceptionOrNull()?.message}"))
        }
        allLogs.add("[BUILD] Repository ready")

        var branch = "build-${System.currentTimeMillis()}"
        var attempt = 0
        var currentFiles = files.toMutableMap()
        var lastFixedFiles: Map<String, String> = emptyMap()

        snapshotManager.clearSnapshots()

        while (attempt <= MAX_BUILD_RETRIES) {
            if (attempt > 0) {
                allLogs.add("")
                allLogs.add("[REBUILD] === Attempt $attempt of $MAX_BUILD_RETRIES ===")
            }

            onEvent(BuildEvent(BuildPhase.UPLOADING, "Uploading project to cloud...", 25f, projectDir = projectDir.absolutePath, logs = allLogs))
            val uploadResult = retryOnTransient(5) {
                if (attempt == 0) {
                    cloudProvider.pushFiles(projectDir.absolutePath, projectName, branch)
                } else {
                    cloudProvider.pushFix(projectDir.absolutePath, projectName, branch, lastFixedFiles)
                    Result.success(branch)
                }
            }
            if (uploadResult.isFailure) {
                allLogs.add("[BUILD] Upload failed: ${uploadResult.exceptionOrNull()?.message}")
                if (attempt < MAX_BUILD_RETRIES) {
                    allLogs.add("[BUILD] Retrying upload with fresh branch...")
                    branch = "retry-${System.currentTimeMillis()}"
                    attempt++
                    continue
                }
                return Result.failure(Exception("Upload failed after all retries: ${uploadResult.exceptionOrNull()?.message}"))
            }
            allLogs.add("[BUILD] Upload complete (branch: $branch)")

            onEvent(BuildEvent(BuildPhase.QUEUED, "Triggering cloud build...", 32f, projectDir = projectDir.absolutePath, logs = allLogs))
            val buildResult = retryOnTransient(5) { cloudProvider.startBuild(branch) }
            if (buildResult.isFailure) {
                allLogs.add("[BUILD] Failed to start build: ${buildResult.exceptionOrNull()?.message}")
                if (attempt < MAX_BUILD_RETRIES) {
                    allLogs.add("[BUILD] Waiting 10s before retry...")
                    delay(10000)
                    attempt++
                    continue
                }
                return Result.failure(Exception("Failed to start build after all retries: ${buildResult.exceptionOrNull()?.message}"))
            }
            val buildId = buildResult.getOrDefault("")
            allLogs.add("[BUILD] Build triggered (run: $buildId)")

            val pollResult = pollBuildStatus(buildId, allLogs, projectDir) { event ->
                onEvent(event.copy(logs = allLogs.toList()))
            }

            if (pollResult.isSuccess) {
                val apkPath = pollResult.getOrDefault("")
                allLogs.add("[SUCCESS] APK generated: $apkPath")
                onEvent(BuildEvent(BuildPhase.COMPLETED, "Build complete!", 100f, projectDir = projectDir.absolutePath, apkPath = apkPath, logs = allLogs))
                try { cloudProvider.cleanup(branch) } catch (_: Exception) {}
                snapshotManager.clearSnapshots()
                return Result.success(apkPath)
            }

            val buildError = pollResult.exceptionOrNull()?.message ?: "Build failed"
            allLogs.add("[BUILD] Compilation failed: $buildError")

            if (attempt >= MAX_BUILD_RETRIES) {
                allLogs.add("[REBUILD] Max repair attempts ($MAX_BUILD_RETRIES) exhausted")
                try { cloudProvider.cleanup(branch) } catch (_: Exception) {}
                return Result.failure(Exception("Build failed after $MAX_BUILD_RETRIES repair attempts: $buildError"))
            }

            val logsResult = cloudProvider.getBuildLogs(buildId)
            val buildLogs = logsResult.getOrDefault(buildError)
            allLogs.add("[BUILD] Retrieved build logs (${buildLogs.length} chars)")

            val snapshot = snapshotManager.createSnapshot(attempt + 1, currentFiles.toMap(), buildLogs)
            allLogs.add("[SNAPSHOT] Created before repair attempt ${attempt + 1}")

            onEvent(BuildEvent(BuildPhase.ANALYZING, "Analyzing build errors...", 35f, projectDir = projectDir.absolutePath, logs = allLogs))
            allLogs.add("[ANALYZER] Analyzing build failure (attempt ${attempt + 1})...")

            val analysis = repairAgent.analyzeError(buildLogs, currentFiles)
            allLogs.add("[ANALYZER] Category: ${analysis.categories.joinToString { it.name }}, " +
                "failing=${analysis.failingFiles.size} files, severity=${analysis.severity}")
            allLogs.add("[ANALYZER] Root cause: ${analysis.rootCauseSummary}")

            onEvent(BuildEvent(BuildPhase.SEARCHING_FIXES, "Searching local fix database...", 38f, projectDir = projectDir.absolutePath, logs = allLogs))
            allLogs.add("[FIX SEARCH] Checking local error intelligence database...")

            val localFixProvider = repairAgent.getOrchestrator().localFixProvider
            val localFixes = localFixProvider.findFixes(buildLogs, currentFiles)
            if (localFixes.isNotEmpty()) {
                allLogs.add("[FIX SEARCH] Found ${localFixes.size} local solution(s): ${localFixes.map { it.id }}")
            } else {
                allLogs.add("[FIX SEARCH] No local fix found, falling back to AI")
            }

            onEvent(BuildEvent(BuildPhase.CALLING_AI_REPAIR, "Calling AI for repair...", 40f, projectDir = projectDir.absolutePath, logs = allLogs))
            allLogs.add("[AI] Provider: ${repairAgent.getOrchestrator().lastProvider}")

            val repairResult = repairAgent.attemptRepair(
                idea, projectName, packageName,
                currentFiles, buildLogs, attempt + 1, snapshot
            )

            if (repairResult.rollbackNeeded) {
                allLogs.add("[REBUILD] AI fix returned no changes, rolling back...")
                onEvent(BuildEvent(BuildPhase.ROLLING_BACK, "Rolling back bad changes...", 15f, projectDir = projectDir.absolutePath, logs = allLogs))

                val rolledBack = snapshotManager.rollbackTo(snapshot)
                currentFiles.clear()
                currentFiles.putAll(rolledBack)
                writeFiles(projectDir, rolledBack)
                allLogs.add("[ROLLBACK] Original project restored from snapshot ${snapshot.buildAttempt}")

                branch = "retry-${System.currentTimeMillis()}"
                attempt++
                continue
            }

            if (repairResult.success && repairResult.fixedFiles.isNotEmpty()) {
                val providerName = repairAgent.getOrchestrator().lastProvider
                allLogs.add("[AI] ${repairResult.message} (provider: $providerName)")
                repairResult.fixedFiles.keys.forEach { path ->
                    allLogs.add("[PATCH] Modified $path")
                }
                lastFixedFiles = repairResult.fixedFiles
                currentFiles.putAll(repairResult.fixedFiles)
                writeFiles(projectDir, repairResult.fixedFiles)

                onEvent(BuildEvent(BuildPhase.APPLYING_FIX, "Applied fix to ${repairResult.fixedFiles.size} file(s)", 42f, projectDir = projectDir.absolutePath, logs = allLogs))

                onEvent(BuildEvent(BuildPhase.REBUILDING, "Rebuilding with fixes...", 45f, projectDir = projectDir.absolutePath, logs = allLogs))
                allLogs.add("[REBUILD] Attempt ${attempt + 1}/$MAX_BUILD_RETRIES with ${repairResult.fixedFiles.size} patched file(s)")
            } else {
                allLogs.add("[ANALYZER] Repair did not produce changes: ${repairResult.message}")
                allLogs.add("[ANALYZER] Retrying with broader context...")

                val broadResult = repairAgent.attemptRepair(
                    idea, projectName, packageName,
                    currentFiles, buildLogs, attempt + 1, snapshot
                )

                if (broadResult.success && broadResult.fixedFiles.isNotEmpty()) {
                    lastFixedFiles = broadResult.fixedFiles
                    currentFiles.putAll(broadResult.fixedFiles)
                    writeFiles(projectDir, broadResult.fixedFiles)

                    broadResult.fixedFiles.keys.forEach { path ->
                        allLogs.add("[PATCH] Modified $path (broad context)")
                    }

                    onEvent(BuildEvent(BuildPhase.APPLYING_FIX, "Applied fix (broad context) to ${broadResult.fixedFiles.size} file(s)", 42f, projectDir = projectDir.absolutePath, logs = allLogs))
                    onEvent(BuildEvent(BuildPhase.REBUILDING, "Rebuilding with fixes...", 45f, projectDir = projectDir.absolutePath, logs = allLogs))
                    allLogs.add("[REBUILD] Attempt ${attempt + 1}/$MAX_BUILD_RETRIES with ${broadResult.fixedFiles.size} patched file(s)")
                } else {
                    allLogs.add("[REBUILD] All fix attempts exhausted, retrying build anyway...")
                    attempt++
                    branch = "retry-${System.currentTimeMillis()}"
                    continue
                }
            }

            branch = "fix-${System.currentTimeMillis()}"
            attempt++
        }

        return Result.failure(Exception("Build failed after $MAX_BUILD_RETRIES repair attempts"))
    }

    private suspend fun pollBuildStatus(
        buildId: String,
        allLogs: MutableList<String>,
        projectDir: File,
        onEvent: (BuildEvent) -> Unit
    ): Result<String> {
        var lastPhase = BuildPhase.QUEUED
        var pollCount = 0
        val maxPolls = 180
        var consecutiveErrors = 0

        while (pollCount < maxPolls) {
            delay(5000)
            pollCount++

            val statusResult = cloudProvider.getBuildStatus(buildId)
            if (statusResult.isFailure) {
                consecutiveErrors++
                if (consecutiveErrors > 10) {
                    return Result.failure(Exception("Lost connection to build server"))
                }
                allLogs.add("[BUILD] Status check failed (attempt $consecutiveErrors), retrying...")
                continue
            }
            consecutiveErrors = 0

            val status = statusResult.getOrDefault(CloudBuildStatus(BuildPhase.QUEUED, 32f, "Checking..."))

            if (status.status != lastPhase) {
                lastPhase = status.status
                allLogs.add("[BUILD] [${status.status.displayName}] ${status.message}")
                onEvent(BuildEvent(status.status, status.message, status.progress, projectDir = projectDir.absolutePath))
            }

            when (status.status) {
                BuildPhase.COMPLETED -> {
                    onEvent(BuildEvent(BuildPhase.DOWNLOADING, "Downloading APK...", 90f, projectDir = projectDir.absolutePath))
                    allLogs.add("[BUILD] Downloading APK...")

                    val dlResult = retryOnTransient(3) {
                        cloudProvider.downloadApk(buildId, getApkDir().absolutePath)
                    }
                    if (dlResult.isFailure) {
                        return Result.failure(Exception("APK download failed: ${dlResult.exceptionOrNull()?.message}"))
                    }
                    val apkPath = dlResult.getOrDefault("")
                    allLogs.add("[SUCCESS] APK downloaded and verified: $apkPath")
                    return Result.success(apkPath)
                }
                BuildPhase.FAILED -> {
                    return Result.failure(Exception(status.error ?: status.message))
                }
                else -> {}
            }
        }

        return Result.failure(Exception("Build polling timed out after ${maxPolls * 5}s"))
    }

    private suspend fun <T> retryOnTransient(maxRetries: Int, block: suspend () -> Result<T>): Result<T> {
        var lastError: Exception? = null
        val delays = listOf(2000L, 5000L, 10000L, 20000L, 30000L)
        repeat(maxRetries) { attempt ->
            val result = try {
                block()
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull() as? Exception
            if (lastError != null && cloudProvider.isTransientError(lastError!!) && attempt < maxRetries - 1) {
                val delayMs = delays.getOrElse(attempt) { 10000L }
                delay(delayMs)
            } else if (lastError != null && !cloudProvider.isTransientError(lastError!!)) {
                return result
            }
        }
        return Result.failure(lastError ?: Exception("Operation failed after $maxRetries retries"))
    }

    private fun getProjectDir(projectName: String): File {
        val base = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), AppConstants.PROJECTS_DIR)
        return File(base, projectName.replace(" ", "_"))
    }

    private fun getApkDir(): File {
        val base = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "IdeaForge")
        return File(base, "APKs")
    }

    private fun writeFiles(projectDir: File, files: Map<String, String>) {
        val tempDir = File(projectDir.parentFile, "${projectDir.name}.tmp.${System.currentTimeMillis()}")
        try {
            for ((path, content) in files) {
                val file = File(tempDir, path)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
            tempDir.renameTo(projectDir)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            for ((path, content) in files) {
                val file = File(projectDir, path)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
        }
    }

    private fun timestamp(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    private fun ensureCriticalFiles(files: Map<String, String>, projectName: String, packageName: String): Map<String, String> {
        val patched = files.toMutableMap()
        val pkgPath = packageName.replace(".", "/")

        if (!patched.keys.any { it.endsWith("AndroidManifest.xml") }) {
            patched["app/src/main/AndroidManifest.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity
            android:name=".$pkgPath.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.AppCompat.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>"""
        }

        if (!patched.keys.any { it.endsWith("strings.xml") }) {
            patched["app/src/main/res/values/strings.xml"] = """<resources>
    <string name="app_name">${projectName.replace("_", " ")}</string>
</resources>"""
        }

        if (!patched.keys.any { it == "settings.gradle.kts" }) {
            patched["settings.gradle.kts"] = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "$projectName"
include(":app")"""
        }

        if (!patched.keys.any { it == "build.gradle.kts" }) {
            patched["build.gradle.kts"] = ""
        }

        if (!patched.keys.any { it == "gradle.properties" }) {
            patched["gradle.properties"] = """android.useAndroidX=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx1024m
android.nonTransitiveRClass=true"""
        }

        if (!patched.keys.any { it == "app/build.gradle.kts" }) {
            patched["app/build.gradle.kts"] = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "$packageName"
    compileSdk = 35
    defaultConfig {
        applicationId = "$packageName"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}"""
        }

        return patched
    }
}
