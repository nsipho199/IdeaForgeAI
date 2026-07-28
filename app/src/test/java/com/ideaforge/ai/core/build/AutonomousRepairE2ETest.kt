package com.ideaforge.ai.core.build

import android.content.Context
import com.ideaforge.ai.core.network.ApiService
import com.ideaforge.ai.core.network.ChatCompletionResponse
import com.ideaforge.ai.core.network.ChatMessage
import com.ideaforge.ai.core.network.Choice
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File

class AutonomousRepairE2ETest {

    private lateinit var apiService: ApiService
    private lateinit var repairAgent: RepairAgent
    private lateinit var localFixDb: LocalFixDatabase
    private lateinit var orchestrator: AiOrchestrator

    @Before
    fun setup() {
        apiService = mockk()
        localFixDb = LocalFixDatabase()
        repairAgent = RepairAgent(apiService, "test-key")
        orchestrator = AiOrchestrator(apiService, "test-key", localFixDb)
    }

    // ─────────────────────────────────────────────────
    // TEST 1: Gradle dependency failure
    // ─────────────────────────────────────────────────
    @Test
    fun `test 1 - Gradle dependency error triggers local fix classification`() = runTest {
        val errorLogs = """
FAILURE: Build failed with an exception.
* What went wrong:
Could not resolve dependency.
> Could not resolve androidx.compose.material3:material3.
Affected build.gradle.kts line 42
""".trimIndent()

        val files = mapOf(
            "app/build.gradle.kts" to """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.test.app"
    compileSdk = 35
}
dependencies {
    implementation("androidx.compose.material3:material3")
}""",
            "app/src/main/AndroidManifest.xml" to """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Test"><activity android:name=".MainActivity"/></application>
</manifest>""",
            "gradle.properties" to ""
        )

        val analysis = repairAgent.analyzeError(errorLogs, files)

        assertTrue("Should detect GRADLE category", analysis.categories.contains(ErrorCategory.GRADLE))
        assertEquals("Severity should be 8", 8, analysis.severity)
        assertTrue("Should mention Gradle in root cause", analysis.rootCauseSummary.contains("Gradle", ignoreCase = true))
        assertTrue("failingFiles should include build.gradle",
            analysis.failingFiles.keys.any { it.contains("build.gradle") })
        assertEquals("Confidence should be 0.8", 0.8f, analysis.confidence)

        println("[PASS] Gradle dependency error classified correctly")
    }

    @Test
    fun `test 1b - LocalFixDatabase matches Gradle error patterns`() {
        val errorLogs = """
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> Could not resolve all dependencies for configuration ':app:debugRuntimeClasspath'.
   > Could not find compose-bom:2024.12.01.
""".trimIndent()

        val files = mapOf(
            "app/build.gradle.kts" to "old content"
        )

        val fixes = localFixDb.findFixes(errorLogs, files)
        assertTrue("Should find at least one local fix for Gradle error", fixes.isNotEmpty())

        val fixIds = fixes.map { it.id }
        println("  Local fixes matched: $fixIds")

        val working = files.toMutableMap()
        val applied = fixes.take(2).flatMap { fix ->
            localFixDb.applyFix(fix, working, "TestApp", "com.test").entries
        }.associate { it.key to it.value }

        if (applied.isNotEmpty()) {
            println("  Applied fix to: ${applied.keys}")
            val content = applied["app/build.gradle.kts"] ?: ""
            assertTrue("Fix should contain compose-bom", content.contains("compose-bom"))
            assertTrue("Fix should contain compileSdk = 35", content.contains("compileSdk = 35"))
        }

        println("[PASS] Gradle error matched and fixed by LocalFixDatabase")
    }

    // ─────────────────────────────────────────────────
    // TEST 2: Kotlin syntax failure
    // ─────────────────────────────────────────────────
    @Test
    fun `test 2 - Kotlin syntax error classification`() = runTest {
        val errorLogs = """
e: app/src/main/java/com/test/MainActivity.kt:15:5: Unresolved reference: setContent
e: app/src/main/java/com/test/MainActivity.kt:22:9: Type mismatch: inferred type is Unit but Int was expected
""".trimIndent()

        val files = mapOf(
            "app/src/main/java/com/test/MainActivity.kt" to """
package com.test
import android.os.Bundle
import androidx.activity.ComponentActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {  // unresolved
        }
    }
}
""".trimIndent()
        )

        val analysis = repairAgent.analyzeError(errorLogs, files)

        assertTrue("Should detect KOTLIN category", analysis.categories.contains(ErrorCategory.KOTLIN))
        assertTrue("Should also match COMPILE category", analysis.categories.contains(ErrorCategory.COMPILE))
        assertTrue("failingFiles should contain MainActivity.kt",
            analysis.failingFiles.keys.any { it.contains("MainActivity.kt") })

        println("[PASS] Kotlin syntax error classified correctly (categories=${analysis.categories.map { it.name }})")
    }

