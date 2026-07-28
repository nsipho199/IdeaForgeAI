package com.ideaforge.ai.core.build

import android.util.Log
import com.ideaforge.ai.core.network.ApiService

private const val TAG = "AiOrchestrator"

interface AiProvider {
    val name: String
    val isAvailable: Boolean
    suspend fun generateResponse(prompt: String, systemPrompt: String? = null): AiResult
    suspend fun generateFix(
        idea: String,
        projectName: String,
        packageName: String,
        failingFiles: Map<String, String>,
        errorLogs: String
    ): AiResult
}

sealed class AiResult {
    data class Success(val data: String, val files: Map<String, String> = emptyMap()) : AiResult()
    data class RateLimited(val retryAfter: Int = 60) : AiResult()
    data class Error(val message: String) : AiResult()
    data class NetworkError(val message: String) : AiResult()
}

class GeminiProvider(
    private val apiService: ApiService,
    private val apiKey: String
) : AiProvider {
    private val generator = ProjectGenerator(apiService, apiKey)

    override val name = "Google Gemini 2.5 Flash"
    override val isAvailable: Boolean
        get() = apiKey.isNotBlank()

    override suspend fun generateResponse(prompt: String, systemPrompt: String?): AiResult {
        val result = generator.generateProject(prompt, "Project", "com.app")
        return if (result.isSuccess) {
            AiResult.Success("Generated ${result.getOrDefault(emptyMap()).size} files", result.getOrDefault(emptyMap()))
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
            if (msg.contains("rate limit", ignoreCase = true) || msg.contains("429", ignoreCase = true)) {
                AiResult.RateLimited()
            } else if (msg.contains("DNS", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ||
                msg.contains("SSL", ignoreCase = true) || msg.contains("Network", ignoreCase = true)) {
                AiResult.NetworkError(msg)
            } else {
                AiResult.Error(msg)
            }
        }
    }

    override suspend fun generateFix(
        idea: String,
        projectName: String,
        packageName: String,
        failingFiles: Map<String, String>,
        errorLogs: String
    ): AiResult {
        val result = generator.generateFix(idea, projectName, packageName, failingFiles, errorLogs)
        return if (result.isSuccess) {
            val files = result.getOrDefault(emptyMap())
            AiResult.Success("Fixed ${files.size} files", files)
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
            if (msg.contains("rate limit", ignoreCase = true)) {
                AiResult.RateLimited()
            } else if (msg.contains("DNS", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ||
                msg.contains("SSL", ignoreCase = true)
            ) {
                AiResult.NetworkError(msg)
            } else {
                AiResult.Error(msg)
            }
        }
    }
}

class LocalFixProvider(private val database: LocalFixDatabase) : AiProvider {
    override val name = "Local Fix Database"
    override val isAvailable = true

    fun findFixes(errorLogs: String, currentFiles: Map<String, String>): List<KnownFix> {
        return database.findFixes(errorLogs, currentFiles)
    }

    fun applyFix(fix: KnownFix, currentFiles: MutableMap<String, String>, projectName: String, packageName: String): Map<String, String> {
        return database.applyFix(fix, currentFiles, projectName, packageName)
    }

    override suspend fun generateResponse(prompt: String, systemPrompt: String?): AiResult {
        return AiResult.Success("")
    }

    override suspend fun generateFix(
        idea: String,
        projectName: String,
        packageName: String,
        failingFiles: Map<String, String>,
        errorLogs: String
    ): AiResult {
        val fixes = database.findFixes(errorLogs, failingFiles)
        if (fixes.isEmpty()) return AiResult.Success("No local fixes found")

        val working = failingFiles.toMutableMap()
        val applied = fixes.take(3).flatMap { fix ->
            database.applyFix(fix, working, projectName, packageName).entries
        }.associate { it.key to it.value }

        if (applied.isEmpty()) {
            return AiResult.Success("Local fixes matched but none required file changes")
        }

        val summary = "Applied ${applied.size} local fix(es): ${applied.keys.joinToString(", ")}"
        Log.i(TAG, summary)
        return AiResult.Success(summary, applied)
    }
}

class AiOrchestrator(
    private val apiService: ApiService,
    private val apiKey: String,
    private val localFixDatabase: LocalFixDatabase = LocalFixDatabase()
) {
    private val geminiProvider: GeminiProvider by lazy { GeminiProvider(apiService, apiKey) }
    val localFixProvider: LocalFixProvider by lazy { LocalFixProvider(localFixDatabase) }
    private var activeProviderIndex = 0
    var lastProvider: String = "none"
        private set

    private val providers: List<AiProvider>
        get() {
            val list = mutableListOf<AiProvider>()
            if (geminiProvider.isAvailable) list.add(geminiProvider)
            list.add(localFixProvider)
            return list
        }

    suspend fun generateFixWithFallback(
        idea: String,
        projectName: String,
        packageName: String,
        failingFiles: Map<String, String>,
        errorLogs: String,
        maxRetries: Int = 3
    ): AiResult {
        var lastError: AiResult? = null
        val delays = listOf(2000L, 5000L, 10000L)

        for (attempt in 0 until maxRetries) {
            for (providerIdx in activeProviderIndex until providers.size) {
                val provider = providers[providerIdx]
                if (!provider.isAvailable) continue

                Log.d(TAG, "Fix attempt $attempt: provider '${provider.name}' (index $providerIdx)")
                lastProvider = provider.name

                val result = provider.generateFix(idea, projectName, packageName, failingFiles, errorLogs)
                when (result) {
                    is AiResult.Success -> {
                        if (result.files.isNotEmpty()) {
                            Log.i(TAG, "Provider '${provider.name}' succeeded: ${result.files.size} file(s)")
                            activeProviderIndex = providerIdx
                            return result
                        }
                        if (provider !is LocalFixProvider) {
                            Log.w(TAG, "Provider '${provider.name}' returned no files, trying next")
                        }
                        lastError = result
                        activeProviderIndex = minOf(providerIdx + 1, providers.size - 1)
                        continue
                    }
                    is AiResult.RateLimited -> {
                        Log.w(TAG, "Provider '${provider.name}' rate limited (retryAfter=${result.retryAfter}s)")
                        lastError = result
                        val waitMs = (result.retryAfter * 1000L).coerceAtMost(60000L)
                        kotlinx.coroutines.delay(waitMs)
                        activeProviderIndex = minOf(providerIdx + 1, providers.size - 1)
                        if (providerIdx < providers.size - 1) continue else break
                    }
                    is AiResult.NetworkError -> {
                        Log.e(TAG, "Provider '${provider.name}' network error: ${result.message}")
                        lastError = result
                        activeProviderIndex = minOf(providerIdx + 1, providers.size - 1)
                        continue
                    }
                    is AiResult.Error -> {
                        Log.e(TAG, "Provider '${provider.name}' error: ${result.message}")
                        lastError = result
                        activeProviderIndex = minOf(providerIdx + 1, providers.size - 1)
                        continue
                    }
                }
            }

            activeProviderIndex = 0
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(delays.getOrElse(attempt) { 10000L })
            }
        }

        return lastError ?: AiResult.Error("All providers exhausted after $maxRetries attempts")
    }

    fun resetProviders() {
        activeProviderIndex = 0
        lastProvider = "none"
    }
}
