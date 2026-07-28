package com.ideaforge.ai.core.build

import com.ideaforge.ai.core.network.ApiService

class ErrorRecovery(private val projectGenerator: ProjectGenerator) {

    data class RetryResult(
        val success: Boolean,
        val fixedFiles: Map<String, String> = emptyMap(),
        val attemptNumber: Int = 0,
        val message: String = ""
    )

    suspend fun attemptFix(
        idea: String,
        projectName: String,
        packageName: String,
        currentFiles: Map<String, String>,
        errorLogs: String,
        attempt: Int,
        maxRetries: Int = 5
    ): RetryResult {
        val failingFiles = extractFailingFiles(currentFiles, errorLogs)

        val fixResult = projectGenerator.generateFix(idea, projectName, packageName, failingFiles, errorLogs)
        if (fixResult.isSuccess) {
            val fixedFiles = fixResult.getOrDefault(emptyMap())
            return RetryResult(
                success = true,
                fixedFiles = fixedFiles,
                attemptNumber = attempt,
                message = "Fixed ${fixedFiles.size} files on attempt $attempt"
            )
        }

        if (failingFiles.size > 3) {
            val reducedFiles = failingFiles.entries.take(3).associate { it.key to it.value }
            val retryResult = projectGenerator.generateFix(idea, projectName, packageName, reducedFiles, errorLogs)
            if (retryResult.isSuccess) {
                return RetryResult(
                    success = true,
                    fixedFiles = retryResult.getOrDefault(emptyMap()),
                    attemptNumber = attempt,
                    message = "Fixed ${retryResult.getOrDefault(emptyMap()).size} files on reduced set (attempt $attempt)"
                )
            }
        }

        val ktFiles = currentFiles.filter { it.key.endsWith(".kt") }.entries.take(4).associate { it.key to it.value }
        if (ktFiles.isNotEmpty()) {
            val lastResort = projectGenerator.generateFix(idea, projectName, packageName, ktFiles, errorLogs)
            if (lastResort.isSuccess) {
                return RetryResult(
                    success = true,
                    fixedFiles = lastResort.getOrDefault(emptyMap()),
                    attemptNumber = attempt,
                    message = "Fixed ${lastResort.getOrDefault(emptyMap()).size} files (last resort, attempt $attempt)"
                )
            }
        }

        return RetryResult(
            success = false,
            attemptNumber = attempt,
            message = "AI fix generation failed: ${fixResult.exceptionOrNull()?.message}"
        )
    }

    private fun extractFailingFiles(allFiles: Map<String, String>, errorLogs: String): Map<String, String> {
        val failingPaths = mutableSetOf<String>()
        val lines = errorLogs.lines()

        for (line in lines) {
            val normalizedLine = line.lowercase().trim()
            if (normalizedLine.isBlank()) continue

            val isError = normalizedLine.contains("error:") ||
                    normalizedLine.contains("error[") ||
                    normalizedLine.contains("e:") ||
                    normalizedLine.contains("unresolved reference") ||
                    normalizedLine.contains("type mismatch") ||
                    normalizedLine.contains("cannot find") ||
                    normalizedLine.contains("not found") ||
                    normalizedLine.contains("expecting") ||
                    normalizedLine.contains("overload resolution") ||
                    normalizedLine.contains("none of the following") ||
                    normalizedLine.contains("is not abstract") ||
                    normalizedLine.contains("does not override") ||
                    normalizedLine.contains("incompatible types") ||
                    normalizedLine.contains("cannot access") ||
                    normalizedLine.contains("unused import")

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
        }

        if (matched.isEmpty()) {
            return allFiles.filter { it.key.endsWith(".kt") }.entries.take(5).associate { it.key to it.value }
        }

        return matched
    }
}
