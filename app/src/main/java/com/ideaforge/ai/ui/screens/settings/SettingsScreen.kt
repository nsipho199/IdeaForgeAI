package com.ideaforge.ai.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.ideaforge.ai.core.network.BuildReadiness

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val githubToken by viewModel.githubToken.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()
    val tokenValidation by viewModel.tokenValidation.collectAsState()
    val isValidating by viewModel.isValidating.collectAsState()
    val isRunningDiagnostics by viewModel.isRunningDiagnostics.collectAsState()
    val networkResults by viewModel.networkResults.collectAsState()
    val authResults by viewModel.authResults.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val exportText by viewModel.exportText.collectAsState()

    var showTokenInput by remember { mutableStateOf(false) }
    var tokenText by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    var showApiKeyInput by remember { mutableStateOf(false) }
    var apiKeyText by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    val savedApiKey = remember { mutableStateOf(viewModel.getOpenCodeApiKey()) }

    LaunchedEffect(githubToken) {
        if (githubToken.isNotBlank()) {
            viewModel.validateGithubToken()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            SettingsSection("Cloud Build") {
                ListItem(
                    headlineContent = { Text("GitHub Personal Access Token") },
                    supportingContent = {
                        when {
                            isValidating -> Text("Validating...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            tokenValidation?.valid == true -> Text(
                                "\u2713 Connected as: ${tokenValidation!!.username}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            githubToken.isNotBlank() && tokenValidation?.valid == false -> Text(
                                tokenValidation?.error?.take(80) ?: "Token invalid",
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Text(
                                "Required for cloud builds",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            if (githubToken.isNotBlank()) {
                                TextButton(onClick = { viewModel.validateGithubToken() }) {
                                    Text("Verify")
                                }
                            }
                            TextButton(onClick = { showTokenInput = !showTokenInput }) {
                                Text(if (githubToken.isNotBlank()) "Change" else "Add")
                            }
                        }
                    }
                )

                if (tokenValidation?.valid == true && tokenValidation!!.missingPermissions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Missing Token Permissions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            tokenValidation!!.missingPermissions.forEach { perm ->
                                Text(
                                    "\u2022 $perm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Fix: Use a classic token with 'repo' and 'workflow' scopes.\ngithub.com \u2192 Settings \u2192 Developer settings \u2192 Personal access tokens \u2192 Tokens (classic) \u2192 Generate new token \u2192 Check 'repo' and 'workflow' scopes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (showTokenInput) {
                    ListItem(
                        headlineContent = {
                            OutlinedTextField(
                                value = tokenText,
                                onValueChange = { tokenText = it },
                                label = { Text("GitHub Personal Access Token") },
                                placeholder = { Text("ghp_xxxxxxxxxxxx or github_pat_xxxx") },
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showToken = !showToken }) {
                                        Icon(
                                            if (showToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = {
                                    showTokenInput = false
                                    tokenText = ""
                                }) {
                                    Text("Cancel")
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.setGithubToken(tokenText.trim())
                                        showTokenInput = false
                                        tokenText = ""
                                    },
                                    enabled = tokenText.isNotBlank() && (tokenText.startsWith("ghp_") || tokenText.startsWith("github_pat_"))
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text("How to get a GitHub token:") },
                    supportingContent = {
                        Column {
                            Text("1. Go to github.com \u2192 Settings \u2192 Developer settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2. Click 'Personal access tokens' \u2192 'Tokens (classic)'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("3. Click 'Generate new token (classic)'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4. Give it a name (e.g., IdeaForge)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("5. Check the 'repo' and 'workflow' scopes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("6. Click 'Generate token' and paste it above", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("The 'repo' and 'workflow' scopes give all required permissions.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            SettingsSection("AI Model") {
                ListItem(
                    headlineContent = { Text("Google Gemini API Key") },
                    supportingContent = {
                        Text(
                            if (savedApiKey.value.isNotBlank()) "Key set (\u2022\u2022\u2022${savedApiKey.value.takeLast(4)})" else "Required for AI code generation (free)",
                            color = if (savedApiKey.value.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        TextButton(onClick = { showApiKeyInput = !showApiKeyInput }) {
                            Text(if (savedApiKey.value.isNotBlank()) "Change" else "Add")
                        }
                    }
                )

                if (showApiKeyInput) {
                    ListItem(
                        headlineContent = {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { apiKeyText = it },
                                label = { Text("Google Gemini API Key") },
                                placeholder = { Text("AIza...") },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { showApiKeyInput = false; apiKeyText = "" }) {
                                    Text("Cancel")
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.setOpenCodeApiKey(apiKeyText.trim())
                                        savedApiKey.value = apiKeyText.trim()
                                        showApiKeyInput = false
                                        apiKeyText = ""
                                    },
                                    enabled = apiKeyText.isNotBlank()
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text("How to get a free API key:") },
                    supportingContent = {
                        Column {
                            Text("1. Go to ai.google.dev", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2. Sign in with your Google account", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("3. Click 'Get API key' \u2192 'Create API key'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4. Copy the key and paste it above", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("FREE: 15 req/min, 1M tokens/day", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            SettingsSection("Appearance") {
                SettingsChipGroup(Icons.Default.Brightness6, "Theme", listOf("system" to "System", "light" to "Light", "dark" to "Dark"), themeMode) { viewModel.setThemeMode(it) }
            }

            SettingsSection("Language") {
                SettingsChipGroup(Icons.Default.Language, "Language", listOf("en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "pt" to "Portuguese", "ar" to "Arabic", "zh" to "Chinese", "hi" to "Hindi"), language) { viewModel.setLanguage(it) }
            }

            SettingsSection("Notifications") {
                ListItem(
                    headlineContent = { Text("Push Notifications") },
                    supportingContent = { Text("Receive build status notifications", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Switch(checked = notificationsEnabled, onCheckedChange = { viewModel.setNotificationsEnabled(it) }) }
                )
            }

            // === DIAGNOSTICS SECTION ===
            SettingsSection("Diagnostics") {
                ListItem(
                    headlineContent = { Text("Run Full Diagnostics") },
                    supportingContent = { Text("Test network, API authentication, and build readiness", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        TextButton(
                            onClick = { viewModel.runFullDiagnostics() },
                            enabled = !isRunningDiagnostics
                        ) {
                            if (isRunningDiagnostics) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Testing...")
                            } else {
                                Text("Run All")
                            }
                        }
                    }
                )

                // Build Readiness Summary
                readiness?.let { ready ->
                    BuildReadinessCard(ready)
                }

                // Network Test Results
                if (networkResults.isNotEmpty()) {
                    DiagnosticResultCard("Network Tests", networkResults)
                }

                // Auth Test Results
                if (authResults.isNotEmpty()) {
                    DiagnosticResultCard("API Authentication", authResults)
                }

                // Export button
                if (exportText != null) {
                    ListItem(
                        headlineContent = { Text("Export Diagnostics Report") },
                        supportingContent = { Text("Copy report to clipboard (no secrets included)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            TextButton(onClick = { viewModel.exportDiagnostics() }) {
                                Text("Copy")
                            }
                        }
                    )
                }
            }

            SettingsSection("About") {
                ListItem(
                    headlineContent = { Text("About IdeaForge AI") },
                    supportingContent = { Text("Version 1.0.0 \u2014 Gemini AI + Cloud Build", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
    }
}

@Composable
private fun BuildReadinessCard(readiness: BuildReadiness) {
    val isReady = readiness.isReady
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isReady) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isReady) "\uD83D\uDFE2 Ready to Build" else "\uD83D\uDD34 Not Ready",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            readiness.toChecklist().forEach { (label, result) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (result) {
                        BuildReadiness.CheckResult.PASS -> Icons.Default.CheckCircle
                        BuildReadiness.CheckResult.FAIL -> Icons.Default.Error
                        BuildReadiness.CheckResult.SKIP -> Icons.Default.SkipNext
                        BuildReadiness.CheckResult.PENDING -> Icons.Default.Schedule
                    }
                    val tint = when (result) {
                        BuildReadiness.CheckResult.PASS -> MaterialTheme.colorScheme.primary
                        BuildReadiness.CheckResult.FAIL -> MaterialTheme.colorScheme.error
                        BuildReadiness.CheckResult.SKIP -> MaterialTheme.colorScheme.onSurfaceVariant
                        BuildReadiness.CheckResult.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        when (result) {
                            BuildReadiness.CheckResult.PASS -> "\u2713"
                            BuildReadiness.CheckResult.FAIL -> "\u2717"
                            BuildReadiness.CheckResult.SKIP -> "skip"
                            BuildReadiness.CheckResult.PENDING -> "..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }

            if (!isReady) {
                val firstFail = readiness.firstFailure()
                if (firstFail != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "First failure: $firstFail — fix this step first",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticResultCard(title: String, results: List<com.ideaforge.ai.core.network.DiagnosticResult>) {
    val allPassed = results.all { it.passed }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPassed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            results.forEach { result ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (result.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(result.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(result.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.elapsedMs > 0) {
                        Text("${result.elapsedMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChipGroup(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, options: List<Pair<String, String>>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.padding(horizontal = 16.dp))
    FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> FilterChip(selected = selectedOption == value, onClick = { onOptionSelected(value) }, label = { Text(label) }) }
    }
}