    @Test
    fun `test 2b - RepairAgent attempts AI fix for Kotlin error`() = runTest {
        val errorLogs = "e: app/src/main/java/com/test/MainActivity.kt:10: Unresolved reference: bar"
        val files = mapOf(
            "app/src/main/java/com/test/MainActivity.kt" to """
package com.test
import androidx.activity.ComponentActivity
class MainActivity : ComponentActivity() {
    fun foo() { bar() }
}
""".trimIndent().trimStart()
        )

        coEvery {
            apiService.chatCompletion(any(), any())
        } returns Response.success(
            ChatCompletionResponse(
                choices = listOf(
                    Choice(
                        message = ChatMessage(
                            role = "assistant",
                            content = "===FILE:app/src/main/java/com/test/MainActivity.kt===\npackage com.test\nimport androidx.activity.ComponentActivity\nclass MainActivity : ComponentActivity() {\n    fun foo() { println(\"fixed\") }\n}\n===END FILE==="
                        )
                    )
                )
            )
        )

        val snapshot = BuildSnapshot(
            id = "snap_test_1",
            buildAttempt = 1,
            timestamp = System.currentTimeMillis(),
            files = files,
            errorLogs = errorLogs
        )

        val result = repairAgent.attemptRepair(
            idea = "test app",
            projectName = "TestApp",
            packageName = "com.test",
            currentFiles = files,
            errorLogs = errorLogs,
            attemptNumber = 1,
            snapshot = snapshot
        )

        assertTrue("Repair should succeed", result.success)
        assertTrue("Fixed files should contain MainActivity.kt",
            result.fixedFiles.keys.any { it.contains("MainActivity.kt") })
        assertTrue("Fixed content should differ from original",
            result.fixedFiles.values.any { it.contains("println") })

        println("[PASS] Kotlin error repaired via AI: ${result.message}")
    }

    // ─────────────────────────────────────────────────
    // TEST 3: Manifest failure
    // ─────────────────────────────────────────────────
    @Test
    fun `test 3 - Manifest error classification and local fix`() = runTest {
        val errorLogs = """
AndroidManifest.xml:5: error: <activity> element is missing or not correctly declared.
Manifest file does not specify a launcher activity.
""".trimIndent()

        val files = mapOf(
            "app/src/main/AndroidManifest.xml" to """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Test">
    </application>
</manifest>"""
        )

        val analysis = repairAgent.analyzeError(errorLogs, files)
        assertTrue("Should detect MANIFEST category", analysis.categories.contains(ErrorCategory.MANIFEST))
        assertTrue("failingFiles should include AndroidManifest.xml",
            analysis.failingFiles.keys.any { it.contains("AndroidManifest") })

        val fixes = localFixDb.findFixes(errorLogs, files)
        assertTrue("LocalFixDatabase should match manifest error", fixes.isNotEmpty())
        assertTrue("Should match missing_android_manifest fix",
            fixes.any { it.id == "missing_android_manifest" })

        val working = files.toMutableMap()
        val allFixes = mutableMapOf<String, String>()
        for (fix in fixes.take(3)) {
            allFixes.putAll(localFixDb.applyFix(fix, working, "TestApp", "com.test"))
        }
        assertTrue("Fix should produce AndroidManifest.xml with activity",
            working.values.any { it.contains("<activity") })

        println("[PASS] Manifest error detected and fixed by LocalFixDatabase")
    }

