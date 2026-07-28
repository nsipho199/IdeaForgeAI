package com.ideaforge.ai.domain.model

import org.junit.Assert.*
import org.junit.Test

class BuildProgressTest {

    @Test
    fun `BuildProgress default values are correct`() {
        val progress = BuildProgress(requestId = "test-123")
        assertEquals("test-123", progress.requestId)
        assertEquals(BuildStage.CONNECTING, progress.stage)
        assertEquals(0f, progress.progress)
        assertEquals("", progress.message)
        assertTrue(progress.logs.isEmpty())
        assertNull(progress.error)
        assertEquals(0, progress.retryCount)
        assertNull(progress.downloadUrl)
        assertNull(progress.projectDir)
        assertNull(progress.apkPath)
    }

    @Test
    fun `BuildProgress copy works correctly`() {
        val original = BuildProgress(requestId = "1", stage = BuildStage.BUILDING, progress = 50f)
        val copied = original.copy(stage = BuildStage.COMPLETED, progress = 100f)
        assertEquals("1", copied.requestId)
        assertEquals(BuildStage.COMPLETED, copied.stage)
        assertEquals(100f, copied.progress)
    }

    @Test
    fun `BuildStage displayName is correct for all stages`() {
        assertEquals("Connecting", BuildStage.CONNECTING.displayName)
        assertEquals("Generating Code", BuildStage.GENERATING_CODE.displayName)
        assertEquals("Uploading", BuildStage.UPLOADING.displayName)
        assertEquals("Queued", BuildStage.QUEUED.displayName)
        assertEquals("Building", BuildStage.BUILDING.displayName)
        assertEquals("Testing", BuildStage.TESTING.displayName)
        assertEquals("Signing APK", BuildStage.SIGNING.displayName)
        assertEquals("Packaging APK", BuildStage.PACKAGING.displayName)
        assertEquals("Downloading APK", BuildStage.DOWNLOADING_APK.displayName)
        assertEquals("Completed", BuildStage.COMPLETED.displayName)
        assertEquals("Failed", BuildStage.FAILED.displayName)
    }

    @Test
    fun `BuildStage progressPercent is correct`() {
        assertEquals(5f, BuildStage.CONNECTING.progressPercent)
        assertEquals(20f, BuildStage.GENERATING_CODE.progressPercent)
        assertEquals(30f, BuildStage.UPLOADING.progressPercent)
        assertEquals(35f, BuildStage.QUEUED.progressPercent)
        assertEquals(50f, BuildStage.BUILDING.progressPercent)
        assertEquals(65f, BuildStage.TESTING.progressPercent)
        assertEquals(75f, BuildStage.SIGNING.progressPercent)
        assertEquals(85f, BuildStage.PACKAGING.progressPercent)
        assertEquals(90f, BuildStage.DOWNLOADING_APK.progressPercent)
        assertEquals(100f, BuildStage.COMPLETED.progressPercent)
        assertEquals(0f, BuildStage.FAILED.progressPercent)
    }

    @Test
    fun `BuildStage icon is not empty`() {
        BuildStage.entries.forEach { stage ->
            assertNotNull("Icon for $stage should not be null", stage.icon)
            assertTrue("Icon for $stage should not be empty", stage.icon.isNotEmpty())
        }
    }

    @Test
    fun `parseBuildStage handles standard names`() {
        assertEquals(BuildStage.CONNECTING, parseBuildStage("CONNECTING"))
        assertEquals(BuildStage.GENERATING_CODE, parseBuildStage("GENERATING"))
        assertEquals(BuildStage.GENERATING_CODE, parseBuildStage("GENERATING_CODE"))
        assertEquals(BuildStage.UPLOADING, parseBuildStage("UPLOADING"))
        assertEquals(BuildStage.QUEUED, parseBuildStage("QUEUED"))
        assertEquals(BuildStage.BUILDING, parseBuildStage("BUILDING"))
        assertEquals(BuildStage.TESTING, parseBuildStage("TESTING"))
        assertEquals(BuildStage.SIGNING, parseBuildStage("SIGNING"))
        assertEquals(BuildStage.PACKAGING, parseBuildStage("PACKAGING"))
        assertEquals(BuildStage.DOWNLOADING_APK, parseBuildStage("DOWNLOADING"))
        assertEquals(BuildStage.DOWNLOADING_APK, parseBuildStage("DOWNLOADING_APK"))
        assertEquals(BuildStage.COMPLETED, parseBuildStage("COMPLETED"))
        assertEquals(BuildStage.FAILED, parseBuildStage("FAILED"))
    }

    @Test
    fun `parseBuildStage handles aliases`() {
        assertEquals(BuildStage.GENERATING_CODE, parseBuildStage("PLANNING"))
        assertEquals(BuildStage.GENERATING_CODE, parseBuildStage("GENERATED"))
        assertEquals(BuildStage.BUILDING, parseBuildStage("COMPILING"))
        assertEquals(BuildStage.BUILDING, parseBuildStage("BUILDING_PROJECT"))
        assertEquals(BuildStage.TESTING, parseBuildStage("RUNNING_TESTS"))
        assertEquals(BuildStage.FAILED, parseBuildStage("ERROR"))
    }

    @Test
    fun `parseBuildStage handles case insensitive`() {
        assertEquals(BuildStage.CONNECTING, parseBuildStage("connecting"))
        assertEquals(BuildStage.BUILDING, parseBuildStage("Building"))
        assertEquals(BuildStage.COMPLETED, parseBuildStage("completed"))
    }

    @Test
    fun `parseBuildStage handles space-separated names`() {
        assertEquals(BuildStage.GENERATING_CODE, parseBuildStage("GENERATING CODE"))
        assertEquals(BuildStage.UPLOADING, parseBuildStage("UPLOADING PROJECT"))
    }

    @Test
    fun `parseBuildStage defaults to CONNECTING for unknown`() {
        assertEquals(BuildStage.CONNECTING, parseBuildStage("UNKNOWN_STAGE"))
        assertEquals(BuildStage.CONNECTING, parseBuildStage(""))
    }

    @Test
    fun `BuildProgress with all fields`() {
        val progress = BuildProgress(
            requestId = "req-1",
            stage = BuildStage.COMPLETED,
            progress = 100f,
            message = "Done!",
            logs = listOf("line1", "line2"),
            estimatedTimeRemaining = 0L,
            error = null,
            retryCount = 2,
            downloadUrl = "https://example.com/apk",
            projectDir = "/storage/.../project",
            apkPath = "/storage/.../app.apk"
        )
        assertEquals(2, progress.logs.size)
        assertEquals(2, progress.retryCount)
        assertEquals("https://example.com/apk", progress.downloadUrl)
    }
}
