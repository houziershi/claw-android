package com.openclaw.agent.ui.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.agent.core.skill.Skill
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillsScreen(
    onBack: () -> Unit,
    viewModel: SkillsViewModel = hiltViewModel()
) {
    val skills by viewModel.skills.collectAsState()
    val selectedSkill by viewModel.selectedSkill.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSkills() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedSkill?.name ?: "Skills",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSkill != null) viewModel.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedSkill == null) {
                        IconButton(onClick = { viewModel.reloadSkills() }) {
                            Icon(Icons.Default.Refresh, "Reload")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedSkill != null) {
            // Skill detail view
            SkillDetailView(
                skill = selectedSkill!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            // Skill list
            if (skills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No skills loaded",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = ClawSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ClawSpacing.sm),
                    contentPadding = PaddingValues(vertical = ClawSpacing.sm)
                ) {
                    item {
                        Text(
                            "Skills are activated automatically when your message matches trigger keywords.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = ClawSpacing.xs)
                        )
                    }
                    items(skills) { skill ->
                        SkillListItem(
                            skill = skill,
                            onToggle = { viewModel.toggleSkill(skill.name, it) },
                            onClick = { viewModel.selectSkill(skill) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillListItem(
    skill: Skill,
    onToggle: (Boolean) -> Unit,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${skillIcon(skill.name)}  ${skill.name}",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(ClawSpacing.xs))
                Text(
                    skill.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = skill.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = ClawSpacing.sm)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillDetailView(
    skill: Skill,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(ClawSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ClawSpacing.lg)
    ) {
        // Header
        Text(
            "${skillIcon(skill.name)}  ${skill.name}",
            style = MaterialTheme.typography.headlineSmall
        )

        // Description
        Text(
            skill.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Status
        Card(
            shape = ClawShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = if (skill.enabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClawSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (skill.enabled) "✅ Enabled" else "⏸️ Disabled",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Triggers
        if (skill.triggers.isNotEmpty()) {
            Text("🎯 Triggers", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ClawSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ClawSpacing.xs)
            ) {
                skill.triggers.forEach { trigger ->
                    AssistChip(
                        onClick = {},
                        label = { Text(trigger) }
                    )
                }
            }
        }

        // Required Tools
        if (skill.requiredTools.isNotEmpty()) {
            Text("🔧 Required Tools", style = MaterialTheme.typography.titleSmall)
            skill.requiredTools.forEach { tool ->
                Text(
                    "• $tool",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = ClawSpacing.sm)
                )
            }
        }

        // System Prompt
        if (skill.systemPrompt.isNotBlank()) {
            Text("📝 System Prompt", style = MaterialTheme.typography.titleSmall)
            Card(
                shape = ClawShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    skill.systemPrompt,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(ClawSpacing.md)
                )
            }
        }
    }
}

private fun skillIcon(name: String): String = when (name.lowercase()) {
    "weather" -> "🌤️"
    "translator" -> "🌐"
    "web summary" -> "📰"
    "daily planner" -> "📅"
    "device control" -> "📱"
    else -> "⚡"
}
