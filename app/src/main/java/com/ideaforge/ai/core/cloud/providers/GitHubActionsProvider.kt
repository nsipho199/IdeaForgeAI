package com.ideaforge.ai.core.cloud.providers

import android.util.Log
import com.ideaforge.ai.core.cloud.CloudBuildProvider
import com.ideaforge.ai.core.cloud.CloudBuildStatus
import com.ideaforge.ai.core.cloud.BuildPhase
import com.ideaforge.ai.core.cloud.DiagnosticStep
import com.ideaforge.ai.core.cloud.TokenType
import com.ideaforge.ai.core.cloud.TokenValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

private const val TAG = "GitHubAPI"

class GitHubActionsProvider(
    token: String,
    owner: String,
    private val repo: String
) : CloudBuildProvider {

    override val name = "GitHub Actions"
    private val apiBase = "https://api.github.com"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val token: String = token
    private var owner: String = owner

    private data class HttpResponse(val code: Int, val body: String, val headers: Map<String, String>)

    override suspend fun validateToken(): TokenValidation = withContext(Dispatchers.IO) {
        val diagnostics = mutableListOf<DiagnosticStep>()
        val tokenPrefix = if (token.length > 7) token.take(7) else token
        logSafe("Starting validation (token prefix: $tokenPrefix...)")

        try {
            warmUpConnection("$apiBase/user")

            logRequest("GET", "/user")
            val (userCode, userBody, userHeaders) = httpGetWithHeaders("$apiBase/user")
            logResponse("GET", "/user", userCode, userBody, userHeaders)

            diagnostics.add(DiagnosticStep(
                name = "Authentication",
                passed = userCode == 200,
                detail = when (userCode) {
                    200 -> "Token is valid"
                    401 -> "Token is invalid or expired (HTTP 401)"
                    403 -> "Token access denied (HTTP 403) — ${parseGitHubError(userBody)}"
                    else -> "Unexpected response: HTTP $userCode"
                }
            ))

            if (userCode == 401) {
                return@withContext buildInvalidResult(
                    "GitHub token is invalid or expired.",
                    userBody, diagnostics, TokenType.UNKNOWN, emptyList()
                )
            }
            if (userCode == 403) {
                val apiError = parseGitHubError(userBody)
                val isSaml = apiError.lowercase().contains("saml") || apiError.lowercase().contains("sso")
                val detail = if (isSaml) "SAML/SSO authorization required — $apiError" else apiError
                return@withContext buildInvalidResult(
                    "GitHub token access denied.",
                    userBody, diagnostics, detectTokenType(userHeaders),
                    listOf(DiagnosticStep("SAML/SSO check", isSaml, detail))
                )
            }
            if (userCode != 200) {
                return@withContext buildInvalidResult(
                    "GitHub API returned HTTP $userCode.",
                    userBody, diagnostics, TokenType.UNKNOWN, emptyList()
                )
            }

            val userObj = json.parseToJsonElement(userBody).jsonObject
            val username = userObj["login"]?.jsonPrimitive?.content
                ?: return@withContext buildInvalidResult(
                    "Could not read GitHub username.",
                    userBody, diagnostics, TokenType.UNKNOWN, emptyList()
                )
            val accountType = userObj["type"]?.jsonPrimitive?.content ?: "User"
            val isOrg = accountType.equals("Organization", ignoreCase = true)

            val tokenType = detectTokenType(userHeaders)
            diagnostics.add(DiagnosticStep(
                name = "Token type",
                passed = true,
                detail = tokenType.displayName
            ))

            val scopesRaw = userHeaders["x-oauth-scopes"] ?: ""
            val acceptedScopesRaw = userHeaders["x-accepted-oauth-scopes"] ?: ""
            val scopes = scopesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val hasRepoScope = scopes.any { it == "repo" || it.startsWith("repo:") }
            val hasWorkflowScope = scopes.any { it == "workflow" || it.startsWith("workflow:") }

            diagnostics.add(DiagnosticStep(
                name = "Scopes",
                passed = scopes.isNotEmpty(),
                detail = if (scopes.isEmpty()) {
                    "No classic scopes detected. This may be a fine-grained token or OAuth token."
                } else {
                    "Scopes: ${scopes.joinToString(", ")}"
                }
            ))

            diagnostics.add(DiagnosticStep(
                name = "repo scope",
                passed = hasRepoScope,
                detail = if (hasRepoScope) "Present" else "Missing — needed to create repositories and manage files"
            ))

            diagnostics.add(DiagnosticStep(
                name = "workflow scope",
                passed = hasWorkflowScope,
                detail = if (hasWorkflowScope) "Present" else "Missing — needed to trigger GitHub Actions workflows"
            ))

            if (!hasRepoScope || !hasWorkflowScope) {
                val missing = mutableListOf<String>()
                if (!hasRepoScope) missing.add("repo")
                if (!hasWorkflowScope) missing.add("workflow")
                return@withContext TokenValidation(
                    valid = true,
                    username = username,
                    canCreateRepos = false,
                    missingPermissions = missing,
                    tokenType = tokenType,
                    scopes = scopes,
                    scopesRaw = scopesRaw,
                    authHeaderFormat = "token",
                    diagnostics = diagnostics,
                    error = buildString {
                        appendLine("Token is valid (connected as: @$username) but missing required scopes.")
                        appendLine()
                        appendLine("Your token has: ${scopesRaw.ifBlank { "(none detected)" }}")
                        appendLine("Required: repo, workflow")
                        appendLine("Missing: ${missing.joinToString(", ")}")
                        appendLine()
                        if (tokenType == TokenType.FINE_GRAINED) {
                            appendLine("This is a fine-grained token. Classic PATs are recommended for IdeaForge.")
                        }
                        appendLine("Fix: Generate a new classic token at github.com → Settings → Developer settings → Personal access tokens → Tokens (classic)")
                        appendLine("Check 'repo' and 'workflow' scopes.")
                    }
                )
            }

            logRequest("POST", "/user/repos (permission test)")
            val (repoTestCode, repoTestBody, repoTestHeaders) = httpPostWithHeaders(
                "$apiBase/user/repos",
                """{"name":"__ideaforge_permission_test__","auto_init":false,"private":true}"""
            )
            logResponse("POST", "/user/repos (test)", repoTestCode, repoTestBody, repoTestHeaders)

            diagnostics.add(DiagnosticStep(
                name = "Repository creation",
                passed = repoTestCode in 200..299,
                detail = when {
                    repoTestCode in 200..299 -> "Test repository created and will be deleted"
                    repoTestCode == 403 -> "Denied (HTTP 403) — ${parseGitHubError(repoTestBody)}"
                    repoTestCode == 422 -> "Failed (HTTP 422) — ${parseGitHubError(repoTestBody)}"
                    repoTestCode == 429 -> "Rate limited (HTTP 429)"
                    else -> "HTTP $repoTestCode — ${parseGitHubError(repoTestBody)}"
                }
            ))

            if (repoTestCode in 200..299) {
                val createdName = json.parseToJsonElement(repoTestBody).jsonObject["name"]?.jsonPrimitive?.content
                if (createdName != null) {
                    httpDelete("$apiBase/repos/$username/$createdName")
                    logSafe("Deleted test repo: $createdName")
                }
                return@withContext TokenValidation(
                    valid = true,
                    username = username,
                    canCreateRepos = true,
                    tokenType = tokenType,
                    scopes = scopes,
                    scopesRaw = scopesRaw,
                    authHeaderFormat = "token",
                    diagnostics = diagnostics
                )
            }

            val apiError = parseGitHubError(repoTestBody)
            val repoDiagnosis = diagnoseRepoCreationFailure(repoTestCode, repoTestBody, apiError, username)
            diagnostics.add(repoDiagnosis.second)

            val endpoint = if (isOrg) "POST /orgs/{org}/repos" else "POST /user/repos"

            return@withContext TokenValidation(
                valid = true,
                username = username,
                canCreateRepos = false,
                missingPermissions = listOf(repoDiagnosis.first),
                tokenType = tokenType,
                scopes = scopes,
                scopesRaw = scopesRaw,
                authHeaderFormat = "token",
                repoCreationEndpoint = endpoint,
                diagnostics = diagnostics,
                error = buildString {
                    appendLine("Token is valid (connected as: @$username) but repository creation failed.")
                    appendLine()
                    appendLine("Token type: ${tokenType.displayName}")
                    appendLine("Account type: $accountType")
                    appendLine("Scopes: ${scopesRaw.ifBlank { "(none detected)" }}")
                    appendLine("API endpoint: $endpoint")
                    appendLine("HTTP status: $repoTestCode")
                    appendLine("GitHub says: $apiError")
                    appendLine()
                    appendLine(repoDiagnosis.first)
                }
            )
        } catch (e: Exception) {
            logSafe("Exception: ${e.javaClass.simpleName}: ${e.message}")
            diagnostics.add(DiagnosticStep(
                name = "Network",
                passed = false,
                detail = "${e.javaClass.simpleName}: ${e.message}"
            ))
            val msg = e.message?.lowercase() ?: ""
            val cause = e.cause?.message?.lowercase() ?: ""
            val combined = "$msg $cause"
            val error = when {
                combined.contains("timeout") || combined.contains("timed out") ->
                    "Network timeout connecting to GitHub.\n\n" +
                    "Possible causes:\n" +
                    "  - Slow or unstable internet connection\n" +
                    "  - Firewall blocking api.github.com\n" +
                    "  - VPN or proxy interfering\n\n" +
                    "Fix: Check your network, disable VPN/proxy, or try a different network.\n" +
                    "Use 'Network Diagnostics' in Settings to test connectivity."
                combined.contains("unresolved host") || combined.contains("unknown host") ->
                    "Cannot resolve api.github.com (DNS failure).\n\n" +
                    "Possible causes:\n" +
                    "  - No internet connection\n" +
                    "  - Private DNS blocking GitHub\n" +
                    "  - Firewall blocking DNS queries\n\n" +
                    "Fix: Check internet, disable Private DNS (Settings > Network > Private DNS), or try cellular data.\n" +
                    "Use 'Network Diagnostics' in Settings to test DNS."
                combined.contains("connection refused") ->
                    "Connection refused by api.github.com.\n\n" +
                    "Possible causes:\n" +
                    "  - GitHub is blocking this connection\n" +
                    "  - Firewall or proxy is intercepting\n" +
                    "  - VPN is blocking GitHub\n\n" +
                    "Fix: Disable VPN/proxy/firewall and retry.\n" +
                    "Use 'Network Diagnostics' in Settings to test."
                combined.contains("ssl") || combined.contains("certificate") ->
                    "SSL/TLS error connecting to GitHub.\n\n" +
                    "Possible causes:\n" +
                    "  - Device date/time is incorrect\n" +
                    "  - VPN/proxy intercepting HTTPS\n" +
                    "  - Outdated system certificates\n\n" +
                    "Fix: Check device date/time, disable VPN, or update system."
                combined.contains("connection reset") ->
                    "Connection reset by api.github.com.\n\n" +
                    "Possible causes:\n" +
                    "  - Network instability\n" +
                    "  - Firewall actively blocking\n\n" +
                    "Fix: Try a different network.\n" +
                    "Use 'Network Diagnostics' in Settings to test."
                combined.contains("network unreachable") ->
                    "No network route to api.github.com.\n\n" +
                    "Possible causes:\n" +
                    "  - Airplane mode is on\n" +
                    "  - WiFi/cellular is disconnected\n" +
                    "  - VPN has no route\n\n" +
                    "Fix: Check airplane mode, reconnect to network."
                else -> "Network error connecting to GitHub: ${e.message}\n\n" +
                    "Exception: ${e.javaClass.simpleName}\n" +
                    "Use 'Network Diagnostics' in Settings to test connectivity."
            }
            TokenValidation(valid = false, error = error, diagnostics = diagnostics)
        }
    }

    private fun diagnoseRepoCreationFailure(
        code: Int,
        body: String,
        apiError: String,
        username: String
    ): Pair<String, DiagnosticStep> {
        return when (code) {
            401 -> "Token is invalid or expired." to DiagnosticStep("Repo creation diagnosis", false, "HTTP 401: $apiError")
            403 -> {
                val lower = apiError.lowercase()
                when {
                    lower.contains("saml") || lower.contains("sso") ->
                        "SAML/SSO authorization required. Log in to github.com in a browser, authorize this token for the organization, then retry." to
                            DiagnosticStep("Repo creation diagnosis", false, "SAML/SSO: $apiError")
                    lower.contains("organization") && (lower.contains("policy") || lower.contains("not allowed")) ->
                        "Organization policy blocks repository creation. Contact your organization admin." to
                            DiagnosticStep("Repo creation diagnosis", false, "Org policy: $apiError")
                    lower.contains("rate limit") ->
                        "Rate limit exceeded. Wait a few minutes." to
                            DiagnosticStep("Repo creation diagnosis", false, "Rate limit: $apiError")
                    else ->
                        "Permission denied (HTTP 403). GitHub says: $apiError" to
                            DiagnosticStep("Repo creation diagnosis", false, "HTTP 403: $apiError")
                }
            }
            422 -> {
                val lower = apiError.lowercase()
                when {
                    lower.contains("already exists") || lower.contains("name already") ->
                        "Repository '$repo' already exists under @$username. The app will use it." to
                            DiagnosticStep("Repo creation diagnosis", true, "Repo exists: $apiError")
                    else ->
                        "Invalid request (HTTP 422). GitHub says: $apiError" to
                            DiagnosticStep("Repo creation diagnosis", false, "HTTP 422: $apiError")
                }
            }
            429 -> "Rate limit exceeded. Wait a few minutes." to
                DiagnosticStep("Repo creation diagnosis", false, "HTTP 429: $apiError")
            else -> "Repository creation failed (HTTP $code). GitHub says: $apiError" to
                DiagnosticStep("Repo creation diagnosis", false, "HTTP $code: $apiError")
        }
    }

    private fun detectTokenType(headers: Map<String, String>): TokenType {
        val scopes = headers["x-oauth-scopes"] ?: ""
        val accepted = headers["x-accepted-oauth-scopes"] ?: ""
        return when {
            scopes.isNotBlank() && (scopes.contains("repo") || scopes.contains("gist")) -> TokenType.CLASSIC
            scopes.isNotBlank() -> TokenType.CLASSIC
            accepted.isNotBlank() -> TokenType.FINE_GRAINED
            else -> TokenType.UNKNOWN
        }
    }

    private fun buildInvalidResult(
        error: String,
        body: String,
        diagnostics: MutableList<DiagnosticStep>,
        tokenType: TokenType,
        extraDiags: List<DiagnosticStep>
    ): TokenValidation {
        diagnostics.addAll(extraDiags)
        return TokenValidation(
            valid = false,
            error = "$error\n\nGitHub response: ${parseGitHubError(body)}",
            tokenType = tokenType,
            diagnostics = diagnostics
        )
    }

    private fun logSafe(msg: String) {
        Log.d(TAG, msg)
    }

    private fun logRequest(method: String, endpoint: String) {
        Log.d(TAG, "→ $method $endpoint")
    }

    private fun logResponse(method: String, endpoint: String, code: Int, body: String, headers: Map<String, String>) {
        Log.d(TAG, "← $method $endpoint → $code")
        Log.d(TAG, "  X-OAuth-Scopes: ${headers["x-oauth-scopes"] ?: "(not present)"}")
        Log.d(TAG, "  X-Accepted-OAuth-Scopes: ${headers["x-accepted-oauth-scopes"] ?: "(not present)"}")
        Log.d(TAG, "  Body (${body.length} chars): ${body.take(300)}")
    }

    override suspend fun ensureRepository(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val validation = validateToken()
            if (!validation.valid) {
                return@withContext Result.failure(Exception(validation.error ?: "Invalid GitHub token"))
            }
            owner = validation.username ?: owner

            logRequest("GET", "/repos/$owner/$repo")
            val (repoCode, repoBody, repoHeaders) = githubGetWithHeaders("$apiBase/repos/$owner/$repo")
            logResponse("GET", "/repos/$owner/$repo", repoCode, repoBody, repoHeaders)
            if (repoCode == 200) {
                ensureWorkflowFile()
                return@withContext Result.success(Unit)
            }

            val endpoint = "POST /user/repos"
            logRequest("POST", endpoint)
            val (forkCode, forkBody, forkHeaders) = githubPostWithHeaders(
                "$apiBase/user/repos",
                """{"name":"$repo","auto_init":true,"private":true,"description":"IdeaForge Cloud Builder"}"""
            )
            logResponse("POST", endpoint, forkCode, forkBody, forkHeaders)

            if (forkCode in 200..299) {
                delay(2000)
                ensureWorkflowFile()
                return@withContext Result.success(Unit)
            }

            val apiError = parseGitHubError(forkBody)
            val (diagnosisMsg, _) = diagnoseRepoCreationFailure(forkCode, forkBody, apiError, owner)
            logSafe("Repo creation failed: $diagnosisMsg")
            return@withContext Result.failure(Exception(diagnosisMsg))
        } catch (e: Exception) {
            logSafe("ensureRepository exception: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun ensureWorkflowFile() {
        val workflowContent = getWorkflowYaml()
        val path = ".github/workflows/build.yml"
        val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/contents/$path")

        if (code == 200) {
            val sha = json.parseToJsonElement(body).jsonObject["sha"]?.jsonPrimitive?.content ?: return
            val encoded = java.util.Base64.getEncoder().encodeToString(workflowContent.toByteArray())
            githubPut(
                "$apiBase/repos/$owner/$repo/contents/$path",
                """{"message":"Update build workflow","content":"$encoded","sha":"$sha","branch":"main"}"""
            )
        } else if (code == 404) {
            val encoded = java.util.Base64.getEncoder().encodeToString(workflowContent.toByteArray())
            githubPut(
                "$apiBase/repos/$owner/$repo/contents/$path",
                """{"message":"Add build workflow","content":"$encoded","branch":"main"}"""
            )
        }
    }

    override suspend fun pushFiles(projectDir: String, projectName: String, branch: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val repoInfo = getRepoInfo()
                val repoFullName = "$owner/$repo"
                logSafe("=== Pushing files to $repoFullName ===")
                logSafe("  Branch: $branch")
                logSafe("  Project name: $projectName")
                logSafe("  Local directory: $projectDir")
                logSafe("  Repository default branch: ${repoInfo.first}")

                createBranchIfAbsent(branch)
                logSafe("Branch '$branch' created/confirmed")

                val dir = File(projectDir)
                val files = dir.walkTopDown().filter { it.isFile }.toList()
                logSafe("Pushing ${files.size} project files to '$branch'...")

                var fileCount = 0
                for (file in files) {
                    val relPath = file.relativeTo(dir).path
                    val content = java.util.Base64.getEncoder().encodeToString(file.readBytes())
                    val apiPath = "projects/$projectName/$relPath"
                    val contentSizeKb = file.length() / 1024

                    val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/contents/$apiPath?ref=$branch")
                    val putCode: Int
                    if (code == 200) {
                        val sha = json.parseToJsonElement(body).jsonObject["sha"]?.jsonPrimitive?.content ?: continue
                        val (pc, _) = githubPut(
                            "$apiBase/repos/$owner/$repo/contents/$apiPath",
                            """{"message":"Update $relPath","content":"$content","sha":"$sha","branch":"$branch"}"""
                        )
                        putCode = pc
                    } else {
                        val (pc, _) = githubPut(
                            "$apiBase/repos/$owner/$repo/contents/$apiPath",
                            """{"message":"Add $relPath","content":"$content","branch":"$branch"}"""
                        )
                        putCode = pc
                    }
                    fileCount++
                    if (fileCount <= 3 || fileCount % 10 == 0) {
                        logSafe("  Pushed [$fileCount/$files.size] $relPath ($contentSizeKb KB, HTTP $putCode)")
                    }
                }
                logSafe("Pushed $fileCount project files to '$branch'")

                val workflowPath = ".github/workflows/build.yml"
                val workflowContent = getWorkflowYaml()
                val encoded = java.util.Base64.getEncoder().encodeToString(workflowContent.toByteArray())
                logSafe("Pushing workflow file to '$branch'...")
                val (wfCode, wfBody, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/contents/$workflowPath?ref=$branch")
                val wfPutCode: Int
                if (wfCode == 200) {
                    val sha = json.parseToJsonElement(wfBody).jsonObject["sha"]?.jsonPrimitive?.content
                    if (sha != null) {
                        val (pc, _) = githubPut(
                            "$apiBase/repos/$owner/$repo/contents/$workflowPath",
                            """{"message":"Update build workflow","content":"$encoded","sha":"$sha","branch":"$branch"}"""
                        )
                        wfPutCode = pc
                    } else {
                        wfPutCode = -1
                    }
                } else {
                    val (pc, _) = githubPut(
                        "$apiBase/repos/$owner/$repo/contents/$workflowPath",
                        """{"message":"Add build workflow","content":"$encoded","branch":"$branch"}"""
                    )
                    wfPutCode = pc
                }
                logSafe("  Workflow file push: HTTP $wfPutCode, size=${workflowContent.length} bytes")

                logSafe("=== Push complete for $repoFullName/$branch ===")
                Result.success(branch)
            } catch (e: Exception) {
                logSafe("pushFiles exception: ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun pushFix(
        projectDir: String,
        projectName: String,
        branch: String,
        changedFiles: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for ((relPath, newContent) in changedFiles) {
                val apiPath = "projects/$projectName/$relPath"
                val encoded = java.util.Base64.getEncoder().encodeToString(newContent.toByteArray())

                val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/contents/$apiPath?ref=$branch")
                if (code == 200) {
                    val sha = json.parseToJsonElement(body).jsonObject["sha"]?.jsonPrimitive?.content ?: continue
                    githubPut(
                        "$apiBase/repos/$owner/$repo/contents/$apiPath",
                        """{"message":"Fix $relPath","content":"$encoded","sha":"$sha","branch":"$branch"}"""
                    )
                } else {
                    githubPut(
                        "$apiBase/repos/$owner/$repo/contents/$apiPath",
                        """{"message":"Add fix: $relPath","content":"$encoded","branch":"$branch"}"""
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startBuild(branch: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val repoInfo = getRepoInfo()
                val repoFullName = "$owner/$repo"
                val defaultBranch = repoInfo.first
                val repoId = repoInfo.second

                logSafe("=== Starting build dispatch ===")
                logSafe("  Repository: $repoFullName (id=$repoId)")
                logSafe("  Default branch: $defaultBranch")
                logSafe("  Target branch: $branch")
                logSafe("  Workflow: .github/workflows/build.yml")
                logSafe("  Dispatch ref: $branch")

                logSafe("Verifying branch '$branch' exists...")
                val branchVerified = verifyBranchExists(branch, maxRetries = 5, initialDelayMs = 3000)
                if (!branchVerified) {
                    return@withContext Result.failure(Exception(
                        "Branch '$branch' not found after push.\n\n" +
                        "Repository: $repoFullName\n" +
                        "GitHub may be slow to index the new branch.\n\n" +
                        "Fix: Wait 30 seconds and try again. If the issue persists, check that your token has 'repo' scope."
                    ))
                }
                logSafe("Branch '$branch' verified")

                logSafe("Verifying workflow file on branch '$branch'...")
                val workflowVerified = verifyWorkflowOnBranch(branch, maxRetries = 5, initialDelayMs = 2000)
                if (!workflowVerified) {
                    logSafe("WARNING: Workflow file not found on branch '$branch', attempting dispatch anyway")
                } else {
                    logSafe("Workflow file confirmed on branch '$branch'")
                }

                logSafe("Dispatching workflow 'build.yml' on branch '$branch'...")
                val body = """{"ref":"$branch"}"""
                logRequest("POST", "/repos/$repoFullName/actions/workflows/build.yml/dispatches")
                val (code, respBody, _) = githubPostWithHeaders("$apiBase/repos/$repoFullName/actions/workflows/build.yml/dispatches", body)
                logResponse("POST", "workflow dispatch", code, respBody, emptyMap())

                if (code !in 200..299) {
                    val apiError = parseGitHubError(respBody)
                    val msg = when (code) {
                        401 -> "GitHub token is invalid.\n\nAPI response: $apiError"
                        403 -> "Actions permission denied.\n\nAPI response: $apiError\n\nYour token needs the 'workflow' scope."
                        404 -> "Workflow 'build.yml' not found on branch '$branch'.\n\nAPI response: $apiError\n\nRepository: $repoFullName\nWorkflow: .github/workflows/build.yml\n\nThe workflow file was pushed but GitHub cannot find it. This can happen if:\n  - The push was not fully indexed yet\n  - The workflow YAML has syntax errors\n  - GitHub Actions is not enabled for this repository"
                        422 -> "Workflow 'build.yml' not found or invalid on branch '$branch'.\n\nAPI response: $apiError\n\nRepository: $repoFullName"
                        429 -> "GitHub rate limit exceeded.\n\nAPI response: $apiError"
                        else -> "Failed to trigger build (HTTP $code).\n\nAPI response: $apiError\n\nRepository: $repoFullName\nBranch: $branch"
                    }
                    return@withContext Result.failure(Exception(msg))
                }
                logSafe("Workflow dispatch accepted (HTTP $code, ref=$branch)")
                logSafe("Waiting 10s for GitHub to register the run...")
                delay(10000)

                logSafe("Polling for workflow run...")
                val pollStartMs = System.currentTimeMillis()
                val pollTimeoutMs = 90_000L
                val pollDelays = listOf(2000L, 3000L, 5000L, 8000L, 12000L, 18000L, 25000L, 35000L)
                var pollAttempt = 0

                for (delayMs in pollDelays) {
                    pollAttempt++
                    val elapsed = System.currentTimeMillis() - pollStartMs
                    logSafe("Poll attempt $pollAttempt (${elapsed}ms elapsed)...")

                    val (runCode, runBody, _) = githubGetWithHeaders("$apiBase/repos/$repoFullName/actions/runs?branch=$branch&per_page=10")
                    if (runCode == 200) {
                        val runs = json.parseToJsonElement(runBody).jsonObject["workflow_runs"]?.jsonArray
                        logSafe("  API returned ${runs?.size ?: 0} runs for branch '$branch'")

                        if (runs != null) {
                            for (run in runs) {
                                val r = run.jsonObject
                                val rId = r["id"]?.jsonPrimitive?.content ?: "?"
                                val rName = r["name"]?.jsonPrimitive?.content ?: "?"
                                val rEvent = r["event"]?.jsonPrimitive?.content ?: "?"
                                val rBranch = r["head_branch"]?.jsonPrimitive?.content ?: "?"
                                val rStatus = r["status"]?.jsonPrimitive?.content ?: "?"
                                val rCreated = r["created_at"]?.jsonPrimitive?.content?.take(19) ?: "?"
                                logSafe("  Run: id=$rId, name=$rName, event=$rEvent, branch=$rBranch, status=$rStatus, created=$rCreated")
                            }

                            val match = runs.firstOrNull {
                                val hb = it.jsonObject["head_branch"]?.jsonPrimitive?.content ?: ""
                                hb == branch
                            }
                            if (match != null) {
                                val runId = match.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                                val runName = match.jsonObject["name"]?.jsonPrimitive?.content ?: "?"
                                val runEvent = match.jsonObject["event"]?.jsonPrimitive?.content ?: "?"
                                val runStatus = match.jsonObject["status"]?.jsonPrimitive?.content ?: "?"
                                logSafe("Build run found: id=$runId, name=$runName, event=$runEvent, branch=$branch, status=$runStatus")
                                return@withContext Result.success(runId)
                            }
                        }
                    } else {
                        logSafe("  Failed to query runs: HTTP $runCode, body=${runBody.take(200)}")
                    }

                    if (elapsed + delayMs > pollTimeoutMs) {
                        logSafe("Approaching poll timeout (${elapsed}ms), checking one more time...")
                    }
                    delay(delayMs)
                }

                val totalPolled = System.currentTimeMillis() - pollStartMs
                logSafe("Build run not found after ${totalPolled}ms of polling")

                logSafe("=== Diagnostic: fetching latest 10 runs across all branches ===")
                val (diagCode, diagBody, _) = githubGetWithHeaders("$apiBase/repos/$repoFullName/actions/runs?per_page=10")
                if (diagCode == 200) {
                    val allRuns = json.parseToJsonElement(diagBody).jsonObject["workflow_runs"]?.jsonArray
                    logSafe("Latest runs across all branches (${allRuns?.size ?: 0} total):")
                    if (allRuns != null) {
                        for (run in allRuns) {
                            val r = run.jsonObject
                            val rId = r["id"]?.jsonPrimitive?.content ?: "?"
                            val rName = r["name"]?.jsonPrimitive?.content ?: "?"
                            val rEvent = r["event"]?.jsonPrimitive?.content ?: "?"
                            val rBranch = r["head_branch"]?.jsonPrimitive?.content ?: "?"
                            val rStatus = r["status"]?.jsonPrimitive?.content ?: "?"
                            val rCreated = r["created_at"]?.jsonPrimitive?.content?.take(19) ?: "?"
                            logSafe("  Run: id=$rId, name=$rName, event=$rEvent, branch=$rBranch, status=$rStatus, created=$rCreated")
                        }
                    }
                } else {
                    logSafe("Diagnostic query failed: HTTP $diagCode")
                }

                val (actionsCode, actionsBody, _) = githubGetWithHeaders("$apiBase/repos/$repoFullName/actions/permissions")
                logSafe("Actions permissions: HTTP $actionsCode, body=${actionsBody.take(300)}")

                Result.failure(Exception(
                    "Build run not found after triggering workflow dispatch.\n\n" +
                    "Repository: $repoFullName\n" +
                    "Branch: $branch\n" +
                    "Polled for: ${totalPolled / 1000}s\n\n" +
                    "Possible causes:\n" +
                    "  - GitHub Actions may not be enabled for this repository\n" +
                    "  - The workflow file may have YAML syntax errors\n" +
                    "  - The 'on: workflow_dispatch:' trigger may be missing or misconfigured in .github/workflows/build.yml\n" +
                    "  - GitHub may still be indexing the branch\n\n" +
                    "Fix:\n" +
                    "  1. Go to github.com/$repoFullName/actions — check if any runs appear manually\n" +
                    "  2. Verify .github/workflows/build.yml has 'on: workflow_dispatch:' correctly\n" +
                    "  3. Check Settings → Actions → General → 'Allow all actions'\n" +
                    "  4. Re-run diagnostics in IdeaForge Settings"
                ))
            } catch (e: Exception) {
                logSafe("startBuild exception: ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun getBuildStatus(buildId: String): Result<CloudBuildStatus> =
        withContext(Dispatchers.IO) {
            try {
                val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/actions/runs/$buildId")
                if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))

                val run = json.parseToJsonElement(body).jsonObject
                val status = run["status"]?.jsonPrimitive?.content ?: "unknown"
                val conclusion = run["conclusion"]?.jsonPrimitive?.content

                val phase = when {
                    status == "completed" && conclusion == "success" -> BuildPhase.COMPLETED
                    status == "completed" -> BuildPhase.FAILED
                    status == "in_progress" -> BuildPhase.BUILDING
                    status == "queued" || status == "waiting" || status == "pending" -> BuildPhase.QUEUED
                    else -> BuildPhase.QUEUED
                }

                val message = when (phase) {
                    BuildPhase.COMPLETED -> "Build succeeded"
                    BuildPhase.FAILED -> "Build failed: ${conclusion ?: "unknown error"}"
                    BuildPhase.BUILDING -> "Compiling your app..."
                    BuildPhase.QUEUED -> "Waiting in build queue..."
                    else -> phase.displayName
                }

                Result.success(CloudBuildStatus(
                    status = phase,
                    progress = phase.progress,
                    message = message,
                    buildId = buildId
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getBuildLogs(buildId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/actions/runs/$buildId/logs")
                if (code == 200) return@withContext Result.success(body)

                val (artCode, artBody, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/actions/runs/$buildId/jobs")
                if (artCode == 200) {
                    val jobs = json.parseToJsonElement(artBody).jsonObject["jobs"]?.jsonArray
                    val logBuilder = StringBuilder()
                    jobs?.forEach { job ->
                        val jobObj = job.jsonObject
                        val jobName = jobObj["name"]?.jsonPrimitive?.content ?: "unknown"
                        val jobConclusion = jobObj["conclusion"]?.jsonPrimitive?.content ?: "pending"
                        logBuilder.appendLine("=== Job: $jobName ($jobConclusion) ===")
                        jobObj["steps"]?.jsonArray?.forEach { step ->
                            val stepObj = step.jsonObject
                            logBuilder.appendLine("  Step: ${stepObj["name"]?.jsonPrimitive?.content ?: ""} (${stepObj["conclusion"]?.jsonPrimitive?.content ?: "pending"})")
                        }
                    }
                    return@withContext Result.success(logBuilder.toString())
                }
                Result.success("Build logs unavailable")
            } catch (e: Exception) {
                Result.success("Build logs unavailable")
            }
        }

    override suspend fun downloadApk(buildId: String, destPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/actions/runs/$buildId/artifacts")
                if (code != 200) return@withContext Result.failure(Exception("Failed to list artifacts: HTTP $code"))

                val artifacts = json.parseToJsonElement(body).jsonObject["artifacts"]?.jsonArray
                if (artifacts.isNullOrEmpty()) return@withContext Result.failure(Exception("No artifacts found"))

                val apkArtifact = artifacts.firstOrNull {
                    val name = it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                    name.contains("apk", ignoreCase = true) || name.contains("app", ignoreCase = true)
                } ?: artifacts.first()

                val artifactId = apkArtifact.jsonObject["id"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("No artifact ID"))

                val zipUrl = "$apiBase/repos/$owner/$repo/actions/artifacts/$artifactId/zip"
                val tempDir = File(destPath)
                tempDir.mkdirs()
                val zipFile = File(tempDir, "temp_artifact.zip")

                downloadFile(zipUrl, zipFile)

                if (zipFile.length() < 1000) {
                    zipFile.delete()
                    return@withContext Result.failure(Exception("Downloaded artifact is too small"))
                }

                val apkPath = extractApkFromZip(zipFile.absolutePath, destPath)
                zipFile.delete()

                val apkFile = File(apkPath)
                if (!apkFile.exists() || apkFile.length() < 10000) {
                    return@withContext Result.failure(Exception("APK file is invalid or too small"))
                }

                val magic = ByteArray(4)
                apkFile.inputStream().use { it.read(magic) }
                if (magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
                    return@withContext Result.failure(Exception("Downloaded file is not a valid APK (bad magic bytes)"))
                }

                Result.success(apkPath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun cancelBuild(buildId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                githubPost("$apiBase/repos/$owner/$repo/actions/runs/$buildId/cancel", "{}")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.success(Unit)
            }
        }

    private suspend fun getRepoInfo(): Pair<String, String> {
        return try {
            val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo")
            if (code == 200) {
                val obj = json.parseToJsonElement(body).jsonObject
                val defaultBranch = obj["default_branch"]?.jsonPrimitive?.content ?: "main"
                val repoId = obj["id"]?.jsonPrimitive?.content?.take(8) ?: "?"
                defaultBranch to repoId
            } else {
                "main" to "?"
            }
        } catch (_: Exception) {
            "main" to "?"
        }
    }

    private suspend fun verifyBranchExists(branch: String, maxRetries: Int = 5, initialDelayMs: Long = 3000): Boolean {
        for (attempt in 1..maxRetries) {
            try {
                val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/branches/$branch")
                if (code == 200) {
                    logSafe("Branch '$branch' exists (confirmed via GET /branches)")
                    return true
                }
                if (code == 404) {
                    logSafe("Branch '$branch' not yet visible (attempt $attempt/$maxRetries, HTTP $code)")
                    if (attempt < maxRetries) {
                        val delayMs = initialDelayMs * (1L shl (attempt - 1))
                        logSafe("  Retrying in ${delayMs}ms...")
                        delay(delayMs)
                    }
                } else {
                    logSafe("Branch check returned HTTP $code (attempt $attempt/$maxRetries): ${body.take(200)}")
                    if (attempt < maxRetries) delay(initialDelayMs)
                }
            } catch (e: Exception) {
                logSafe("Branch check exception (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt < maxRetries) delay(initialDelayMs)
            }
        }
        return false
    }

    private suspend fun verifyWorkflowOnBranch(branch: String, maxRetries: Int = 5, initialDelayMs: Long = 2000): Boolean {
        val workflowPath = ".github/workflows/build.yml"
        for (attempt in 1..maxRetries) {
            try {
                val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/contents/$workflowPath?ref=$branch")
                if (code == 200) {
                    logSafe("Workflow '$workflowPath' confirmed on branch '$branch'")
                    val sha = json.parseToJsonElement(body).jsonObject["sha"]?.jsonPrimitive?.content ?: "?"
                    val size = json.parseToJsonElement(body).jsonObject["size"]?.jsonPrimitive?.content ?: "?"
                    logSafe("  SHA: $sha, Size: $size bytes")
                    return true
                }
                if (code == 404) {
                    logSafe("Workflow '$workflowPath' not yet on branch '$branch' (attempt $attempt/$maxRetries, HTTP $code)")
                    if (attempt < maxRetries) {
                        val delayMs = initialDelayMs * (1L shl (attempt - 1))
                        logSafe("  Retrying in ${delayMs}ms...")
                        delay(delayMs)
                    }
                } else {
                    logSafe("Workflow check returned HTTP $code (attempt $attempt/$maxRetries): ${body.take(200)}")
                    if (attempt < maxRetries) delay(initialDelayMs)
                }
            } catch (e: Exception) {
                logSafe("Workflow check exception (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt < maxRetries) delay(initialDelayMs)
            }
        }
        return false
    }

    override suspend fun cleanup(branch: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (branch.startsWith("build-") || branch.startsWith("fix-")) {
                    githubDelete("$apiBase/repos/$owner/$repo/git/refs/heads/$branch")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.success(Unit)
            }
        }

    override fun isTransientError(error: Exception): Boolean {
        val msg = error.message?.lowercase() ?: return false
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") ||
            msg.contains("permission") || msg.contains("denied") || msg.contains("forbidden")) {
            return false
        }
        return msg.contains("timeout") || msg.contains("timed out") || msg.contains("connection") ||
                msg.contains("reset") || msg.contains("unavailable") || msg.contains("503") ||
                msg.contains("502") || msg.contains("429") || msg.contains("rate limit")
    }

    private fun parseGitHubError(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val message = obj["message"]?.jsonPrimitive?.content ?: ""
            val errors = obj["errors"]?.jsonArray
            val detail = errors?.joinToString("; ") { err ->
                err.jsonObject.entries.joinToString(", ") { "${it.key}: ${it.value.jsonPrimitive.content}" }
            } ?: ""
            val docUrl = obj["documentation_url"]?.jsonPrimitive?.content ?: ""
            buildString {
                if (message.isNotBlank()) append(message)
                if (detail.isNotBlank()) append(" — $detail")
                if (docUrl.isNotBlank()) append("\nSee: $docUrl")
            }.ifBlank { body.take(300) }
        } catch (_: Exception) {
            body.take(300).ifBlank { "Unknown error" }
        }
    }

    private suspend fun createBranchIfAbsent(branch: String) {
        val defaultBranch = getDefaultBranch()
        val (_, existingBranch, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/git/refs/heads/$branch")
        if (existingBranch.contains("\"ref\"")) return

        val (code, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo/git/refs/heads/$defaultBranch")
        if (code != 200) throw Exception("Failed to get default branch ref: HTTP $code")
        val obj = json.parseToJsonElement(body).jsonObject
        val sha = obj["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: throw Exception("No SHA found")

        val (postCode, _, _) = githubPostWithHeaders(
            "$apiBase/repos/$owner/$repo/git/refs",
            """{"ref":"refs/heads/$branch","sha":"$sha"}"""
        )
        if (postCode !in 200..299 && postCode != 422) {
            throw Exception("Failed to create branch: HTTP $postCode")
        }
    }

    private suspend fun getDefaultBranch(): String {
        val (_, body, _) = githubGetWithHeaders("$apiBase/repos/$owner/$repo")
        return json.parseToJsonElement(body).jsonObject["default_branch"]?.jsonPrimitive?.content ?: "main"
    }

    private fun downloadFile(url: String, destFile: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 30000
        conn.readTimeout = 300000
        conn.connect()
        conn.inputStream.copyTo(FileOutputStream(destFile), bufferSize = 8192)
        conn.disconnect()
    }

    private fun extractApkFromZip(zipPath: String, destDir: String): String {
        val destFile = File(destDir)
        destFile.mkdirs()
        ZipInputStream(FileInputStream(zipPath).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".apk") && !entry.isDirectory) {
                    val outPath = File(destFile, entry.name.substringAfterLast('/'))
                    FileOutputStream(outPath).use { output -> zis.copyTo(output, bufferSize = 8192) }
                    return outPath.absolutePath
                }
                entry = zis.nextEntry
            }
        }
        throw Exception("No APK found in artifact")
    }

    private fun readHeaders(conn: HttpURLConnection): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        conn.headerFields.forEach { (key, values) ->
            if (key != null) headers[key.lowercase()] = values.joinToString(", ")
        }
        return headers
    }

    private suspend fun warmUpConnection(url: String) {
        repeat(3) { attempt ->
            try {
                val conn = URL(url).openConnection() as java.net.HttpURLConnection
                try {
                    conn.connectTimeout = 30000
                    conn.readTimeout = 10000
                    conn.requestMethod = "HEAD"
                    conn.connect()
                    conn.disconnect()
                    logSafe("Connection warmup succeeded on attempt ${attempt + 1}")
                    return
                } catch (e: Exception) {
                    logSafe("Connection warmup attempt ${attempt + 1} failed: ${e.message}")
                    if (attempt < 2) delay(2000L * (attempt + 1))
                }
            } catch (_: Exception) {}
        }
        logSafe("Connection warmup failed after 3 attempts — proceeding anyway")
    }

    private fun httpGetWithHeaders(url: String): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.requestMethod = "GET"
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            return HttpResponse(code, body, readHeaders(conn))
        } finally { conn.disconnect() }
    }

    private fun httpPostWithHeaders(url: String, jsonBody: String): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.outputStream.write(jsonBody.toByteArray())
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            return HttpResponse(code, body, readHeaders(conn))
        } finally { conn.disconnect() }
    }

    private suspend fun httpGet(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val r = httpGetWithHeaders(url); Pair(r.code, r.body)
    }

    private suspend fun httpPost(url: String, jsonBody: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val r = httpPostWithHeaders(url, jsonBody); Pair(r.code, r.body)
    }

    private suspend fun httpPut(url: String, jsonBody: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.requestMethod = "PUT"; conn.doOutput = true
            conn.connectTimeout = 30000; conn.readTimeout = 60000
            conn.outputStream.write(jsonBody.toByteArray())
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            Pair(code, body)
        } finally { conn.disconnect() }
    }

    private suspend fun httpDelete(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 30000; conn.readTimeout = 60000
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { "" }
            Pair(code, body)
        } finally { conn.disconnect() }
    }

    private suspend fun githubGetWithHeaders(url: String): HttpResponse {
        var lastEx: Exception? = null
        repeat(3) { attempt ->
            try {
                val r = httpGetWithHeaders(url)
                if (r.code == 429) { delay(30_000L); return@repeat }
                return r
            } catch (e: Exception) { lastEx = e; if (attempt < 2) delay(1000L * (attempt + 1)) }
        }
        throw lastEx ?: Exception("Request failed after 3 retries")
    }

    private suspend fun githubGet(url: String): Pair<Int, String> {
        val r = githubGetWithHeaders(url); return Pair(r.code, r.body)
    }

    private suspend fun githubPostWithHeaders(url: String, jsonBody: String): HttpResponse {
        var lastEx: Exception? = null
        repeat(3) { attempt ->
            try {
                val r = httpPostWithHeaders(url, jsonBody)
                if (r.code == 429) { delay(30_000L); return@repeat }
                return r
            } catch (e: Exception) { lastEx = e; if (attempt < 2) delay(1000L * (attempt + 1)) }
        }
        throw lastEx ?: Exception("Request failed after 3 retries")
    }

    private suspend fun githubPost(url: String, jsonBody: String): Pair<Int, String> {
        val r = githubPostWithHeaders(url, jsonBody); return Pair(r.code, r.body)
    }

    private suspend fun githubPut(url: String, jsonBody: String): Pair<Int, String> {
        var lastEx: Exception? = null
        repeat(3) { attempt ->
            try {
                val (code, body) = httpPut(url, jsonBody)
                if (code == 429) { delay(30_000L); return@repeat }
                return Pair(code, body)
            } catch (e: Exception) { lastEx = e; if (attempt < 2) delay(1000L * (attempt + 1)) }
        }
        throw lastEx ?: Exception("Request failed after 3 retries")
    }

    private suspend fun githubDelete(url: String): Pair<Int, String> {
        var lastEx: Exception? = null
        repeat(2) { attempt ->
            try { return httpDelete(url) } catch (e: Exception) { lastEx = e; if (attempt < 1) delay(1000L) }
        }
        throw lastEx ?: Exception("Delete failed after 2 retries")
    }

    private fun getWorkflowYaml(): String = buildString {
        appendLine("name: IdeaForge Build")
        appendLine()
        appendLine("on:")
        appendLine("  workflow_dispatch:")
        appendLine("    inputs:")
        appendLine("      project_name:")
        appendLine("        description: 'Project name'")
        appendLine("        required: false")
        appendLine("        default: 'app'")
        appendLine()
        appendLine("jobs:")
        appendLine("  build:")
        appendLine("    runs-on: ubuntu-latest")
        appendLine("    steps:")
        appendLine("      - name: Checkout repository")
        appendLine("        uses: actions/checkout@v4")
        appendLine()
        appendLine("      - name: Set up JDK 17")
        appendLine("        uses: actions/setup-java@v4")
        appendLine("        with:")
        appendLine("          java-version: '17'")
        appendLine("          distribution: 'temurin'")
        appendLine("          cache: gradle")
        appendLine()
        appendLine("      - name: Grant execute permission for gradlew")
        appendLine("        run: chmod +x gradlew 2>/dev/null || true")
        appendLine()
        appendLine("      - name: Detect project structure")
        appendLine("        id: detect")
        appendLine("        run: |")
        appendLine("          if [ -f \"projects/*/build.gradle.kts\" ] || [ -f \"projects/*/settings.gradle.kts\" ]; then")
        appendLine("            echo \"PROJECT_DIR=\$(ls -d projects/*/ | head -1)\" >> \$GITHUB_OUTPUT")
        appendLine("            echo \"FOUND=true\" >> \$GITHUB_OUTPUT")
        appendLine("          else")
        appendLine("            echo \"FOUND=false\" >> \$GITHUB_OUTPUT")
        appendLine("          fi")
        appendLine()
        appendLine("      - name: Setup project if needed")
        appendLine("        if: steps.detect.outputs.FOUND == 'true'")
        appendLine("        run: |")
        appendLine("          PROJECT_DIR=\"\${{ steps.detect.outputs.PROJECT_DIR }}\"")
        appendLine("          cd \"\$PROJECT_DIR\"")
        appendLine("          if [ ! -f \"gradlew\" ]; then")
        appendLine("            gradle wrapper --gradle-version 8.9")
        appendLine("          fi")
        appendLine("          chmod +x gradlew")
        appendLine()
        appendLine("      - name: Build Debug APK")
        appendLine("        if: steps.detect.outputs.FOUND == 'true'")
        appendLine("        working-directory: \${{ steps.detect.outputs.PROJECT_DIR }}")
        appendLine("        run: ./gradlew assembleDebug --no-daemon --stacktrace || true")
        appendLine()
        appendLine("      - name: Build Release APK")
        appendLine("        if: steps.detect.outputs.FOUND == 'true'")
        appendLine("        working-directory: \${{ steps.detect.outputs.PROJECT_DIR }}")
        appendLine("        run: ./gradlew assembleRelease --no-daemon --stacktrace || true")
        appendLine()
        appendLine("      - name: Find APKs")
        appendLine("        if: steps.detect.outputs.FOUND == 'true'")
        appendLine("        id: find_apk")
        appendLine("        run: |")
        appendLine("          PROJECT_DIR=\"\${{ steps.detect.outputs.PROJECT_DIR }}\"")
        appendLine("          APK_DIR=\"\$PROJECT_DIR/app/build/outputs/apk\"")
        appendLine("          echo \"APK_DIR=\$APK_DIR\" >> \$GITHUB_OUTPUT")
        appendLine("          find \"\$APK_DIR\" -name \"*.apk\" -type f 2>/dev/null | head -5 || true")
        appendLine()
        appendLine("      - name: Upload APK artifact")
        appendLine("        if: steps.detect.outputs.FOUND == 'true'")
        appendLine("        uses: actions/upload-artifact@v4")
        appendLine("        with:")
        appendLine("          name: ideaforge-apk")
        appendLine("          path: \${{ steps.detect.outputs.PROJECT_DIR }}/app/build/outputs/apk/**/*.apk")
        appendLine("          if-no-files-found: warn")
        appendLine("          retention-days: 7")
        appendLine()
        appendLine("      - name: Upload build logs on failure")
        appendLine("        if: failure()")
        appendLine("        uses: actions/upload-artifact@v4")
        appendLine("        with:")
        appendLine("          name: build-logs")
        appendLine("          path: |")
        appendLine("            \${{ steps.detect.outputs.PROJECT_DIR }}/**/build/reports/")
        appendLine("            \${{ steps.detect.outputs.PROJECT_DIR }}/**/*.log")
        appendLine("          if-no-files-found: ignore")
        appendLine("          retention-days: 3")
    }
}
