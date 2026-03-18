package com.openclaw.agent.ui.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
    var editText by remember(fileContent) { mutableStateOf(fileContent) }

    LaunchedEffect(Unit) { viewModel.loadFiles() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedFile ?: "Memory") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFile != null) viewModel.goBack() else onBack()
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
                    }
                }
            )
        }
    ) { padding ->
        if (selectedFile == null) {
            // File list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(files) { file ->
                    ListItem(
                        headlineContent = { Text(file) },
                        modifier = Modifier.clickable { viewModel.openFile(file) }
                    )
                    HorizontalDivider()
                }
            }
        } else if (isEditing) {
            // Edit mode
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        } else {
            // View mode
            Text(
                text = fileContent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
