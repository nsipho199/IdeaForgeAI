package com.ideaforge.ai.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class DiagnosticResult(
    val name: String,
    val passed: Boolean,
    val detail: String,
    val elapsedMs: Long = 0
)

data class BuildReadiness(
    val internet: CheckResult = CheckResult.PENDING,
    val dns: CheckResult = CheckResult.PENDING,
    val tls: CheckResult = CheckResult.PENDING,
    val githubReachable: CheckResult = CheckResult.PENDING,
    val geminiReachable: CheckResult = CheckResult.PENDING,
    val githubAuth: CheckResult = CheckResult.PENDING,
    val geminiAuth: CheckResult = CheckResult.PENDING,
    val repoCreation: CheckResult = CheckResult.PENDING,
    val workflowPermission: CheckResult = CheckResult.PENDING
) {
    enum class CheckResult { PENDING, PASS, FAIL, SKIP }

    val isReady: Boolean get() = internet == CheckResult.PASS && dns == CheckResult.PASS &&
            tls == CheckResult.PASS && githubReachable == CheckResult.PASS &&
            geminiReachable == CheckResult.PASS && githubAuth == CheckResult.PASS &&
            geminiAuth == CheckResult.PASS

    fun toChecklist(): List<Pair<String, CheckResult>> = listOf(
        "Internet" to internet,
        "DNS" to dns,
        "TLS" to tls,
        "GitHub reachable" to githubReachable,
        "Gemini reachable" to geminiReachable,
        "GitHub authentication" to githubAuth,
        "Gemini authentication" to geminiAuth,
        "Repository creation" to repoCreation,
        "Workflow permission" to workflowPermission
    )

    fun firstFailure(): String? {
        val labels = listOf(
            "Internet" to internet, "DNS" to dns, "TLS" to tls,
            "GitHub reachable" to githubReachable, "Gemini reachable" to geminiReachable,
            "GitHub authentication" to githubAuth, "Gemini authentication" to geminiAuth,
            "Repository creation" to repoCreation, "Workflow permission" to workflowPermission
        )
        return labels.firstOrNull { it.second == CheckResult.FAIL }?.first
    }
}

object NetworkDiagnostics {

    private const val TAG = "NetDiagnostics"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val WARMUP_ATTEMPTS = 3

    data class FullReport(
        val networkResults: List<DiagnosticResult>,
        val authResults: List<DiagnosticResult>,
        val readiness: BuildReadiness,
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    ) {
        val allPassed: Boolean get() = readiness.isReady
        val exportText: String get() = buildString {
            appendLine("=== IdeaForge AI Diiagnostics Report ===")
            appendLine("Timestamp: $timestamp")
            appendLine()

            appendLine("--- Network Tests ---")
            networkResults.forEach { r ->
                val icon = if (r.passed) "PASS" else "FAIL"
                appendLine("[$icon] ${r.name}: ${r.detail}")
            }
            appendLine()

            appendLine("--- API Authentication Tests ---")
            authResults.forEach { r ->
                val icon = if (r.passed) "PASS" else "FAIL"
                appendLine("[$icon] ${r.name}: ${r.detail}")
            }
            appendLine()

            appendLine("--- Build Readiness ---")
            readiness.toChecklist().forEach { (label, result) ->
                val icon = when (result) {
                    BuildReadiness.CheckResult.PASS -> "PASS"
                    BuildReadiness.CheckResult.FAIL -> "FAIL"
                    BuildReadiness.CheckResult.SKIP -> "SKIP"
                    BuildReadiness.CheckResult.PENDING -> "PENDING"
                }
                appendLine("[$icon] $label")
            }
            appendLine()

            if (readiness.isReady) {
                appendLine("STATUS: READY TO BUILD")
            } else {
                appendLine("STATUS: NOT READY — first failure: ${readiness.firstFailure() ?: "unknown"}")
            }
        }
    }

