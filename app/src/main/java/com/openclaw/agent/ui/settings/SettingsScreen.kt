package com.openclaw.agent.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedModel by viewModel.selectedModel.collectAsState(initial = "")
    val themeMode by viewModel.themeMode.collectAsState(initial = "system")
    val showToolCalls by viewModel.showToolCalls.collectAsState(initial = false)
    val apiKey by viewModel.apiKey.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()

    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var baseUrlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var themeDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── API Configuration ──────────────────────────────────────────
            Text("API Configuration", style = MaterialTheme.typography.titleMedium)

            // API Key
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility"
                        )
                    }
                }
            )

            // Base URL
            OutlinedTextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.anthropic.com/v1/messages") },
                singleLine = true
            )

            // Save button (show when either field changed)
            if (apiKeyInput != apiKey || baseUrlInput != baseUrl) {
                Button(
                    onClick = {
                        if (apiKeyInput != apiKey) viewModel.saveApiKey(apiKeyInput)
                        if (baseUrlInput != baseUrl) viewModel.saveBaseUrl(baseUrlInput)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }

            HorizontalDivider()

            // ── Model Selection ────────────────────────────────────────────
            Text("Model", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = viewModel.availableModels.find { it.first == selectedModel }?.second ?: selectedModel,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    label = { Text("Select Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    viewModel.availableModels.forEach { (modelId, displayName) ->
                        DropdownMenuItem(
                            text = { Text(displayName) },
                            onClick = {
                                viewModel.selectModel(modelId)
                                modelDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Theme ───────────────────────────────────────────────────────
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = themeDropdownExpanded,
                onExpandedChange = { themeDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = viewModel.themeModes.find { it.first == themeMode }?.second ?: "System Default",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    label = { Text("Appearance") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = themeDropdownExpanded,
                    onDismissRequest = { themeDropdownExpanded = false }
                ) {
                    viewModel.themeModes.forEach { (modeId, displayName) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (modeId) {
                                        "light" -> "☀️  $displayName"
                                        "dark" -> "🌙  $displayName"
                                        else -> "📱  $displayName"
                                    }
                                )
                            },
                            onClick = {
                                viewModel.selectTheme(modeId)
                                themeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Developer Options ──────────────────────────────────────────
            Text("Developer", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("显示工具调用", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "在聊天中显示 LLM 的工具调用链",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showToolCalls,
                    onCheckedChange = { viewModel.setShowToolCalls(it) }
                )
            }

            HorizontalDivider()

            // ── Connection Test ────────────────────────────────────────────
            Text("Connection Test", style = MaterialTheme.typography.titleMedium)
            Text(
                "Send a test message to verify API Key, Base URL, and model are working correctly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    viewModel.resetTestState()
                    viewModel.testConnection(selectedModel)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionTestState !is ConnectionTestState.Testing
            ) {
                when (connectionTestState) {
                    is ConnectionTestState.Testing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    }
                    else -> {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection")
                    }
                }
            }

            // Test result card
            when (val state = connectionTestState) {
                is ConnectionTestState.Success -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "✅ Connection Successful",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Reply: ${state.reply.take(200)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Tokens: ${state.inputTokens} in / ${state.outputTokens} out",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                is ConnectionTestState.Failure -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "❌ Connection Failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> { /* Idle or Testing — no card */ }
            }

            HorizontalDivider()

            // ── About ──────────────────────────────────────────────────────
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "Claw Android v0.5.0\nAn independent AI agent with memory, skills, voice I/O, and dark theme.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
