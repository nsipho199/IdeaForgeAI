package com.ideaforge.ai.core.build

import android.util.Log
import com.ideaforge.ai.core.network.ApiService

private const val TAG = "RepairAgent"

data class RepairResult(
    val success: Boolean,
    val fixedFiles: Map<String, String> = emptyMap(),
    val attemptNumber: Int = 0,
    val message: String = "",
    val usedLocalFix: Boolean = false,
    val repairedFileCount: Int = 0,
    val rollbackNeeded: Boolean = false
)

enum class ErrorCategory(val patterns: List<Regex>) {
    GRADLE(
        listOf(
            Regex("could not resolve", RegexOption.IGNORE_CASE),
            Regex("gradle.*sync", RegexOption.IGNORE_CASE),
            Regex("plugin.*version", RegexOption.IGNORE_CASE),
            Regex("dependency.*failed", RegexOption.IGNORE_CASE),
            Regex("build.gradle", RegexOption.IGNORE_CASE)
        )
    ),
    KOTLIN(
        listOf(
            Regex("unresolved reference", RegexOption.IGNORE_CASE),
            Regex("type mismatch", RegexOption.IGNORE_CASE),
            Regex("cannot be resolved", RegexOption.IGNORE_CASE),
            Regex("expecting", RegexOption.IGNORE_CASE),
            Regex("overload resolution", RegexOption.IGNORE_CASE),
            Regex("none of the following", RegexOption.IGNORE_CASE)
        )
    ),
    MANIFEST(
        listOf(
            Regex("manifest", RegexOption.IGNORE_CASE),
            Regex("androidmanifest", RegexOption.IGNORE_CASE),
            Regex("activity.*not found", RegexOption.IGNORE_CASE)
        )
    ),
    RESOURCE(
        listOf(
            Regex("resource.*not found", RegexOption.IGNORE_CASE),
            Regex("aapt2", RegexOption.IGNORE_CASE),
            Regex("resource compilation", RegexOption.IGNORE_CASE),
            Regex("failed.*crunch", RegexOption.IGNORE_CASE),
            Regex("cannot find symbol", RegexOption.IGNORE_CASE)
        )
    ),
    COMPILE(
        listOf(
            Regex("error:", RegexOption.IGNORE_CASE),
            Regex("error\\[", RegexOption.IGNORE_CASE),
            Regex("e:", RegexOption.IGNORE_CASE),
            Regex("compilation failed", RegexOption.IGNORE_CASE),
            Regex("could not find", RegexOption.IGNORE_CASE)
        )
    ),
    DEPENDENCY(
        listOf(
            Regex("duplicate class", RegexOption.IGNORE_CASE),
            Regex("duplicate.*found", RegexOption.IGNORE_CASE),
            Regex("incompatible types", RegexOption.IGNORE_CASE),
            Regex("cannot access", RegexOption.IGNORE_CASE)
        )
    ),
    OOM(
        listOf(
            Regex("out of memory", RegexOption.IGNORE_CASE),
            Regex("GC overhead", RegexOption.IGNORE_CASE),
            Regex("Java heap", RegexOption.IGNORE_CASE),
            Regex("metaspace", RegexOption.IGNORE_CASE)
        )
    )
}

