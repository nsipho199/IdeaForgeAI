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

class ProjectGeneratorTest {

    private lateinit var apiService: ApiService
    private lateinit var generator: ProjectGenerator

    @Before
    fun setup() {
        apiService = mockk()
        generator = ProjectGenerator(apiService, "test-api-key")
    }

    @Test
    fun `generateProject returns files from AI response`() = runTest {
        val aiResponse = """
            ===FILE:app/src/main/java/com/example/MainActivity.kt===
            package com.example
            
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                }
            }
            ===END FILE===
            ===FILE:build.gradle.kts===
            plugins {
                id("com.android.application") version "8.7.3" apply false
            }
            ===END FILE===
        """.trimIndent()

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = aiResponse))
                )
            )
        )

        val result = generator.generateProject("Hello World app", "HelloWorld", "com.example.hello")
        assertTrue(result.isSuccess)
        val files = result.getOrDefault(emptyMap())
        assertEquals(2, files.size)
        assertTrue(files.containsKey("app/src/main/java/com/example/MainActivity.kt"))
        assertTrue(files.containsKey("build.gradle.kts"))
        assertTrue(files["app/src/main/java/com/example/MainActivity.kt"]!!.contains("package com.example"))
    }

    @Test
    fun `generateProject returns failure on empty response`() = runTest {
        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(choices = emptyList())
        )

        val result = generator.generateProject("test", "TestApp", "com.test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Empty") == true)
    }

    @Test
    fun `generateProject returns failure on HTTP error`() = runTest {
        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.error(500, ResponseBody.create(null, "error"))

        val result = generator.generateProject("test", "TestApp", "com.test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `generateProject returns failure when no files parsed`() = runTest {
        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = "Here is some text but no file markers"))
                )
            )
        )

        val result = generator.generateProject("test", "TestApp", "com.test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No files") == true)
    }

    @Test
    fun `generateFix returns fixed files`() = runTest {
        val fixResponse = """
            ===FILE:app/src/main/java/com/example/MainActivity.kt===
            package com.example
            
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    // Fixed!
                }
            }
            ===END FILE===
        """.trimIndent()

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = fixResponse))
                )
            )
        )

        val failingFiles = mapOf(
            "app/src/main/java/com/example/MainActivity.kt" to "package com.example\nbroken code"
        )

        val result = generator.generateFix("test app", "TestApp", "com.test", failingFiles, "error: broken code")
        assertTrue(result.isSuccess)
        val files = result.getOrDefault(emptyMap())
        assertEquals(1, files.size)
        assertTrue(files["app/src/main/java/com/example/MainActivity.kt"]!!.contains("Fixed!"))
    }

    @Test
    fun `generateFix handles multiple files`() = runTest {
        val fixResponse = """
            ===FILE:file1.kt===
            fixed content 1
            ===END FILE===
            ===FILE:file2.kt===
            fixed content 2
            ===END FILE===
        """.trimIndent()

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = fixResponse))
                )
            )
        )

        val failingFiles = mapOf("file1.kt" to "broken1", "file2.kt" to "broken2")
        val result = generator.generateFix("test", "Test", "com.test", failingFiles, "errors")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrDefault(emptyMap()).size)
    }

    @Test
    fun `generateFix returns failure on AI error`() = runTest {
        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.error(429, ResponseBody.create(null, "rate limited"))

        val result = generator.generateFix("test", "Test", "com.test", mapOf("file.kt" to "content"), "error")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseGeneratedFiles handles files with special characters`() = runTest {
        val aiResponse = """
            ===FILE:app/src/main/java/com/example/MainActivity.kt===
            package com.example
            
            val message = "Hello, World! @#\$%^&*()"
            val regex = "\\d+".toRegex()
            ===END FILE===
        """.trimIndent()

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = aiResponse))
                )
            )
        )

        val result = generator.generateProject("test", "Test", "com.test")
        assertTrue(result.isSuccess)
        val content = result.getOrDefault(emptyMap()).values.first()
        assertTrue(content.contains("Hello, World!"))
    }

    @Test
    fun `generateProject uses correct model`() = runTest {
        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(message = ChatMessage(role = "assistant", content = "===FILE:test.kt===\ncontent\n===END FILE==="))
                )
            )
        )

        generator.generateProject("test", "Test", "com.test")

        io.mockk.verify {
            apiService.chatCompletion(
                authorization = match { it.startsWith("Bearer ") },
                request = match { it.model == "big-pickle" }
            )
        }
    }
}
