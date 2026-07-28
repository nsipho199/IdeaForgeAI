package com.ideaforge.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BuildProgress(
    val requestId: String,
    val stage: BuildStage = BuildStage.CONNECTING,
    val progress: Float = 0f,
    val message: String = "",
    val logs: List<String> = emptyList(),
    val estimatedTimeRemaining: Long = 0L,
    val error: String? = null,
    val retryCount: Int = 0,
    val downloadUrl: String? = null,
    val projectDir: String? = null,
    val apkPath: String? = null
)

@Serializable
enum class BuildStage {
    CONNECTING,
    GENERATING_CODE,
    ANALYZING,
    SEARCHING_FIXES,
    CALLING_AI_REPAIR,
    APPLYING_FIX,
    REBUILDING,
    ROLLING_BACK,
    UPLOADING,
    QUEUED,
    BUILDING,
    TESTING,
    SIGNING,
    PACKAGING,
    DOWNLOADING_APK,
    COMPLETED,
    FAILED;

    val displayName: String
        get() = when (this) {
            CONNECTING -> "Connecting"
            GENERATING_CODE -> "Generating Code"
            ANALYZING -> "Analyzing Errors"
            SEARCHING_FIXES -> "Searching Fix Database"
            CALLING_AI_REPAIR -> "Calling AI Repair"
            APPLYING_FIX -> "Applying Fix"
            REBUILDING -> "Rebuilding"
            ROLLING_BACK -> "Rolling Back"
            UPLOADING -> "Uploading"
            QUEUED -> "Queued"
            BUILDING -> "Building"
            TESTING -> "Testing"
            SIGNING -> "Signing APK"
            PACKAGING -> "Packaging APK"
            DOWNLOADING_APK -> "Downloading APK"
            COMPLETED -> "Completed"
            FAILED -> "Failed"
        }

    val icon: String
        get() = when (this) {
            CONNECTING -> "\uD83D\uDD0C"
            GENERATING_CODE -> "\u2328\uFE0F"
            ANALYZING -> "\uD83D\uDD0D"
            SEARCHING_FIXES -> "\uD83D\uDD0E"
            CALLING_AI_REPAIR -> "\uD83E\uDD16"
            APPLYING_FIX -> "\uD83D\uDD27"
            REBUILDING -> "\uD83D\uDD04"
            ROLLING_BACK -> "\u2B05\uFE0F"
            UPLOADING -> "\u2B06\uFE0F"
            QUEUED -> "\u23F3"
            BUILDING -> "\uD83D\uDE80"
            TESTING -> "\uD83D\uDD0D"
            SIGNING -> "\u270D\uFE0F"
            PACKAGING -> "\uD83D\uDCE6"
            DOWNLOADING_APK -> "\uD83D\uDCE5"
            COMPLETED -> "\u2705"
            FAILED -> "\u274C"
        }

    val progressPercent: Float
        get() = when (this) {
            CONNECTING -> 5f
            GENERATING_CODE -> 20f
            ANALYZING -> 25f
            SEARCHING_FIXES -> 28f
            CALLING_AI_REPAIR -> 32f
            APPLYING_FIX -> 35f
            REBUILDING -> 38f
            ROLLING_BACK -> 15f
            UPLOADING -> 40f
            QUEUED -> 45f
            BUILDING -> 55f
            TESTING -> 65f
            SIGNING -> 75f
            PACKAGING -> 85f
            DOWNLOADING_APK -> 90f
            COMPLETED -> 100f
            FAILED -> 0f
        }
}

fun parseBuildStage(stage: String): BuildStage {
    return when (stage.uppercase().replace(" ", "_")) {
        "CONNECTING" -> BuildStage.CONNECTING
        "GENERATING", "GENERATING_CODE", "GENERATED", "PLANNING" -> BuildStage.GENERATING_CODE
        "ANALYZING", "ANALYZING_ERRORS" -> BuildStage.ANALYZING
        "SEARCHING_FIXES", "SEARCHING_FIX_DATABASE" -> BuildStage.SEARCHING_FIXES
        "CALLING_AI_REPAIR", "AI_REPAIR" -> BuildStage.CALLING_AI_REPAIR
        "APPLYING_FIX", "APPLYING" -> BuildStage.APPLYING_FIX
        "REBUILDING", "REBUILDING_PROJECT" -> BuildStage.REBUILDING
        "ROLLING_BACK", "ROLLBACK" -> BuildStage.ROLLING_BACK
        "UPLOADING", "UPLOADING_PROJECT" -> BuildStage.UPLOADING
        "QUEUED", "QUEUED_FOR_BUILD" -> BuildStage.QUEUED
        "BUILDING", "BUILDING_PROJECT", "COMPILING" -> BuildStage.BUILDING
        "TESTING", "RUNNING_TESTS" -> BuildStage.TESTING
        "SIGNING", "SIGNING_APK" -> BuildStage.SIGNING
        "PACKAGING", "PACKAGING_APK" -> BuildStage.PACKAGING
        "DOWNLOADING", "DOWNLOADING_APK" -> BuildStage.DOWNLOADING_APK
        "COMPLETED" -> BuildStage.COMPLETED
        "FAILED", "ERROR" -> BuildStage.FAILED
        else -> BuildStage.CONNECTING
    }
}
