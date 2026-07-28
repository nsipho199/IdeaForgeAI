package com.ideaforge.ai.core.cloud

import kotlinx.serialization.Serializable

data class CloudBuildStatus(
    val status: BuildPhase,
    val progress: Float,
    val message: String,
    val buildId: String? = null,
    val logs: String? = null,
    val error: String? = null
)

enum class BuildPhase(val displayName: String, val progress: Float) {
    PLANNING("Planning your app", 5f),
    GENERATING("Generating code with AI", 10f),
    ANALYZING("Analyzing build errors", 15f),
    SEARCHING_FIXES("Searching local fix database", 18f),
    CALLING_AI_REPAIR("Calling AI for repair", 22f),
    APPLYING_FIX("Applying fix to project", 25f),
    REBUILDING("Rebuilding with fixes applied", 28f),
    ROLLING_BACK("Rolling back bad changes", 12f),
    VALIDATING("Validating project", 18f),
    UPLOADING("Uploading to cloud", 25f),
    QUEUED("Queued for build", 32f),
    BUILDING("Compiling code", 45f),
    TESTING("Running tests", 60f),
    SIGNING("Signing APK", 72f),
    PACKAGING("Packaging APK", 82f),
    DOWNLOADING("Downloading APK", 90f),
    COMPLETED("Build complete", 100f),
    FAILED("Build failed", 0f)
}

@Serializable
data class BuildAttempt(
    val attemptNumber: Int,
    val branch: String,
    val buildId: String? = null,
    val phase: BuildPhase = BuildPhase.PLANNING,
    val fixedFiles: List<String> = emptyList(),
    val logs: String = ""
)

const val MAX_BUILD_RETRIES = 10

enum class TokenType(val displayName: String) {
    CLASSIC("Classic PAT"),
    FINE_GRAINED("Fine-grained PAT"),
    OAUTH("OAuth"),
    UNKNOWN("Unknown")
}

data class DiagnosticStep(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class TokenValidation(
    val valid: Boolean,
    val username: String? = null,
    val error: String? = null,
    val canCreateRepos: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val tokenType: TokenType = TokenType.UNKNOWN,
    val scopes: List<String> = emptyList(),
    val scopesRaw: String = "",
    val authHeaderFormat: String = "",
    val repoCreationEndpoint: String = "",
    val diagnostics: List<DiagnosticStep> = emptyList()
)
