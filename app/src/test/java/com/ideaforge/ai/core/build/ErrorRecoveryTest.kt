package com.ideaforge.ai.core.build

import com.ideaforge.ai.core.network.ApiService
import com.ideaforge.ai.core.network.ChatCompletionResponse
import com.ideaforge.ai.core.network.ChatMessage
import com.ideaforge.ai.core.network.Choice
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ErrorRecoveryTest {

    private lateinit var projectGenerator: ProjectGenerator
    private lateinit var errorRecovery: ErrorRecovery
    private lateinit var apiService: ApiService

    @Before
    fun setup() {
        apiService = mockk()
        projectGenerator = ProjectGenerator(apiService, "test-api-key")
        errorRecovery = ErrorRecovery(projectGenerator)
    }

    @Test
    fun `attemptFix returns failure when max retries exceeded`() = runTest {
        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = mapOf("app/src/main/java/MainActivity.kt" to "package com.test"),
            errorLogs = "error: compilation failed",
            attempt = 4,
            maxRetries = 3
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("exceeded"))
    }

    @Test
    fun `attemptFix returns failure when no failing files identified`() = runTest {
        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = mapOf("app/src/main/java/MainActivity.kt" to "package com.test"),
            errorLogs = "no error here, just normal output",
            attempt = 1,
            maxRetries = 3
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("Could not identify"))
    }

    @Test
    fun `attemptFix identifies Kotlin file from error log`() = runTest {
        val errorLogs = "e: app/src/main/java/com/example/MainActivity.kt:10:5: Unresolved reference: foo"
        val files = mapOf(
            "app/src/main/java/com/example/MainActivity.kt" to "package com.example\nfun main() {}",
            "app/src/main/java/com/example/Theme.kt" to "package com.example\nval theme = {}"
        )

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(
                        message = ChatMessage(
                            role = "assistant",
                            content = "===FILE:app/src/main/java/com/example/MainActivity.kt===\npackage com.example\nfun main() { fixed }\n===END FILE==="
                        )
                    )
                )
            )
        )

        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = files,
            errorLogs = errorLogs,
            attempt = 1,
            maxRetries = 3
        )
        assertTrue(result.success)
        assertTrue(result.fixedFiles.containsKey("app/src/main/java/com/example/MainActivity.kt"))
    }

    @Test
    fun `attemptFix identifies build gradle error`() = runTest {
        val errorLogs = "FAILURE: Build failed with an exception.\n* What went wrong: build.gradle.kts line 10"
        val files = mapOf(
            "app/build.gradle.kts" to "plugins { id(\"com.android.application\") }",
            "build.gradle.kts" to "plugins { }"
        )

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(
                        message = ChatMessage(
                            role = "assistant",
                            content = "===FILE:app/build.gradle.kts===\nfixed content\n===END FILE==="
                        )
                    )
                )
            )
        )

        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = files,
            errorLogs = errorLogs,
            attempt = 1,
            maxRetries = 3
        )
        assertTrue(result.success)
    }

    @Test
    fun `attemptFix identifies manifest error`() = runTest {
        val errorLogs = "AndroidManifest.xml:5: error: element activity missing"
        val files = mapOf(
            "app/src/main/AndroidManifest.xml" to "<manifest></manifest>",
            "app/src/main/java/com/example/MainActivity.kt" to "package com.example"
        )

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(
                        message = ChatMessage(
                            role = "assistant",
                            content = "===FILE:app/src/main/AndroidManifest.xml===\n<manifest><application><activity android:name=\".MainActivity\"/></application></manifest>\n===END FILE==="
                        )
                    )
                )
            )
        )

        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = files,
            errorLogs = errorLogs,
            attempt = 1,
            maxRetries = 3
        )
        assertTrue(result.success)
    }

    @Test
    fun `attemptFix falls back to all kotlin files when no specific match`() = runTest {
        val errorLogs = "error: something went wrong but no file reference"
        val files = mapOf(
            "app/src/main/java/com/example/MainActivity.kt" to "package com.example\nfun main() {}",
            "app/src/main/java/com/example/Helper.kt" to "package com.example\nfun helper() {}"
        )

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(
                        message = ChatMessage(
                            role = "assistant",
                            content = "===FILE:app/src/main/java/com/example/MainActivity.kt===\npackage com.example\nfun main() { fixed }\n===END FILE===\n===FILE:app/src/main/java/com/example/Helper.kt===\npackage com.example\nfun helper() { fixed }\n===END FILE==="
                        )
                    )
                )
            )
        )

        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = files,
            errorLogs = errorLogs,
            attempt = 1,
            maxRetries = 3
        )
        assertTrue(result.success)
        assertTrue(result.fixedFiles.size >= 1)
    }

    @Test
    fun `attemptFix handles AI failure gracefully`() = runTest {
        val errorLogs = "e: app/src/main/java/com/example/MainActivity.kt:10: error"
        val files = mapOf("app/src/main/java/com/example/MainActivity.kt" to "package com.example")

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.error(500, ResponseBody.create(null, "Server error"))

        val result = errorRecovery.attemptFix(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = files,
            errorLogs = errorLogs,
            attempt = 1,
            maxRetries = 3
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("failed"))
    }

    @Test
    fun `attemptFix returns correct attempt number`() = runTest {
        val errorLogs = "e: file.kt:1: error"
        val files = mapOf("file.kt" to "content")

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = "===FILE:file.kt===\nfixed\n===END FILE==="))
                )
            )
        )

        val result = errorRecovery.attemptFix("idea", "proj", "com.test", files, errorLogs, 2, 3)
        assertEquals(2, result.attemptNumber)
    }
}
