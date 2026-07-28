package com.ideaforge.ai.ui.screens.apks

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ideaforge.ai.ui.components.ApkCard
import com.ideaforge.ai.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApksScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApksViewModel = hiltViewModel()
) {
    val builds by viewModel.builds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val totalStorageUsed by viewModel.totalStorageUsed.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val builtApks = builds.filter { it.apkPath != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Built APKs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (builtApks.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (builtApks.isNotEmpty()) {
                Text(
                    text = "Storage used: ${TimeUtils.formatFileSize(totalStorageUsed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search APKs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            if (builtApks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "\uD83D\uDCE6", style = MaterialTheme.typography.displayLarge)
                        Text("No APKs built yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Build your first app to see APKs here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val filteredApks = if (searchQuery.isBlank()) builtApks
                else builtApks.filter { it.projectName.contains(searchQuery, ignoreCase = true) }

                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredApks, key = { it.id }) { build ->
                        ApkCard(
                            build = build,
                            onInstall = {
                                build.apkPath?.let { url ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open download URL", Toast.LENGTH_SHORT).show()
                                    }
                                } ?: Toast.makeText(context, "No download URL available", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                build.apkPath?.let { url ->
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Download APK: $url")
                                            putExtra(Intent.EXTRA_SUBJECT, "Check out my app: ${build.projectName}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share APK"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot share", Toast.LENGTH_SHORT).show()
                                    }
                                } ?: Toast.makeText(context, "No download URL available", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { viewModel.deleteBuild(build.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All APKs") },
            text = { Text("Are you sure you want to delete all built APKs? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllBuilds(); showDeleteDialog = false }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
