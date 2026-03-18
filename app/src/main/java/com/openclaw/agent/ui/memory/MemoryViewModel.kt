package com.openclaw.agent.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.agent.core.memory.MemorySnippet
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<MemorySnippet>>(emptyList())
    val searchResults: StateFlow<List<MemorySnippet>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess

    fun loadFiles() {
        viewModelScope.launch {
            _files.value = memoryStore.list().sorted()
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            _selectedFile.value = path
            _fileContent.value = memoryStore.read(path) ?: "(empty)"
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
            _saveSuccess.value = "Saved $path"
        }
    }

    fun clearSaveMessage() {
        _saveSuccess.value = null
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            memoryStore.delete(path)
            _selectedFile.value = null
            _isEditing.value = false
            loadFiles()
        }
    }

    fun goBack() {
        _selectedFile.value = null
        _isEditing.value = false
        _isSearching.value = false
        _searchResults.value = emptyList()
        _searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val query = _searchQuery.value
        if (query.isBlank()) return
        _isSearching.value = true
        viewModelScope.launch {
            _searchResults.value = memoryStore.search(query, maxResults = 20)
            _isSearching.value = false
        }
    }

    fun createNewFile(path: String, content: String) {
        viewModelScope.launch {
            memoryStore.write(path, content)
            loadFiles()
            openFile(path)
        }
    }
}
