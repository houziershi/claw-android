package com.openclaw.agent.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.agent.core.memory.MemoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryStore: MemoryStore
) : ViewModel() {

    private val _files = MutableStateFlow<List<String>>(emptyList())
    val files: StateFlow<List<String>> = _files

    private val _selectedFile = MutableStateFlow<String?>(null)
    val selectedFile: StateFlow<String?> = _selectedFile

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    fun loadFiles() {
        viewModelScope.launch {
            _files.value = memoryStore.list()
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            _selectedFile.value = path
            _fileContent.value = memoryStore.read(path) ?: ""
            _isEditing.value = false
        }
    }

    fun startEditing() {
        _isEditing.value = true
    }

    fun saveFile(content: String) {
        val path = _selectedFile.value ?: return
        viewModelScope.launch {
            memoryStore.write(path, content)
            _fileContent.value = content
            _isEditing.value = false
        }
    }

    fun goBack() {
        _selectedFile.value = null
        _isEditing.value = false
    }
}
