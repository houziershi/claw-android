package com.openclaw.agent.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onMijiaLogin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedModel by viewModel.selectedModel.collectAsState(initial = "")
    val themeMode by viewModel.themeMode.collectAsState(initial = "system")
    val showToolCalls by viewModel.showToolCalls.collectAsState(initial = false)
    val apiKey by viewModel.apiKey.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()
    val mijiaLoggedIn by viewModel.mijiaLoggedIn.collectAsState()
    val mijiaAuthChecking by viewModel.mijiaAuthChecking.collectAsState()

    // Refresh Mijia login state when screen resumes (e.g. after LoginActivity)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMijiaLoginState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                .padding(horizontal = ClawSpacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClawSpacing.lg)
        ) {
            Spacer(modifier = Modifier.height(ClawSpacing.xs))

            // ── API Configuration ──────────────────────────────────────────
            SettingsSection(title = "API 配置") {
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
                Spacer(modifier = Modifier.height(ClawSpacing.sm))
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.anthropic.com/v1/messages") },
                    singleLine = true
                )
                if (apiKeyInput != apiKey || baseUrlInput != baseUrl) {
                    Spacer(modifier = Modifier.height(ClawSpacing.sm))
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
            }

            // ── Model Selection ────────────────────────────────────────────
            SettingsSection(title = "模型") {
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
            }

            // ── Theme ───────────────────────────────────────────────────────
            SettingsSection(title = "外观") {
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
            }

            // ── Developer Options ──────────────────────────────────────────
            SettingsSection(title = "开发者") {
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
            }

            // ── Mijia Smart Home ───────────────────────────────────────────
            SettingsSection(title = "米家智能家居") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = if (mijiaLoggedIn) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ClawSpacing.xl)
                        )
                        Spacer(modifier = Modifier.width(ClawSpacing.md))
                        Column {
                            Text(
                                if (mijiaLoggedIn) "已登录" else "未登录",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (mijiaLoggedIn) "可通过 AI 助手控制智能家居设备"
                                else "登录后可控制米家设备",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (mijiaLoggedIn) {
                        Row {
                            if (mijiaAuthChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                TextButton(onClick = { viewModel.checkMijiaAuth() }) {
                                    Text("验证")
                                }
                            }
                            TextButton(
                                onClick = { viewModel.logoutMijia() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(ClawSpacing.xs))
                                Text("退出")
                            }
                        }
                    } else {
                        Button(onClick = onMijiaLogin) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(ClawSpacing.xs))
                            Text("登录")
                        }
                    }
                }
            }

            // ── Connection Test ────────────────────────────────────────────
            SettingsSection(title = "连接测试") {
                Text(
                    "Send a test message to verify API Key, Base URL, and model are working correctly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(ClawSpacing.sm))
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
                            Spacer(modifier = Modifier.width(ClawSpacing.sm))
                            Text("Testing...")
                        }
                        else -> {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(ClawSpacing.sm))
                            Text("Test Connection")
                        }
                    }
                }

                when (val state = connectionTestState) {
                    is ConnectionTestState.Success -> {
                        Spacer(modifier = Modifier.height(ClawSpacing.sm))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            shape = ClawShapes.cardSmall,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(ClawSpacing.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(ClawSpacing.sm))
                                    Text(
                                        "✅ Connection Successful",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(ClawSpacing.sm))
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
                        Spacer(modifier = Modifier.height(ClawSpacing.sm))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            shape = ClawShapes.cardSmall,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(ClawSpacing.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(ClawSpacing.sm))
                                    Text(
                                        "❌ Connection Failed",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(ClawSpacing.sm))
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
            }

            // ── About ──────────────────────────────────────────────────────
            SettingsSection(title = "关于") {
                Text(
                    "Claw Android v0.5.0\nAn independent AI agent with memory, skills, voice I/O, and dark theme.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(ClawSpacing.xxl))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = ClawSpacing.sm)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ClawShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClawSpacing.lg)
            ) {
                content()
            }
        }
    }
}
