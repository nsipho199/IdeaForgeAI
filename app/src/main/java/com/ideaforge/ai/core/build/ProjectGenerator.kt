package com.ideaforge.ai.core.build

import android.util.Log
import com.ideaforge.ai.core.network.ApiService
import com.ideaforge.ai.core.network.ChatCompletionRequest
import com.ideaforge.ai.core.network.ChatMessage
import com.ideaforge.ai.core.constants.AppConstants
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "ProjectGenerator"

class ProjectGenerator(private val apiService: ApiService, private val apiKey: String) {

    suspend fun generateProject(idea: String, projectName: String, packageName: String): Result<Map<String, String>> {
        val prompt = buildPrompt(idea, projectName, packageName)
        val request = ChatCompletionRequest(
            model = AppConstants.MODEL_ID,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            max_tokens = 16000,
            temperature = 0.7
        )
        return callWithRateLimitRetry(request, "generateProject", 0) { req ->
            val response = apiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = req
            )
            if (!response.isSuccessful) {
                val code = response.code()
                if (code == 429) {
                    val retryAfter = parseRetryAfter(response.headers()?.get("Retry-After"))
                    return@callWithRateLimitRetry RateLimitResult.RateLimited(retryAfter)
                }
                return@callWithRateLimitRetry RateLimitResult.Fatal("AI error: HTTP $code")
            }
            val content = response.body()?.choices?.firstOrNull()?.message?.content
                ?: return@callWithRateLimitRetry RateLimitResult.Fatal("Empty AI response")
            val files = parseGeneratedFiles(content)
            if (files.isEmpty()) return@callWithRateLimitRetry RateLimitResult.Fatal("No files generated")
            RateLimitResult.Success(files)
        }
    }

    suspend fun generateFix(idea: String, projectName: String, packageName: String, failingFiles: Map<String, String>, errorLogs: String): Result<Map<String, String>> {
        val fixPrompt = buildFixPrompt(idea, projectName, packageName, failingFiles, errorLogs)
        val request = ChatCompletionRequest(
            model = AppConstants.MODEL_ID,
            messages = listOf(ChatMessage(role = "user", content = fixPrompt)),
            max_tokens = 16000,
            temperature = 0.5
        )
        return callWithRateLimitRetry(request, "generateFix", 0) { req ->
            val response = apiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = req
            )
            if (!response.isSuccessful) {
                val code = response.code()
                if (code == 429) {
                    val retryAfter = parseRetryAfter(response.headers()?.get("Retry-After"))
                    return@callWithRateLimitRetry RateLimitResult.RateLimited(retryAfter)
                }
                return@callWithRateLimitRetry RateLimitResult.Fatal("AI fix error: HTTP $code")
            }
            val content = response.body()?.choices?.firstOrNull()?.message?.content
                ?: return@callWithRateLimitRetry RateLimitResult.Fatal("Empty AI fix response")
            RateLimitResult.Success(parseGeneratedFiles(content))
        }
    }

    private sealed class RateLimitResult {
        data class Success(val files: Map<String, String>) : RateLimitResult()
        data class RateLimited(val retryAfterSeconds: Int) : RateLimitResult()
        data class Fatal(val message: String) : RateLimitResult()
        data class NetworkError(val exception: Exception) : RateLimitResult()
    }

    private val rateLimitRetryDelays = listOf(10000L, 30000L, 60000L)

    private suspend fun callWithRateLimitRetry(
        request: ChatCompletionRequest,
        operation: String,
        depth: Int,
        block: suspend (ChatCompletionRequest) -> RateLimitResult
    ): Result<Map<String, String>> {
        return try {
            val result = block(request)
            when (result) {
                is RateLimitResult.Success -> Result.success(result.files)
                is RateLimitResult.Fatal -> Result.failure(Exception(result.message))
                is RateLimitResult.RateLimited -> {
                    val retryCount = depth + 1
                    Log.w(TAG, "$operation: HTTP 429 rate limited (retry $retryCount)")
                    Log.w(TAG, "  Provider: Google Gemini")
                    Log.w(TAG, "  Model: ${AppConstants.MODEL_ID}")
                    Log.w(TAG, "  Retry-After header: ${result.retryAfterSeconds}s")

                    if (retryCount <= rateLimitRetryDelays.size) {
                        val delayMs = if (result.retryAfterSeconds > 0) {
                            (result.retryAfterSeconds * 1000L).coerceIn(5000L, 120000L)
                        } else {
                            rateLimitRetryDelays[retryCount - 1]
                        }
                        val remainingTotal = rateLimitRetryDelays.drop(retryCount).sum() + delayMs
                        Log.d(TAG, "$operation: waiting ${delayMs}ms before retry $retryCount (remaining ~${remainingTotal / 1000}s)")
                        delay(delayMs)
                        callWithRateLimitRetry(request, operation, retryCount, block)
                    } else {
                        Result.failure(Exception(
                            "Gemini is temporarily rate limited. Your API key is valid.\n\n" +
                            "Provider: Google Gemini\n" +
                            "Model: ${AppConstants.MODEL_ID}\n" +
                            "Free tier: 15 requests per minute | 1M tokens per day\n\n" +
                            "The app automatically retried ${rateLimitRetryDelays.size} times with up to ${rateLimitRetryDelays.last() / 1000}s waits, but Gemini's quota window has not reset yet.\n\n" +
                            "Please wait 60 seconds and try building again."
                        ))
                    }
                }
                is RateLimitResult.NetworkError -> {
                    val e = result.exception
                    when (e) {
                        is retrofit2.HttpException -> {
                            val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "" }
                            if (e.code() == 429) {
                                val retryAfter = e.response()?.headers()?.get("Retry-After")?.toIntOrNull() ?: 0
                                Log.w(TAG, "$operation: HTTP 429 caught as HttpException, retrying...")
                                Log.w(TAG, "  Provider: Google Gemini")
                                Log.w(TAG, "  Model: ${AppConstants.MODEL_ID}")
                                if (retryAfter > 0) Log.w(TAG, "  Retry-After header: ${retryAfter}s")
                                val retryCount = depth + 1
                                if (retryCount <= rateLimitRetryDelays.size) {
                                    val delayMs = if (retryAfter > 0) {
                                        (retryAfter * 1000L).coerceIn(5000L, 120000L)
                                    } else {
                                        rateLimitRetryDelays[retryCount - 1]
                                    }
                                    Log.d(TAG, "$operation: waiting ${delayMs}ms for rate limit (HttpException path)")
                                    delay(delayMs)
                                    callWithRateLimitRetry(request, operation, retryCount, block)
                                } else {
                                    Result.failure(Exception("Gemini is temporarily rate limited. Your API key is valid. Please wait for the quota window to reset."))
                                }
                            } else {
                                Result.failure(Exception("Gemini API error: HTTP ${e.code()}\n$body"))
                            }
                        }
                        is java.net.UnknownHostException -> Result.failure(Exception(
                            "Cannot reach Gemini API (DNS failure).\n\n" +
                            "Possible causes:\n" +
                            "  - No internet connection\n" +
                            "  - Private DNS blocking generativelanguage.googleapis.com\n" +
                            "  - Firewall blocking\n\n" +
                            "Fix: Check internet, disable Private DNS, or try cellular data.\n" +
                            "Use 'Network Diagnostics' in Settings to test."
                        ))
                        is java.net.SocketTimeoutException -> Result.failure(Exception(
                            "Gemini API timed out.\n\n" +
                            "The AI is taking too long to respond. Possible causes:\n" +
                            "  - Slow internet connection\n" +
                            "  - Gemini API is overloaded\n" +
                            "  - Request too large\n\n" +
                            "Fix: Try again. If persistent, simplify your app idea."
                        ))
                        is javax.net.ssl.SSLHandshakeException -> Result.failure(Exception(
                            "SSL/TLS error connecting to Gemini API.\n\n" +
                            "Possible causes:\n" +
                            "  - Device date/time is incorrect\n" +
                            "  - VPN/proxy intercepting HTTPS\n\n" +
                            "Fix: Check device date/time, disable VPN."
                        ))
                        else -> Result.failure(Exception("Network error during AI $operation: ${e.message}", e))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$operation: unexpected exception: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(Exception("Unexpected error during AI $operation: ${e.message}"))
        }
    }

    private fun parseRetryAfter(headerValue: String?): Int {
        if (headerValue.isNullOrBlank()) return 0
        return try {
            val seconds = headerValue.toInt()
            Log.d(TAG, "Retry-After header: $seconds seconds")
            seconds
        } catch (_: NumberFormatException) {
            try {
                val httpDate = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                val targetTime = httpDate.parse(headerValue)?.time ?: return 0
                val now = System.currentTimeMillis()
                val diff = ((targetTime - now) / 1000).toInt()
                diff.coerceAtLeast(1)
            } catch (_: Exception) { 0 }
        }
    }

    private fun buildPrompt(idea: String, projectName: String, packageName: String): String {
        val pkgPath = packageName.replace(".", "/")
        return """You are an expert Android developer. Generate a COMPLETE, COMPILABLE Android project.

IDEA: $idea
PROJECT NAME: $projectName
PACKAGE NAME: $packageName

OUTPUT FORMAT — for EACH file:
===FILE:path/to/file===
complete file contents
===END FILE===

CRITICAL FILES (generate ALL of these):
1. settings.gradle.kts — must contain: pluginManagement, dependencyResolutionManagement, rootProject.name="$projectName", include(":app")
2. build.gradle.kts — root build file (can be empty)
3. gradle.properties — android.useAndroidX=true, kotlin.code.style=official, org.gradle.jvmargs=-Xmx1024m
4. app/build.gradle.kts — namespace=$packageName, compileSdk=35, minSdk=26, targetSdk=35, compose enabled, compose-bom:2024.12.01
5. app/src/main/AndroidManifest.xml — with .MainActivity as launcher activity
6. app/src/main/java/$pkgPath/MainActivity.kt — extends ComponentActivity, uses setContent with Compose
7. app/src/main/java/$pkgPath/ui/theme/Theme.kt — Material3 theme
8. app/src/main/java/$pkgPath/ui/theme/Color.kt — color definitions
9. app/src/main/java/$pkgPath/ui/theme/Type.kt — typography
10. app/src/main/res/values/strings.xml — app_name string

RULES:
- Kotlin + Jetpack Compose + Material3
- All imports must be correct (no missing imports)
- No "TODO", no placeholders, no "..." ellipsis
- Every file must be COMPLETE and COMPILABLE
- Use standard AndroidX imports: androidx.compose.material3, androidx.activity.compose, androidx.compose.ui
- Theme uses MaterialTheme.colorScheme, not Material.colors
- Do NOT use experimental APIs (no SegmentedButton, no rememberPullToRefreshState)
- Use FilterChip instead of SegmentedButton
- All function parameters must have correct types
- Do not use "content" as a variable name in composable functions (it shadows Lambda receiver)
- MainActivity must use: class MainActivity : ComponentActivity()"""
    }

    private fun buildFixPrompt(idea: String, projectName: String, packageName: String, failingFiles: Map<String, String>, errorLogs: String): String {
        val pkgPath = packageName.replace(".", "/")
        val filesSection = failingFiles.entries.joinToString("\n\n") { (path, content) ->
            "===FILE:$path===\n$content\n===END FILE==="
        }
        return """You are an expert Android developer fixing build errors. Fix ALL the failing files below.

ORIGINAL IDEA: $idea
PACKAGE: $packageName

BUILD ERRORS:
$errorLogs

FAILING FILES:
$filesSection

INSTRUCTIONS:
1. Read the build errors carefully
2. Fix EVERY error in each file
3. Ensure all imports are correct
4. Ensure all types match
5. Do NOT add new files — only fix the provided ones
6. Output ONLY the fixed files in ===FILE:path=== format
7. Each file must be COMPLETE — no placeholders, no TODO, no "..."
8. Common fixes: add missing imports, fix type mismatches, fix unresolved references, fix package names
9. For Compose: use MaterialTheme.colorScheme (not Material.colors), use FilterChip (not SegmentedButton)
10. Make sure all composable functions have correct return types (Unit)
11. Do not use "content" as a variable name in composable scope (shadows Lambda receiver)"""
    }

    private fun parseGeneratedFiles(aiResponse: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        val parts = aiResponse.split("===FILE:")
        for (i in 1 until parts.size) {
            val part = parts[i]
            val pathEnd = part.indexOf("===")
            if (pathEnd < 0) continue
            val path = part.substring(0, pathEnd).trim()
            var content = part.substring(pathEnd + 3)
            if (content.startsWith("\n")) content = content.substring(1)
            val endIdx = content.indexOf("===END FILE===")
            if (endIdx >= 0) content = content.substring(0, endIdx)
            files[path.trim()] = content.trimEnd()
        }
        return files
    }
}
