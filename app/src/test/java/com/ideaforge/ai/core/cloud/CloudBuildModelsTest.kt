package com.ideaforge.ai.core.cloud

import org.junit.Assert.*
import org.junit.Test

class CloudBuildModelsTest {

    @Test
    fun `CloudBuildStatus default values`() {
        val status = CloudBuildStatus(
            status = BuildPhase.QUEUED,
            progress = 32f,
            message = "Testing"
        )
        assertEquals(BuildPhase.QUEUED, status.status)
        assertEquals(32f, status.progress)
        assertEquals("Testing", status.message)
        assertNull(status.buildId)
        assertNull(status.logs)
        assertNull(status.error)
    }

    @Test
    fun `CloudBuildStatus with all fields`() {
        val status = CloudBuildStatus(
            status = BuildPhase.COMPLETED,
            progress = 100f,
            message = "Done",
            buildId = "12345",
            logs = "build output",
            error = null
        )
        assertEquals("12345", status.buildId)
        assertEquals("build output", status.logs)
    }

    @Test
    fun `BuildPhase displayName is correct`() {
        assertEquals("Planning your app", BuildPhase.PLANNING.displayName)
        assertEquals("Generating code with AI", BuildPhase.GENERATING.displayName)
        assertEquals("Validating project", BuildPhase.VALIDATING.displayName)
        assertEquals("Uploading to cloud", BuildPhase.UPLOADING.displayName)
        assertEquals("Queued for build", BuildPhase.QUEUED.displayName)
        assertEquals("Compiling code", BuildPhase.BUILDING.displayName)
        assertEquals("Running tests", BuildPhase.TESTING.displayName)
        assertEquals("Signing APK", BuildPhase.SIGNING.displayName)
        assertEquals("Packaging APK", BuildPhase.PACKAGING.displayName)
        assertEquals("Downloading APK", BuildPhase.DOWNLOADING.displayName)
        assertEquals("Build complete", BuildPhase.COMPLETED.displayName)
        assertEquals("Build failed", BuildPhase.FAILED.displayName)
    }

    @Test
    fun `BuildPhase progress values are monotonically increasing`() {
        val phases = listOf(
            BuildPhase.PLANNING,
            BuildPhase.GENERATING,
            BuildPhase.ANALYZING,
            BuildPhase.SEARCHING_FIXES,
            BuildPhase.CALLING_AI_REPAIR,
            BuildPhase.APPLYING_FIX,
            BuildPhase.REBUILDING,
            BuildPhase.VALIDATING,
            BuildPhase.UPLOADING,
            BuildPhase.QUEUED,
            BuildPhase.BUILDING,
            BuildPhase.TESTING,
            BuildPhase.SIGNING,
            BuildPhase.PACKAGING,
            BuildPhase.DOWNLOADING,
            BuildPhase.COMPLETED
        )
        for (i in 1 until phases.size) {
            assertTrue(
                "Progress should increase: ${phases[i-1].displayName}(${phases[i-1].progress}) -> ${phases[i].displayName}(${phases[i].progress})",
                phases[i].progress >= phases[i - 1].progress
            )
        }
    }

    @Test
    fun `BuildPhase COMPLETED has 100 percent progress`() {
        assertEquals(100f, BuildPhase.COMPLETED.progress)
    }

    @Test
    fun `BuildPhase FAILED has 0 progress`() {
        assertEquals(0f, BuildPhase.FAILED.progress)
    }

    @Test
    fun `MAX_BUILD_RETRIES is 10`() {
        assertEquals(10, MAX_BUILD_RETRIES)
    }

    @Test
    fun `BuildAttempt default values`() {
        val attempt = BuildAttempt(attemptNumber = 1, branch = "build-123")
        assertEquals(1, attempt.attemptNumber)
        assertEquals("build-123", attempt.branch)
        assertNull(attempt.buildId)
        assertEquals(BuildPhase.PLANNING, attempt.phase)
        assertTrue(attempt.fixedFiles.isEmpty())
        assertEquals("", attempt.logs)
    }

    @Test
    fun `BuildAttempt with all fields`() {
        val attempt = BuildAttempt(
            attemptNumber = 2,
            branch = "fix-456",
            buildId = "789",
            phase = BuildPhase.BUILDING,
            fixedFiles = listOf("file1.kt", "file2.kt"),
            logs = "build output here"
        )
        assertEquals(2, attempt.attemptNumber)
        assertEquals("fix-456", attempt.branch)
        assertEquals("789", attempt.buildId)
        assertEquals(BuildPhase.BUILDING, attempt.phase)
        assertEquals(2, attempt.fixedFiles.size)
    }

    @Test
    fun `BuildPhase has all expected phases`() {
        val phases = BuildPhase.entries
        assertEquals(18, phases.size)
        assertTrue(phases.contains(BuildPhase.PLANNING))
        assertTrue(phases.contains(BuildPhase.GENERATING))
        assertTrue(phases.contains(BuildPhase.ANALYZING))
        assertTrue(phases.contains(BuildPhase.SEARCHING_FIXES))
        assertTrue(phases.contains(BuildPhase.CALLING_AI_REPAIR))
        assertTrue(phases.contains(BuildPhase.APPLYING_FIX))
        assertTrue(phases.contains(BuildPhase.REBUILDING))
        assertTrue(phases.contains(BuildPhase.ROLLING_BACK))
        assertTrue(phases.contains(BuildPhase.VALIDATING))
        assertTrue(phases.contains(BuildPhase.UPLOADING))
        assertTrue(phases.contains(BuildPhase.QUEUED))
        assertTrue(phases.contains(BuildPhase.BUILDING))
        assertTrue(phases.contains(BuildPhase.TESTING))
        assertTrue(phases.contains(BuildPhase.SIGNING))
        assertTrue(phases.contains(BuildPhase.PACKAGING))
        assertTrue(phases.contains(BuildPhase.DOWNLOADING))
        assertTrue(phases.contains(BuildPhase.COMPLETED))
        assertTrue(phases.contains(BuildPhase.FAILED))
    }
}
