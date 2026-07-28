package com.ideaforge.ai.ui.screens.home

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.ui.components.BuildHistoryCard
import com.ideaforge.ai.ui.components.ProjectCard
import com.ideaforge.ai.ui.components.PromptQuickCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onBuildApp: (String) -> Unit,
    onNavigateToPromptLibrary: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val ideaText by viewModel.ideaText.collectAsState()
    val recentIdeas by viewModel.recentIdeas.collectAsState()
    val recentProjects by viewModel.recentProjects.collectAsState()
    val recentBuilds by viewModel.recentBuilds.collectAsState()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsState()
    val hasOpenCodeApiKey by viewModel.hasOpenCodeApiKey.collectAsState()
    val githubUsername by viewModel.githubUsername.collectAsState()
    val tokenError by viewModel.tokenError.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.refreshApiKeyStatus()
        viewModel.validateToken()
    }
    var showRecentIdeas by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("IdeaForge AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("One Idea. One Tap. One APK.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )

        if (!hasGitHubToken) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                onClick = onNavigateToSettings
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "GitHub Token Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Tap to set your GitHub Personal Access Token for cloud builds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else if (githubUsername != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Connected as: $githubUsername",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Ready to build",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (tokenError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                onClick = onNavigateToSettings
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "GitHub Token Invalid",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            tokenError!!.take(100),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        if (!hasOpenCodeApiKey) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                onClick = onNavigateToSettings
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "API Key Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Tap to add your Google Gemini API key for AI code generation (free)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Describe Your App Idea", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tell us what you want to build and Gemini AI will generate it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = ideaText,
                            onValueChange = { viewModel.updateIdeaText(it) },
                            modifier = Modifier.fillMaxWidth().height(200.dp).animateContentSize(),
                            placeholder = { Text("Describe your app idea in detail...\n\nFor example:\n\"A fitness tracker with workout plans, calorie counter, and progress photos\"", style = MaterialTheme.typography.bodyMedium) },
                            supportingText = { Text("${ideaText.length} / ${AppConstants.MAX_IDE_LENGTH} characters") },
                            maxLines = 10
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (text.isNotBlank()) viewModel.updateIdeaText(text) else Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Paste") }
                            FilledTonalButton(onClick = { Toast.makeText(context, "Voice input coming soon", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Mic, contentDescription = "Voice", modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Voice") }
                            FilledTonalButton(onClick = { viewModel.clearIdea() }) { Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Clear") }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ExtendedFloatingActionButton(
                            onClick = {
                                if (ideaText.length >= AppConstants.MIN_IDE_LENGTH) {
                                    viewModel.addRecentIdea(ideaText)
                                    onBuildApp(ideaText)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Build")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Build App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (recentIdeas.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent Ideas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { showRecentIdeas = !showRecentIdeas }) {
                            Icon(if (showRecentIdeas) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle")
                        }
                    }
                }
                if (showRecentIdeas) {
                    items(recentIdeas) { idea ->
                        Card(onClick = { viewModel.useRecentIdea(idea) }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(idea, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteRecentIdea(idea) }) { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }

            item { Text("Quick Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptQuickCard("Fitness App", "\uD83C\uDFCB", { onBuildApp("A comprehensive fitness tracker with workout logging, calorie counter, step tracking, water intake, progress photos, and exercise library with instructions") }, Modifier.weight(1f))
                    PromptQuickCard("Budget App", "\uD83D\uDCB0", { onBuildApp("A personal budget tracker with income and expense tracking, categories, monthly budgets, visual charts, savings goals, and financial dashboard") }, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptQuickCard("Quiz Game", "\uD83C\uDFAE", { onBuildApp("A trivia quiz game with multiple categories, difficulty levels, timer, scoring, high scores leaderboard, streak bonuses, and daily challenges") }, Modifier.weight(1f))
                    PromptQuickCard("Notes App", "\uD83D\uDCDD", { onBuildApp("A notes app with rich text editing, folders, tags, pin important notes, search, checklists, and export options") }, Modifier.weight(1f))
                }
            }

            if (recentProjects.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(8.dp)); Text("Recent Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(recentProjects.take(5)) { project -> ProjectCard(project = project, onClick = { }) }
            }

            if (recentBuilds.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(8.dp)); Text("Build History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(recentBuilds.take(5)) { build -> BuildHistoryCard(build = build, onClick = { }) }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