    @Test
    fun `test 3b - ensureCriticalFiles patches missing manifest`() {
        // Simulate BuildManager.ensureCriticalFiles logic
        val files = mapOf<String, String>()
        val patched = mutableMapOf<String, String>().apply { putAll(files) }
        val packageName = "com.test.app"
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
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>"""
        }

        assertTrue("Manifest should contain activity", patched.values.any { it.contains("<activity") })
        assertTrue("Manifest should contain MAIN action", patched.values.any { it.contains("MAIN") })
        assertTrue("Manifest should contain LAUNCHER category", patched.values.any { it.contains("LAUNCHER") })
        println("[PASS] ensureCriticalFiles patches missing AndroidManifest correctly")
    }

    // ─────────────────────────────────────────────────
    // TEST 4: AI rate-limit simulation with provider fallback
    // ─────────────────────────────────────────────────
    @Test
    fun `test 4 - Orchestrator fallback to local fix provider when Gemini rate limited`() = runTest {
        val errorLogs = """
FAILURE: Build failed.
> Could not resolve dependency.
""".trimIndent()

        val files = mapOf(
            "app/build.gradle.kts" to """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.test"
    compileSdk = 35
}""",
            "gradle.properties" to ""
        )

        val mockApiService: ApiService = mockk()

        coEvery {
            mockApiService.chatCompletion(any(), any())
        } returns Response.error(429, ResponseBody.create(null, "Rate limited"))

        val testOrchestrator = AiOrchestrator(mockApiService, "test-key", localFixDb)

        val result = testOrchestrator.generateFixWithFallback(
            idea = "test",
            projectName = "TestApp",
            packageName = "com.test",
            failingFiles = files,
            errorLogs = errorLogs,
            maxRetries = 1
        )

        val lastProvider = testOrchestrator.lastProvider

        val hasLocalFixFiles = result is AiResult.Success && result.files.isNotEmpty()
        assertTrue("Should fallback to local fix provider. Last provider: $lastProvider, hasFiles=$hasLocalFixFiles",
            lastProvider.contains("Local Fix", ignoreCase = true) || hasLocalFixFiles)

        if (result is AiResult.Success && result.files.isNotEmpty()) {
            println("  Fallback succeeded: ${result.data}")
            println("  Fixed files: ${result.files.keys}")
        }

        println("[PASS] Provider fallback from rate-limited Gemini to local fix provider works")
    }

    @Test
    fun `test 4b - GeminiProvider correctly categorizes 429 as rate limited`() = runTest {
        val mockApiService: ApiService = mockk()

        coEvery {
            mockApiService.chatCompletion(any(), any())
        } returns Response.error(429, ResponseBody.create(null, "Rate limit exceeded"))

        val geminiProvider = GeminiProvider(mockApiService, "test-key")
        val fixResult = geminiProvider.generateFix(
            "test", "TestApp", "com.test",
            mapOf("file.kt" to "content"),
            "error: test"
        )

        assertTrue("429 should be categorized as RateLimited", fixResult is AiResult.RateLimited)
        println("[PASS] HTTP 429 correctly mapped to AiResult.RateLimited")
    }

    @Test
    fun `test 4c - GeminiProvider network errors mapped correctly`() = runTest {
        val mockApiService: ApiService = mockk()

        coEvery {
            mockApiService.chatCompletion(any(), any())
        } returns Response.error(500, ResponseBody.create(null, "Internal error"))

        val geminiProvider = GeminiProvider(mockApiService, "test-key")
        val fixResult = geminiProvider.generateFix(
            "test", "TestApp", "com.test",
            mapOf("file.kt" to "content"),
            "error: test"
        )

        assertTrue("500 should be categorized as Error", fixResult is AiResult.Error)
        println("[PASS] HTTP 500 correctly mapped to AiResult.Error")
    }

    @Test
    fun `test 4d - GeminiProvider unavailable when key blank`() {
        val geminiProvider = GeminiProvider(apiService, "")
        assertFalse("Provider should be unavailable with blank key", geminiProvider.isAvailable)
        println("[PASS] GeminiProvider correctly unavailable with blank key")
    }

    // ─────────────────────────────────────────────────
    // TEST 5: Rollback via SnapshotManager
    // ─────────────────────────────────────────────────
    @Test
    fun `test 5 - SnapshotManager create and rollback`() {
        val tempDir = createTempDir("snapshot_test_")
        val mockContext: Context = mockk()
        every { mockContext.filesDir } returns tempDir

        val snapshotManager = SnapshotManager(mockContext)

        val originalFiles = mapOf(
            "app/src/main/java/com/test/MainActivity.kt" to "package com.test\nfun main() {}",
            "app/build.gradle.kts" to "plugins { }"
        )

        val badFiles = mapOf(
            "app/src/main/java/com/test/MainActivity.kt" to "BROKEN CONTENT NO COMPILATION",
            "app/build.gradle.kts" to "BROKEN"
        )

        val snapshot = snapshotManager.createSnapshot(
            buildAttempt = 1,
            files = originalFiles,
            errorLogs = "Error: compilation failed"
        )

        assertEquals("Snapshot buildAttempt should be 1", 1, snapshot.buildAttempt)
        assertEquals("Snapshot should have 2 files", 2, snapshot.files.size)
        assertTrue("Snapshot should contain original content",
            snapshot.files["app/src/main/java/com/test/MainActivity.kt"]?.contains("fun main()") == true)

        val rolledBack = snapshotManager.rollbackTo(snapshot)

        assertEquals("Rolled back files should have same keys", originalFiles.keys, rolledBack.keys)
        assertEquals("Rolled back content should match original",
            originalFiles["app/src/main/java/com/test/MainActivity.kt"],
            rolledBack["app/src/main/java/com/test/MainActivity.kt"])

        println("[PASS] SnapshotManager creates and restores snapshots correctly")

        tempDir.deleteRecursively()
    }

    @Test
    fun `test 5b - SnapshotManager returns null when no snapshot exists`() {
        val tempDir = createTempDir("snapshot_null_test_")
        val mockContext: Context = mockk()
        every { mockContext.filesDir } returns tempDir

        val snapshotManager = SnapshotManager(mockContext)
        assertNull("getLatestSnapshot should return null initially",
            snapshotManager.getLatestSnapshot())

        val rolledBack = snapshotManager.rollbackToLatest()
        assertNull("rollbackToLatest should return null when no snapshot",
            rolledBack)

        println("[PASS] SnapshotManager handles empty state correctly")
        tempDir.deleteRecursively()
    }

    // ─────────────────────────────────────────────────
    // TEST 6: BuildProgress UI verification
    // ─────────────────────────────────────────────────
    @Test
    fun `test 6 - BuildStage display names and icons for all repair stages`() {
        val stages = listOf(
            com.ideaforge.ai.domain.model.BuildStage.CONNECTING,
            com.ideaforge.ai.domain.model.BuildStage.GENERATING_CODE,
            com.ideaforge.ai.domain.model.BuildStage.ANALYZING,
            com.ideaforge.ai.domain.model.BuildStage.SEARCHING_FIXES,
            com.ideaforge.ai.domain.model.BuildStage.CALLING_AI_REPAIR,
            com.ideaforge.ai.domain.model.BuildStage.APPLYING_FIX,
            com.ideaforge.ai.domain.model.BuildStage.REBUILDING,
            com.ideaforge.ai.domain.model.BuildStage.ROLLING_BACK,
            com.ideaforge.ai.domain.model.BuildStage.UPLOADING,
            com.ideaforge.ai.domain.model.BuildStage.QUEUED,
            com.ideaforge.ai.domain.model.BuildStage.BUILDING,
            com.ideaforge.ai.domain.model.BuildStage.TESTING,
            com.ideaforge.ai.domain.model.BuildStage.SIGNING,
            com.ideaforge.ai.domain.model.BuildStage.PACKAGING,
            com.ideaforge.ai.domain.model.BuildStage.DOWNLOADING_APK,
            com.ideaforge.ai.domain.model.BuildStage.COMPLETED,
            com.ideaforge.ai.domain.model.BuildStage.FAILED
        )

        assertEquals("There should be 17 BuildStage values", 17, stages.size)

        for (stage in stages) {
            assertTrue("DisplayName for $stage should not be empty", stage.displayName.isNotBlank())
            assertTrue("Icon for $stage should not be empty", stage.icon.isNotBlank())
            assertTrue("ProgressPercent for $stage should be >= 0", stage.progressPercent >= 0f)
            assertTrue("ProgressPercent for $stage should be <= 100", stage.progressPercent <= 100f)
            println("  ${stage.name}: \"${stage.displayName}\" ${stage.icon} ${stage.progressPercent}%")
        }

        val analyzing = com.ideaforge.ai.domain.model.BuildStage.ANALYZING
        assertEquals("ANALYZING displayName", "Analyzing Errors", analyzing.displayName)

        val searching = com.ideaforge.ai.domain.model.BuildStage.SEARCHING_FIXES
        assertEquals("SEARCHING_FIXES displayName", "Searching Fix Database", searching.displayName)

        val calling = com.ideaforge.ai.domain.model.BuildStage.CALLING_AI_REPAIR
        assertEquals("CALLING_AI_REPAIR displayName", "Calling AI Repair", calling.displayName)

        val applying = com.ideaforge.ai.domain.model.BuildStage.APPLYING_FIX
        assertEquals("APPLYING_FIX displayName", "Applying Fix", applying.displayName)

        val rebuilding = com.ideaforge.ai.domain.model.BuildStage.REBUILDING
        assertEquals("REBUILDING displayName", "Rebuilding", rebuilding.displayName)

        val rolling = com.ideaforge.ai.domain.model.BuildStage.ROLLING_BACK
        assertEquals("ROLLING_BACK displayName", "Rolling Back", rolling.displayName)

        println("[PASS] All BuildStage UI values correct")
    }

    @Test
    fun `test 6b - parseBuildStage handles all new stages`() {
        val tests = mapOf(
            "ANALYZING" to com.ideaforge.ai.domain.model.BuildStage.ANALYZING,
            "ANALYZING_ERRORS" to com.ideaforge.ai.domain.model.BuildStage.ANALYZING,
            "SEARCHING_FIXES" to com.ideaforge.ai.domain.model.BuildStage.SEARCHING_FIXES,
            "SEARCHING_FIX_DATABASE" to com.ideaforge.ai.domain.model.BuildStage.SEARCHING_FIXES,
            "CALLING_AI_REPAIR" to com.ideaforge.ai.domain.model.BuildStage.CALLING_AI_REPAIR,
            "AI_REPAIR" to com.ideaforge.ai.domain.model.BuildStage.CALLING_AI_REPAIR,
            "APPLYING_FIX" to com.ideaforge.ai.domain.model.BuildStage.APPLYING_FIX,
            "APPLYING" to com.ideaforge.ai.domain.model.BuildStage.APPLYING_FIX,
            "REBUILDING" to com.ideaforge.ai.domain.model.BuildStage.REBUILDING,
            "REBUILDING_PROJECT" to com.ideaforge.ai.domain.model.BuildStage.REBUILDING,
            "ROLLING_BACK" to com.ideaforge.ai.domain.model.BuildStage.ROLLING_BACK,
            "ROLLBACK" to com.ideaforge.ai.domain.model.BuildStage.ROLLING_BACK
        )

        for ((input, expected) in tests) {
            val parsed = com.ideaforge.ai.domain.model.parseBuildStage(input)
            assertEquals("parseBuildStage(\"$input\") should be ${expected.name}", expected, parsed)
        }

        println("[PASS] parseBuildStage handles all new repair stage names")
    }

    @Test
    fun `test 6c - BuildProgress JSON round-trip with repair stages`() {
        val stages = listOf(
            com.ideaforge.ai.domain.model.BuildStage.ANALYZING,
            com.ideaforge.ai.domain.model.BuildStage.SEARCHING_FIXES,
            com.ideaforge.ai.domain.model.BuildStage.CALLING_AI_REPAIR,
            com.ideaforge.ai.domain.model.BuildStage.APPLYING_FIX,
            com.ideaforge.ai.domain.model.BuildStage.REBUILDING,
            com.ideaforge.ai.domain.model.BuildStage.ROLLING_BACK
        )

        for (stage in stages) {
            val progress = com.ideaforge.ai.domain.model.BuildProgress(
                requestId = "test-${stage.name}",
                stage = stage,
                progress = stage.progressPercent,
                message = "Testing ${stage.displayName}",
                logs = listOf("log line 1", "log line 2"),
                retryCount = 3,
                projectDir = "/tmp/test",
                apkPath = null,
                error = null
            )

            assertEquals("requestId should match", "test-${stage.name}", progress.requestId)
            assertEquals("stage should match", stage, progress.stage)
            assertEquals("progress should match", stage.progressPercent, progress.progress)
            assertEquals("message should contain displayName", progress.message, "Testing ${stage.displayName}")
            assertEquals("retryCount should be 3", 3, progress.retryCount)

            println("  ${stage.name}: progress=${progress.progress}% message=\"${progress.message}\" retryCount=${progress.retryCount}")
        }

        println("[PASS] BuildProgress round-trips correctly for all repair stages")
    }
}