class RepairAgent(
    private val apiService: ApiService,
    private val apiKey: String
) {
    private val localFixDatabase = LocalFixDatabase()
    private val orchestrator = AiOrchestrator(apiService, apiKey, localFixDatabase)
    private val projectGenerator = ProjectGenerator(apiService, apiKey)

    data class ErrorAnalysis(
        val categories: Set<ErrorCategory>,
        val failingFiles: Map<String, String>,
        val rootCauseSummary: String,
        val severity: Int,
        val confidence: Float
    )

    fun getOrchestrator(): AiOrchestrator = orchestrator

    suspend fun analyzeError(
        errorLogs: String,
        allFiles: Map<String, String>
    ): ErrorAnalysis {
        val categories = ErrorCategory.entries.filter { cat ->
            cat.patterns.any { it.containsMatchIn(errorLogs) }
        }.toSet()

        val failingFiles = extractFailingFiles(allFiles, errorLogs)
        val severity = categories.map { cat ->
            when (cat) {
                ErrorCategory.GRADLE -> 8
                ErrorCategory.KOTLIN -> 7
                ErrorCategory.MANIFEST -> 9
                ErrorCategory.RESOURCE -> 5
                ErrorCategory.COMPILE -> 6
                ErrorCategory.DEPENDENCY -> 4
                ErrorCategory.OOM -> 10
            }
        }.sum() / maxOf(categories.size, 1)

        val rootCause = buildString {
            if (categories.contains(ErrorCategory.OOM)) {
                append("Out of memory during build. ")
            }
            if (categories.contains(ErrorCategory.GRADLE)) {
                append("Gradle configuration issue. ")
            }
            if (categories.contains(ErrorCategory.KOTLIN)) {
                append("Kotlin compilation errors in source files. ")
            }
            if (categories.contains(ErrorCategory.MANIFEST)) {
                append("AndroidManifest.xml misconfiguration. ")
            }
            if (categories.contains(ErrorCategory.RESOURCE)) {
                append("Android resource compilation issue. ")
            }
            if (categories.contains(ErrorCategory.DEPENDENCY)) {
                append("Dependency conflict or missing dependency. ")
            }
            if (categories.isEmpty()) {
                append("Unknown build error. ")
            }
        }

        return ErrorAnalysis(
            categories = categories,
            failingFiles = failingFiles,
            rootCauseSummary = rootCause.trim(),
            severity = severity,
            confidence = if (categories.isNotEmpty()) 0.8f else 0.3f
        )
    }

    suspend fun attemptRepair(
        idea: String,
        projectName: String,
        packageName: String,
        currentFiles: Map<String, String>,
        errorLogs: String,
        attemptNumber: Int,
        snapshot: BuildSnapshot?
    ): RepairResult {
        val analysis = analyzeError(errorLogs, currentFiles)

        Log.i(TAG, "[REBUILD] Repair attempt $attemptNumber")
        Log.i(TAG, "[ANALYZER] Categories: ${analysis.categories.joinToString { it.name }}")
        Log.i(TAG, "[ANALYZER] Root cause: ${analysis.rootCauseSummary}")
        Log.i(TAG, "[ANALYZER] Failing files: ${analysis.failingFiles.size}")

        val localFixProvider = orchestrator.localFixProvider
        val localFixes = localFixProvider.findFixes(errorLogs, currentFiles)

        if (localFixes.isNotEmpty()) {
            Log.i(TAG, "[FIX SEARCH] Local fixes available: ${localFixes.size}")
            val working = currentFiles.toMutableMap()
            val applied = mutableMapOf<String, String>()

            for (fix in localFixes.take(3)) {
                val result = localFixProvider.applyFix(fix, working, projectName, packageName)
                applied.putAll(result)
            }

            if (applied.isNotEmpty()) {
                Log.i(TAG, "[FIX SEARCH] Applied ${applied.size} local fixes: ${applied.keys}")
                return RepairResult(
                    success = true,
                    fixedFiles = applied,
                    attemptNumber = attemptNumber,
                    message = "Fixed ${applied.size} file(s) via local knowledge base",
                    usedLocalFix = true,
                    repairedFileCount = applied.size
                )
            }
        }

        if (snapshot != null) {
            Log.i(TAG, "[AI] Attempting AI repair attempt $attemptNumber")
            val fixResult = projectGenerator.generateFix(
                idea, projectName, packageName,
                analysis.failingFiles.ifEmpty { currentFiles.entries.take(5).associate { it.key to it.value } },
                errorLogs
            )

            if (fixResult.isSuccess) {
                val fixed = fixResult.getOrDefault(emptyMap())
                if (fixed.any { (path, content) -> content != currentFiles[path] }) {
                    return RepairResult(
                        success = true,
                        fixedFiles = fixed,
                        attemptNumber = attemptNumber,
                        message = "AI repaired ${fixed.size} file(s)",
                        usedLocalFix = false,
                        repairedFileCount = fixed.size
                    )
                }
            }

            Log.w(TAG, "[AI] AI fix returned same content or failed, trying fallback")
            val ktFiles = currentFiles.filter { it.key.endsWith(".kt") }.entries.take(4).associate { it.key to it.value }
            if (ktFiles.isNotEmpty()) {
                val ktFixResult = projectGenerator.generateFix(idea, projectName, packageName, ktFiles, errorLogs)
                if (ktFixResult.isSuccess) {
                    val ktFixed = ktFixResult.getOrDefault(emptyMap())
                    val changed = ktFixed.filter { (path, content) -> content != currentFiles[path] }
                    if (changed.isNotEmpty()) {
                        return RepairResult(
                            success = true,
                            fixedFiles = changed,
                            attemptNumber = attemptNumber,
                            message = "AI repaired ${changed.size} .kt file(s) (fallback)",
                            usedLocalFix = false,
                            repairedFileCount = changed.size
                        )
                    }
                }
            }

            return RepairResult(
                success = false,
                attemptNumber = attemptNumber,
                message = "AI fix returned no changes; may need rollback",
                rollbackNeeded = true
            )
        }

        return RepairResult(
            success = false,
            attemptNumber = attemptNumber,
            message = "No snapshot available for rollback safety"
        )
    }

    private fun extractFailingFiles(allFiles: Map<String, String>, errorLogs: String): Map<String, String> {
        val failingPaths = mutableSetOf<String>()
        val lines = errorLogs.lines()

        for (line in lines) {
            val normalizedLine = line.lowercase().trim()
            if (normalizedLine.isBlank()) continue

            val isError = ErrorCategory.entries.any { cat ->
                cat.patterns.any { it.containsMatchIn(normalizedLine) }
            }
            if (!isError) continue

            val ktMatch = Regex("([\\w/]+\\.kt):(\\d+):(\\d+)").find(line)
            if (ktMatch != null) {
                val filePath = ktMatch.groupValues[1]
                for (path in allFiles.keys) {
                    if (path.endsWith(filePath) || path.contains(filePath)) {
                        failingPaths.add(path)
                    }
                }
            }

            val pathMatch = Regex("(app/src/main/java/[\\w/]+\\.kt)").find(line)
            if (pathMatch != null) failingPaths.add(pathMatch.groupValues[1])

            val resMatch = Regex("(app/src/main/res/[\\w/]+\\.xml)").find(line)
            if (resMatch != null) failingPaths.add(resMatch.groupValues[1])

            if (normalizedLine.contains("build.gradle") || normalizedLine.contains("could not resolve")) {
                failingPaths.addAll(allFiles.keys.filter { it.contains("build.gradle") })
            }
            if (normalizedLine.contains("manifest") || normalizedLine.contains("androidmanifest")) {
                failingPaths.addAll(allFiles.keys.filter { it.contains("AndroidManifest") })
            }
            if (normalizedLine.contains("settings.gradle")) {
                failingPaths.addAll(allFiles.keys.filter { it.contains("settings.gradle") })
            }
            if (normalizedLine.contains("gradle.properties")) {
                failingPaths.addAll(allFiles.keys.filter { it.contains("gradle.properties") })
            }
            if (normalizedLine.contains("r.java") || normalizedLine.contains("unresolved reference: r")) {
                failingPaths.addAll(allFiles.keys.filter { it.endsWith("strings.xml") || it.endsWith("colors.xml") })
            }
        }

        if (failingPaths.isEmpty()) {
            val allKt = allFiles.filter { it.key.endsWith(".kt") }
            val allXml = allFiles.filter { it.key.endsWith(".xml") }
            val allGradle = allFiles.filter { it.key.contains("build.gradle") || it.key.contains("settings.gradle") || it.key == "gradle.properties" }
            return (allKt + allGradle + allXml).entries.take(8).associate { it.key to it.value }
        }

        val matched = allFiles.filter { (path, _) ->
            failingPaths.any { fp -> path == fp || path.endsWith(fp) || fp.endsWith(path) }
        }.ifEmpty {
            allFiles.filter { it.key.endsWith(".kt") }.entries.take(5).associate { it.key to it.value }
        }

        return matched
    }
}
