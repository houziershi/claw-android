package com.openclaw.agent.ui.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing
import com.openclaw.agent.ui.theme.Neutral800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    var editText by remember(fileContent) { mutableStateOf(fileContent) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadFiles() }

    // Show save snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(saveSuccess) {
        saveSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selectedFile != null -> selectedFile!!
                            showSearch -> "Search Memory"
                            else -> "Memory"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            isEditing -> viewModel.saveFile(editText)
                            selectedFile != null -> viewModel.goBack()
                            showSearch -> {
                                showSearch = false
                                viewModel.goBack()
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedFile != null) {
                        if (isEditing) {
                            IconButton(onClick = {
                                viewModel.saveFile(editText)
                            }) {
                                Icon(Icons.Default.Save, "Save")
                            }
                        } else {
                            IconButton(onClick = { viewModel.startEditing() }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                        }
                    } else if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            // ── Search mode ──
            showSearch -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(ClawSpacing.lg)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search keywords...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { viewModel.search() }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(ClawSpacing.md))

                    if (isSearching) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(ClawSpacing.xl))
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            "${searchResults.size} results found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(ClawSpacing.sm))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(ClawSpacing.sm)) {
                            items(searchResults) { snippet ->
                                SearchResultCard(
                                    snippet = snippet,
                                    onClick = {
                                        showSearch = false
                                        viewModel.openFile(snippet.path)
                                    }
                                )
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Text(
                            "No results found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── File editor ──
            selectedFile != null && isEditing -> {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(ClawSpacing.lg),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Neutral800,
                        focusedContainerColor = Neutral800
                    )
                )
            }

            // ── File viewer ──
            selectedFile != null -> {
                Text(
                    text = fileContent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(ClawSpacing.lg)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // ── File list ──
            else -> {
                val coreFiles = files.filter { !it.startsWith("daily/") }
                val dailyFiles = files.filter { it.startsWith("daily/") }.sortedDescending()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = ClawSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ClawSpacing.sm),
                    contentPadding = PaddingValues(vertical = ClawSpacing.sm)
                ) {
                    // Core files section
                    if (coreFiles.isNotEmpty()) {
                        item {
                            Text(
                                "📋 Core Files",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = ClawSpacing.xs),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(coreFiles) { file ->
                            MemoryFileItem(
                                file = file,
                                icon = when {
                                    file == "SOUL.md" -> "🧠"
                                    file == "USER.md" -> "👤"
                                    file == "MEMORY.md" -> "💾"
                                    else -> "📄"
                                },
                                onClick = { viewModel.openFile(file) }
                            )
                        }
                    }

                    // Daily notes section
                    if (dailyFiles.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(ClawSpacing.sm))
                            Text(
                                "📅 Daily Notes (${dailyFiles.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = ClawSpacing.xs),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(dailyFiles) { file ->
                            MemoryFileItem(
                                file = file.removePrefix("daily/"),
                                icon = "📝",
                                onClick = { viewModel.openFile(file) }
                            )
                        }
                    }

                    // Empty state
                    if (files.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ClawSpacing.xxl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No memory files yet.\nStart chatting and memories will be created automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryFileItem(
    file: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ClawShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClawSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClawSpacing.sm),
                modifier = Modifier.weight(1f)
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    file,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    snippet: com.openclaw.agent.core.memory.MemorySnippet,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ClawShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(ClawSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    snippet.path,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${"%.0f".format(snippet.relevance * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(ClawSpacing.xs))
            Text(
                snippet.content.take(200),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