    suspend fun runFullDiagnostics(
        context: Context,
        githubToken: String,
        geminiApiKey: String
    ): FullReport = withContext(Dispatchers.IO) {
        val networkResults = mutableListOf<DiagnosticResult>()
        val authResults = mutableListOf<DiagnosticResult>()
        var readiness = BuildReadiness()

        // Phase 0: Warmup — prevents first-connection timeout on cellular
        warmUpConnections()

        // Phase 1: Network connectivity
        val netCheck = checkNetworkAvailable(context)
        networkResults.add(netCheck)
        readiness = readiness.copy(internet = if (netCheck.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL)

        val inetCheck = checkInternetConnected(context)
        networkResults.add(inetCheck)
        if (!inetCheck.passed && readiness.internet == BuildReadiness.CheckResult.FAIL) {
            readiness = readiness.copy(dns = BuildReadiness.CheckResult.FAIL, tls = BuildReadiness.CheckResult.FAIL,
                githubReachable = BuildReadiness.CheckResult.FAIL, geminiReachable = BuildReadiness.CheckResult.FAIL)
        } else {
            // DNS
            val dnsGithub = checkDnsResolution("api.github.com")
            val dnsGemini = checkDnsResolution("generativelanguage.googleapis.com")
            networkResults.add(dnsGithub)
            networkResults.add(dnsGemini)
            readiness = readiness.copy(dns = if (dnsGithub.passed && dnsGemini.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL)

            // Single combined test per endpoint — TLS handshake + HTTP response in one shot
            val githubTest = testEndpoint("https://api.github.com", "GitHub API")
            val geminiTest = testEndpoint("https://generativelanguage.googleapis.com/v1beta/", "Gemini API")
            networkResults.add(githubTest)
            networkResults.add(geminiTest)

            // Reachability: pass if we got ANY successful HTTP response (even 404/401 means reachable)
            readiness = readiness.copy(
                githubReachable = if (githubTest.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL,
                geminiReachable = if (geminiTest.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL,
                tls = if (githubTest.passed && geminiTest.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL
            )
        }

        // Phase 2: API Authentication
        if (readiness.githubReachable == BuildReadiness.CheckResult.PASS) {
            if (githubToken.isNotBlank()) {
                val ghAuth = testGitHubAuth(githubToken)
                authResults.add(ghAuth)
                val ghResult = if (ghAuth.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL
                readiness = readiness.copy(githubAuth = ghResult)

                val hasRepo = ghAuth.detail.contains("[repo]")
                val hasWorkflow = ghAuth.detail.contains("[workflow]")
                readiness = readiness.copy(
                    repoCreation = if (ghResult == BuildReadiness.CheckResult.PASS && hasRepo) BuildReadiness.CheckResult.PASS
                    else if (ghResult == BuildReadiness.CheckResult.FAIL) BuildReadiness.CheckResult.FAIL
                    else BuildReadiness.CheckResult.SKIP,
                    workflowPermission = if (ghResult == BuildReadiness.CheckResult.PASS && hasWorkflow) BuildReadiness.CheckResult.PASS
                    else if (ghResult == BuildReadiness.CheckResult.FAIL) BuildReadiness.CheckResult.FAIL
                    else BuildReadiness.CheckResult.SKIP
                )
            } else {
                authResults.add(DiagnosticResult("GitHub authentication", false, "No token set — add in Settings"))
                readiness = readiness.copy(
                    githubAuth = BuildReadiness.CheckResult.FAIL,
                    repoCreation = BuildReadiness.CheckResult.SKIP,
                    workflowPermission = BuildReadiness.CheckResult.SKIP
                )
            }
        } else {
            authResults.add(DiagnosticResult("GitHub authentication", false, "Skipped — GitHub unreachable"))
            readiness = readiness.copy(githubAuth = BuildReadiness.CheckResult.FAIL,
                repoCreation = BuildReadiness.CheckResult.SKIP, workflowPermission = BuildReadiness.CheckResult.SKIP)
        }

        if (readiness.geminiReachable == BuildReadiness.CheckResult.PASS) {
            if (geminiApiKey.isNotBlank()) {
                val gemAuth = testGeminiAuth(geminiApiKey)
                authResults.add(gemAuth)
                readiness = readiness.copy(geminiAuth = if (gemAuth.passed) BuildReadiness.CheckResult.PASS else BuildReadiness.CheckResult.FAIL)
            } else {
                authResults.add(DiagnosticResult("Gemini authentication", false, "No API key set — add in Settings"))
                readiness = readiness.copy(geminiAuth = BuildReadiness.CheckResult.FAIL)
            }
        } else {
            authResults.add(DiagnosticResult("Gemini authentication", false, "Skipped — Gemini unreachable"))
            readiness = readiness.copy(geminiAuth = BuildReadiness.CheckResult.FAIL)
        }

        Log.d(TAG, "Full diagnostics complete: ready=${readiness.isReady}")
        FullReport(networkResults, authResults, readiness)
    }

    private fun warmUpConnections() {
        val hosts = listOf("api.github.com" to 443, "generativelanguage.googleapis.com" to 443)
        for ((host, port) in hosts) {
            repeat(WARMUP_ATTEMPTS) { attempt ->
                try {
                    val start = System.currentTimeMillis()
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.close()
                    val elapsed = System.currentTimeMillis() - start
                    Log.d(TAG, "Warmup $host:$port OK (${elapsed}ms, attempt ${attempt + 1})")
                    return
                } catch (e: Exception) {
                    Log.d(TAG, "Warmup $host:$port failed (attempt ${attempt + 1}): ${e.message}")
                    if (attempt < WARMUP_ATTEMPTS - 1) {
                        try { Thread.sleep(2000L * (attempt + 1)) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun checkNetworkAvailable(context: Context): DiagnosticResult {
        val start = System.currentTimeMillis()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val elapsed = System.currentTimeMillis() - start

        return if (caps != null) {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Unknown"
            }
            DiagnosticResult("Network available", true, "Connected via $transport", elapsed)
        } else {
            DiagnosticResult("Network available", false, "No active network connection", elapsed)
        }
    }

    private fun checkInternetConnected(context: Context): DiagnosticResult {
        val start = System.currentTimeMillis()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return DiagnosticResult(
            "Internet connected", false, "No active network", System.currentTimeMillis() - start
        )
        val caps = cm.getNetworkCapabilities(network) ?: return DiagnosticResult(
            "Internet connected", false, "No network capabilities", System.currentTimeMillis() - start
        )
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val elapsed = System.currentTimeMillis() - start

        return DiagnosticResult(
            "Internet connected",
            hasInternet,
            if (hasInternet) "Internet access confirmed" else "Network present but no internet access (validation failed)",
            elapsed
        )
    }

    private fun checkDnsResolution(hostname: String): DiagnosticResult {
        val start = System.currentTimeMillis()
        return try {
            val addresses = InetAddress.getAllByName(hostname)
            val elapsed = System.currentTimeMillis() - start
            val ips = addresses.joinToString(", ") { it.hostAddress ?: "?" }
            Log.d(TAG, "DNS $hostname -> $ips (${elapsed}ms)")
            DiagnosticResult("DNS: $hostname", true, "Resolved to $ips (${elapsed}ms)", elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            Log.e(TAG, "DNS FAILED for $hostname: ${e.message}")
            DiagnosticResult("DNS: $hostname", false, "Cannot resolve $hostname: ${e.message}", elapsed)
        }
    }

    private fun testEndpoint(url: String, label: String): DiagnosticResult {
        val start = System.currentTimeMillis()
        var conn: HttpsURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connect()

            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            val cipher = conn.cipherSuite ?: "unknown"
            val remoteAddr = conn.url.host ?: "?"

            Log.d(TAG, "testEndpoint $label: HTTP $code, cipher=$cipher, ${elapsed}ms, remote=$remoteAddr")
            Log.d(TAG, "  URL: $url")
            Log.d(TAG, "  Client: ${conn.javaClass.simpleName}")
            Log.d(TAG, "  ConnectTimeout: ${conn.connectTimeout}ms, ReadTimeout: ${conn.readTimeout}ms")

            DiagnosticResult(
                "HTTPS: $label",
                true,
                "HTTP $code | cipher=$cipher | ${elapsed}ms | client=${conn.javaClass.simpleName}",
                elapsed
            )
        } catch (e: java.net.SocketTimeoutException) {
            val elapsed = System.currentTimeMillis() - start
            Log.e(TAG, "testEndpoint $label TIMEOUT: ${e.message} (${elapsed}ms)")
            Log.e(TAG, "  URL: $url, client=${conn?.javaClass?.simpleName}, connectTimeout=${conn?.connectTimeout}ms, readTimeout=${conn?.readTimeout}ms")
            Log.e(TAG, "  Remote: ${conn?.url?.host}")
            DiagnosticResult("HTTPS: $label", false, "Timeout (${elapsed}ms): ${e.message}", elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            Log.e(TAG, "testEndpoint $label FAILED: ${e.javaClass.simpleName}: ${e.message} (${elapsed}ms)")
            DiagnosticResult("HTTPS: $label", false, "${e.javaClass.simpleName}: ${e.message}", elapsed)
        } finally {
            try { conn?.inputStream?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun testGitHubAuth(token: String): DiagnosticResult {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.github.com/user").openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connect()

            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            val body = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            val headers = mutableMapOf<String, String>()
            conn.headerFields.forEach { (key, values) ->
                if (key != null) headers[key.lowercase()] = values.joinToString(", ")
            }

            when (code) {
                200 -> {
                    val obj = org.json.JSONObject(body)
                    val username = obj.optString("login", "unknown") ?: "unknown"
                    val accountType = obj.optString("type", "User") ?: "User"
                    val rateLimit = headers["x-ratelimit-remaining"] ?: "?"
                    val rateLimitTotal = headers["x-ratelimit-limit"] ?: "?"
                    val scopes = headers["x-oauth-scopes"] ?: "(none)"
                    val hasRepo = scopes.split(",").map { it.trim() }.any { it == "repo" || it.startsWith("repo:") }
                    val hasWorkflow = scopes.split(",").map { it.trim() }.any { it == "workflow" || it.startsWith("workflow:") }

                    val detail = buildString {
                        append("@$username ($accountType)")
                        append(" | Rate limit: $rateLimit/$rateLimitTotal")
                        append(" | Scopes: $scopes")
                        if (hasRepo) append(" | [repo]")
                        if (hasWorkflow) append(" | [workflow]")
                    }
                    DiagnosticResult("GitHub authentication", true, detail, elapsed)
                }
                401 -> DiagnosticResult("GitHub authentication", false, "HTTP 401 — Token is invalid or expired", elapsed)
                403 -> {
                    val error = try { org.json.JSONObject(body).optString("message", "") ?: "" } catch (_: Exception) { "" }
                    when {
                        error.lowercase().contains("saml") || error.lowercase().contains("sso") ->
                            DiagnosticResult("GitHub authentication", false, "HTTP 403 — SAML/SSO authorization required: $error", elapsed)
                        error.lowercase().contains("rate limit") ->
                            DiagnosticResult("GitHub authentication", false, "HTTP 403 — Rate limited: $error", elapsed)
                        else ->
                            DiagnosticResult("GitHub authentication", false, "HTTP 403 — Access denied: $error", elapsed)
                    }
                }
                429 -> DiagnosticResult("GitHub authentication", false, "HTTP 429 — Rate limit exceeded", elapsed)
                else -> DiagnosticResult("GitHub authentication", false, "HTTP $code — ${body.take(200)}", elapsed)
            }
        } catch (e: java.net.UnknownHostException) {
            DiagnosticResult("GitHub authentication", false, "DNS failed: ${e.message}", System.currentTimeMillis() - start)
        } catch (e: java.net.SocketTimeoutException) {
            DiagnosticResult("GitHub authentication", false, "Timeout: ${e.message}", System.currentTimeMillis() - start)
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            DiagnosticResult("GitHub authentication", false, "SSL/TLS failed: ${e.message}", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            DiagnosticResult("GitHub authentication", false, "${e.javaClass.simpleName}: ${e.message}", System.currentTimeMillis() - start)
        } finally {
            try { conn?.inputStream?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun testGeminiAuth(apiKey: String): DiagnosticResult {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = 60_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = """{"model":"gemini-2.5-flash","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
            conn.outputStream.write(body.toByteArray())

            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            val responseBody = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }

            when {
                code in 200..299 -> {
                    try {
                        val obj = org.json.JSONObject(responseBody)
                        val model = obj.optString("model", "unknown") ?: "unknown"
                        DiagnosticResult("Gemini authentication", true, "API key valid | Model: $model | Response OK", elapsed)
                    } catch (_: Exception) {
                        DiagnosticResult("Gemini authentication", true, "API key valid (HTTP $code)", elapsed)
                    }
                }
                code == 400 -> {
                    val error = try { org.json.JSONObject(responseBody).optJSONObject("error")?.optString("message", "") ?: "" } catch (_: Exception) { "" }
                    when {
                        error.lowercase().contains("api key not valid") || error.lowercase().contains("invalid api key") ->
                            DiagnosticResult("Gemini authentication", false, "HTTP 400 — API key invalid: $error", elapsed)
                        error.lowercase().contains("model not found") || error.lowercase().contains("is not found") ->
                            DiagnosticResult("Gemini authentication", false, "HTTP 400 — Model not found: $error", elapsed)
                        else ->
                            DiagnosticResult("Gemini authentication", false, "HTTP 400 — Bad request: $error", elapsed)
                    }
                }
                code == 401 || code == 403 -> {
                    val error = try { org.json.JSONObject(responseBody).optJSONObject("error")?.optString("message", "") ?: "" } catch (_: Exception) { "" }
                    DiagnosticResult("Gemini authentication", false, "HTTP $code — ${if (error.isNotBlank()) error else "Access denied"}", elapsed)
                }
                code == 429 -> {
                    val error = try { org.json.JSONObject(responseBody).optJSONObject("error")?.optString("message", "") ?: "" } catch (_: Exception) { "" }
                    val retryAfter = conn.getHeaderField("Retry-After")
                    val retryMsg = if (retryAfter != null) {
                        try {
                            val seconds = retryAfter.toInt()
                            " Retry-After: ${seconds}s."
                        } catch (_: NumberFormatException) {
                            " Retry-After: $retryAfter."
                        }
                    } else ""
                    val detail = buildString {
                        append("API key is valid. Gemini quota/rate limit exceeded.")
                        if (error.isNotBlank()) append(" Gemini: $error.")
                        else append(" Free-tier limits have been reached.")
                        append(retryMsg)
                        append(" Please wait for the quota to reset or use another API key.")
                    }
                    DiagnosticResult("Gemini authentication", true, detail, elapsed)
                }
                code == 404 -> {
                    val error = try { org.json.JSONObject(responseBody).optJSONObject("error")?.optString("message", "") ?: "" } catch (_: Exception) { "" }
                    DiagnosticResult("Gemini authentication", false, "HTTP 404 — Endpoint not found: $error", elapsed)
                }
                else -> {
                    val error = try { org.json.JSONObject(responseBody).optJSONObject("error")?.optString("message", responseBody.take(200)) ?: responseBody.take(200) } catch (_: Exception) { responseBody.take(200) }
                    DiagnosticResult("Gemini authentication", false, "HTTP $code — $error", elapsed)
                }
            }
        } catch (e: java.net.UnknownHostException) {
            DiagnosticResult("Gemini authentication", false, "DNS failed: ${e.message}", System.currentTimeMillis() - start)
        } catch (e: java.net.SocketTimeoutException) {
            DiagnosticResult("Gemini authentication", false, "Timeout: ${e.message}", System.currentTimeMillis() - start)
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            DiagnosticResult("Gemini authentication", false, "SSL/TLS failed: ${e.message}", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            DiagnosticResult("Gemini authentication", false, "${e.javaClass.simpleName}: ${e.message}", System.currentTimeMillis() - start)
        } finally {
            try { conn?.inputStream?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
